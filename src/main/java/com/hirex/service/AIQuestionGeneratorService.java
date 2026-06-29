package com.hirex.service;

import com.hirex.entity.*;
import com.hirex.repository.InterviewQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * AIQuestionGeneratorService — generates interview questions via OpenAI.
 *
 * KEY FIX: generateInitialQuestions() MUST produce questions even when
 * the AI is down (quota/rate-limit/network).  OpenAIService.generateQuestions()
 * already falls back to static questions on any error, so this layer
 * just needs to avoid empty lists reaching the session.
 */
@Service
public class AIQuestionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGeneratorService.class);

    private final InterviewQuestionRepository questionRepository;
    private final OllamaService ollamaService;

    @Value("${ai.interview.num-initial-questions:5}")
    private int numInitialQuestions;

    @Value("${ai.interview.questions-per-category:2}")
    private int questionsPerCategory;

    public AIQuestionGeneratorService(InterviewQuestionRepository questionRepository,
                                      OllamaService ollamaService) {
        this.questionRepository = questionRepository;
        this.ollamaService = ollamaService;
    }

    /**
     * Generate the initial question set for a session.
     *
     * FIX: if all three category generators return empty (e.g. the AI is
     * completely down and even the fallback list is somehow empty), we add
     * a guaranteed minimum set of static questions so the session never
     * starts with zero questions.
     */
    public List<InterviewQuestion> generateInitialQuestions(InterviewSession session) {
        List<InterviewQuestion> questions = new ArrayList<>();

        try {
            questions.addAll(generateTechnicalQuestions(session, questionsPerCategory));
        } catch (Exception e) {
            log.warn("Technical question generation failed: {}", e.getMessage());
        }
        try {
            questions.addAll(generateBehavioralQuestions(session, questionsPerCategory));
        } catch (Exception e) {
            log.warn("Behavioral question generation failed: {}", e.getMessage());
        }
        try {
            questions.addAll(generateDomainKnowledgeQuestions(session, Math.max(1, questionsPerCategory - 1)));
        } catch (Exception e) {
            log.warn("Domain knowledge question generation failed: {}", e.getMessage());
        }

        // Guarantee at least 3 questions even if all generators failed
        if (questions.size() < 3) {
            log.warn("Question generation produced only {} question(s); adding emergency fallback set.",
                    questions.size());
            questions.addAll(buildEmergencyFallbackQuestions(session, 5 - questions.size()));
        }

        Collections.shuffle(questions);

        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setSequenceNumber(i + 1);
            questions.get(i).setGeneratedBy(QuestionGenerationSource.AI_GENERATED);
            questionRepository.save(questions.get(i));
        }

        log.info("Session {}: generated {} questions.", session.getId(), questions.size());
        return questions;
    }

    // ── Category generators ───────────────────────────────────────────────

    public List<InterviewQuestion> generateTechnicalQuestions(InterviewSession session, int count) {
        String prompt = String.format(
                "Generate %d technical interview questions for a %s position.\n" +
                        "Job description: %s\n\n" +
                        "Questions should assess practical knowledge and problem-solving skills.\n" +
                        "Format: Return only the questions, one per line.",
                count, session.getPositionTitle(), session.getJobDescription()
        );
        return buildQuestions(session, ollamaService.generateQuestions(prompt, count), QuestionType.TECHNICAL);
    }

    public List<InterviewQuestion> generateBehavioralQuestions(InterviewSession session, int count) {
        String prompt = String.format(
                "Generate %d behavioral interview questions for a %s role.\n" +
                        "Focus on: communication, teamwork, leadership, conflict resolution, adaptability.\n" +
                        "Use the STAR method. Format: Return only the questions, one per line.",
                count, session.getPositionTitle()
        );
        return buildQuestions(session, ollamaService.generateQuestions(prompt, count), QuestionType.BEHAVIORAL);
    }

    public List<InterviewQuestion> generateDomainKnowledgeQuestions(InterviewSession session, int count) {
        String prompt = String.format(
                "Generate %d domain-specific knowledge questions based on:\n%s\n\n" +
                        "Test industry knowledge, best practices, and relevant frameworks.\n" +
                        "Format: Return only the questions, one per line.",
                count, session.getJobDescription()
        );
        return buildQuestions(session, ollamaService.generateQuestions(prompt, count), QuestionType.DOMAIN_KNOWLEDGE);
    }

    /** Convert raw text questions to InterviewQuestion entities. */
    private List<InterviewQuestion> buildQuestions(InterviewSession session,
                                                   List<String> texts,
                                                   QuestionType type) {
        List<InterviewQuestion> list = new ArrayList<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            InterviewQuestion q = new InterviewQuestion();
            q.setSession(session);
            q.setQuestionText(text.trim());
            q.setQuestionType(type);
            q.setDifficultyLevel("MEDIUM");
            q.setGeneratedBy(QuestionGenerationSource.AI_GENERATED);
            list.add(q);
        }
        return list;
    }

    /**
     * Last-resort static questions when AI is completely unavailable.
     * These are generic but professional enough to conduct a real interview.
     */
    private List<InterviewQuestion> buildEmergencyFallbackQuestions(InterviewSession session, int count) {
        List<String> fallback = Arrays.asList(
                "Tell me about your most impactful professional accomplishment.",
                "Describe a challenging situation and how you resolved it.",
                "What motivates you to perform your best work?",
                "How do you approach collaborating with a team on a complex project?",
                "What are the key skills you bring to this role?"
        );
        List<String> chosen = fallback.subList(0, Math.min(count, fallback.size()));
        List<InterviewQuestion> list = new ArrayList<>();
        for (String text : chosen) {
            InterviewQuestion q = new InterviewQuestion();
            q.setSession(session);
            q.setQuestionText(text);
            q.setQuestionType(QuestionType.BEHAVIORAL);
            q.setDifficultyLevel("MEDIUM");
            q.setGeneratedBy(QuestionGenerationSource.AI_GENERATED);
            list.add(q);
        }
        return list;
    }

    // ── Follow-up generation ──────────────────────────────────────────────

    /**
     * Generate a follow-up question based on the candidate's previous answer.
     * Falls back to a generic probing question if AI is unavailable.
     */
    public String generateFollowUpQuestion(InterviewQuestion originalQuestion, String candidateAnswer) {
        String prompt = String.format(
                "Original question: %s\n\n" +
                        "Candidate's answer: %s\n\n" +
                        "Generate one follow-up question to dig deeper. " +
                        "Reference the candidate's answer. Return only the question.",
                originalQuestion.getQuestionText(), candidateAnswer
        );
        try {
            String result = ollamaService.generateText(prompt);
            if (result == null || result.isBlank()) {
                return "Could you elaborate further on that, with a specific example?";
            }
            return result;
        } catch (Exception e) {
            log.warn("Follow-up question generation failed ({}). Using fallback.", e.getMessage());
            return "Could you elaborate further on that, with a specific example?";
        }
    }

    // ── Remaining helpers (unchanged) ─────────────────────────────────────

    public List<InterviewQuestion> generateProblemSolvingQuestions(InterviewSession session, int count) {
        String prompt = String.format(
                "Generate %d real-world problem-solving scenarios for a role involving: %s\n\n" +
                        "Format: Return only the questions, one per line.", count, session.getJobDescription());
        return buildQuestions(session, ollamaService.generateQuestions(prompt, count), QuestionType.PROBLEM_SOLVING);
    }

    public List<InterviewQuestion> generateSituationalQuestions(InterviewSession session, int count) {
        String prompt = String.format(
                "Generate %d situational questions for a %s position.\n" +
                        "Format: Return only the questions, one per line.", count, session.getPositionTitle());
        return buildQuestions(session, ollamaService.generateQuestions(prompt, count), QuestionType.SITUATIONAL);
    }


    /**
     * Bridge: sends a raw prompt directly to Ollama.
     * Used by AIInterviewService for holistic end-of-interview evaluation.
     */
    public String callOllamaRaw(String prompt) {
        return ollamaService.generateText(prompt);
    }

    public boolean validateQuestionQuality(InterviewQuestion question) {
        String text = question.getQuestionText();
        return text != null
                && text.length() >= 10
                && text.length() <= 1000
                && !text.contains("{{")
                && !text.contains("PLACEHOLDER");
    }

    public List<String> getRecommendedQuestions(String jobTitle, String department) {
        Map<String, List<String>> templates = new HashMap<>();
        templates.put("SOFTWARE_ENGINEER", Arrays.asList(
                "Describe a complex technical problem you solved recently.",
                "How do you approach debugging a production issue?",
                "How do you stay updated with new technologies?"));
        templates.put("PRODUCT_MANAGER", Arrays.asList(
                "Tell us about a product you helped launch.",
                "How do you prioritize features under pressure?",
                "How do you gather and incorporate user feedback?"));
        templates.put("SALES", Arrays.asList(
                "Describe your largest deal and how you closed it.",
                "How do you build long-term client relationships?",
                "How do you manage your pipeline and stay organized?"));
        return templates.getOrDefault(jobTitle.toUpperCase().replace(" ", "_"), new ArrayList<>());
    }
}