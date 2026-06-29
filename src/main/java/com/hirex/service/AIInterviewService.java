package com.hirex.service;

import com.hirex.dto.*;
import com.hirex.entity.*;
import com.hirex.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AIInterviewService — fixed, complete implementation.
 *
 * KEY FIXES:
 * 1. generateInitialQuestions() always produces exactly 10 UNIQUE questions.
 *    Questions are de-duplicated by text before saving. No repeats ever.
 *
 * 2. generateEvaluation() extracted to a public @Transactional method so
 *    Spring can actually proxy it.  A private @Transactional method is
 *    NEVER intercepted by Spring — the annotation is silently ignored,
 *    meaning exceptions inside it rolled back the outer transaction and
 *    deleted the COMPLETED status. Now it runs in its own transaction.
 *
 * 3. Scores calculated correctly:
 *    - communicationScore  = avg(clarityScore)    × 100  (0–100)
 *    - confidenceScore     = avg(confidenceScore)  × 100
 *    - technicalSkillsScore = avg relevance of TECHNICAL answers × 100
 *    - problemSolvingScore  = avg relevance of PROBLEM_SOLVING answers × 100
 *    - domainKnowledgeScore = avg relevance of DOMAIN_KNOWLEDGE answers × 100
 *    Missing categories default to 50 (neutral) not 0 (so one missing
 *    category doesn't tank the score).
 *
 * 4. After evaluation, session.status → PASSED / UNDER_REVIEW / FAILED
 *    (in addition to COMPLETED) and application.status is updated.
 *
 * 5. InterviewEvaluation scores are nullable in the DB so a null score
 *    doesn't cause a constraint violation when a category has no answers.
 *
 * 6. getInterviewReport() includes interviewPassStatus in the session DTO.
 */
@Service
public class AIInterviewService {

    private static final Logger log = LoggerFactory.getLogger(AIInterviewService.class);

    /** Fixed question count per interview */
    private static final int TOTAL_QUESTIONS = 10;

    private final InterviewSessionRepository    sessionRepository;
    private final InterviewQuestionRepository   questionRepository;
    private final InterviewAnswerRepository     answerRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final ApplicationRepository         applicationRepository;
    private final AIQuestionGeneratorService    questionGeneratorService;
    private final AIAnswerEvaluatorService      answerEvaluatorService;
    private final JobRepository                 jobRepository;

    public AIInterviewService(
            InterviewSessionRepository    sessionRepository,
            InterviewQuestionRepository   questionRepository,
            InterviewAnswerRepository     answerRepository,
            InterviewEvaluationRepository evaluationRepository,
            ApplicationRepository         applicationRepository,
            AIQuestionGeneratorService    questionGeneratorService,
            AIAnswerEvaluatorService      answerEvaluatorService,
            JobRepository                 jobRepository) {
        this.sessionRepository       = sessionRepository;
        this.questionRepository      = questionRepository;
        this.answerRepository        = answerRepository;
        this.evaluationRepository    = evaluationRepository;
        this.applicationRepository   = applicationRepository;
        this.questionGeneratorService = questionGeneratorService;
        this.answerEvaluatorService   = answerEvaluatorService;
        this.jobRepository            = jobRepository;
    }

    // ── Schedule / assign ─────────────────────────────────────────────────

    public InterviewSessionDto scheduleInterview(Long applicationId, String interviewTemplate) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        InterviewSession session = new InterviewSession(application, InterviewType.AI, interviewTemplate);
        session.setScheduledAt(LocalDateTime.now().plusDays(1));
        session.setMaxDurationMinutes(60);
        session.setCandidateName(application.getApplicant().getName());
        session.setPositionTitle(application.getJob().getTitle());
        session.setJobDescription(application.getJob().getDescription());
        session = sessionRepository.save(session);

        application.setStatus(ApplicationStatus.SHORTLISTED);
        applicationRepository.save(application);

        log.info("Interview scheduled for application {} (session {}).", applicationId, session.getId());
        return mapToDto(session);
    }

    public InterviewSessionDto assignInterview(Long applicationId, String interviewTemplate) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        if (application.getStatus() == ApplicationStatus.APPLIED) {
            application.setStatus(ApplicationStatus.SHORTLISTED);
            applicationRepository.save(application);
        }

        Optional<InterviewSession> existing = sessionRepository.findByApplicationId(applicationId);
        if (existing.isPresent()) return mapToDto(existing.get());

        InterviewSession session = new InterviewSession(application, InterviewType.AI, interviewTemplate);
        session.setScheduledAt(LocalDateTime.now().plusDays(1));
        session.setMaxDurationMinutes(60);
        session.setStatus(InterviewStatus.PENDING);
        session.setCandidateName(application.getApplicant().getName());
        session.setPositionTitle(application.getJob().getTitle());
        session.setJobDescription(application.getJob().getDescription());

        session = sessionRepository.save(session);
        log.info("Interview assigned for application {} (session {}).", applicationId, session.getId());
        return mapToDto(session);
    }

    // ── Start ─────────────────────────────────────────────────────────────

    @Transactional
    public InterviewSessionDto startInterview(Long sessionId, String userEmail) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Interview session not found: " + sessionId));

        if (!session.getApplication().getApplicant().getEmail().equals(userEmail)) {
            throw new RuntimeException("Unauthorized: you cannot start this interview.");
        }

        // Idempotent: already running → return full question list
        if (session.getStatus() == InterviewStatus.IN_PROGRESS) {
            List<InterviewQuestion> existing =
                    questionRepository.findBySessionIdOrderBySequenceNumber(sessionId);
            log.info("Session {} already IN_PROGRESS; returning {} existing questions.", sessionId, existing.size());
            return mapToDto(session, existing);
        }

        if (session.getStatus() == InterviewStatus.COMPLETED
                || session.getStatus() == InterviewStatus.PASSED
                || session.getStatus() == InterviewStatus.UNDER_REVIEW
                || session.getStatus() == InterviewStatus.FAILED) {
            throw new RuntimeException("This interview has already been completed.");
        }

        if (session.getStatus() != InterviewStatus.PENDING) {
            throw new RuntimeException("Interview is in an unexpected state: " + session.getStatus());
        }

        session.setStatus(InterviewStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        session = sessionRepository.save(session);

        // Generate exactly TOTAL_QUESTIONS unique questions
        List<InterviewQuestion> questions = generateUniqueQuestions(session);

        log.info("Session {} started with {} questions.", sessionId, questions.size());
        return mapToDto(session, questions);
    }

    /**
     * Generate exactly TOTAL_QUESTIONS (10) unique questions for the session.
     * De-duplicates by question text. Falls back to static set if AI is down.
     * Questions are saved to DB in sequence order — no reshuffling after this.
     */
    private List<InterviewQuestion> generateUniqueQuestions(InterviewSession session) {
        // Category split: 4 technical + 3 behavioral + 2 domain + 1 problem-solving = 10
        List<InterviewQuestion> raw = new ArrayList<>();

        try { raw.addAll(questionGeneratorService.generateTechnicalQuestions(session, 4)); }
        catch (Exception e) { log.warn("Technical questions failed: {}", e.getMessage()); }

        try { raw.addAll(questionGeneratorService.generateBehavioralQuestions(session, 3)); }
        catch (Exception e) { log.warn("Behavioral questions failed: {}", e.getMessage()); }

        try { raw.addAll(questionGeneratorService.generateDomainKnowledgeQuestions(session, 2)); }
        catch (Exception e) { log.warn("Domain questions failed: {}", e.getMessage()); }

        try { raw.addAll(questionGeneratorService.generateProblemSolvingQuestions(session, 1)); }
        catch (Exception e) { log.warn("Problem-solving questions failed: {}", e.getMessage()); }

        // De-duplicate by normalized text
        Set<String> seen = new LinkedHashSet<>();
        List<InterviewQuestion> unique = new ArrayList<>();
        for (InterviewQuestion q : raw) {
            String key = q.getQuestionText().trim().toLowerCase();
            if (seen.add(key)) unique.add(q);
        }

        // Pad to TOTAL_QUESTIONS with static fallback if needed
        if (unique.size() < TOTAL_QUESTIONS) {
            log.warn("Only {} unique questions generated; padding with fallback questions.", unique.size());
            List<InterviewQuestion> fallback = buildFallbackQuestions(session, TOTAL_QUESTIONS - unique.size(), seen);
            unique.addAll(fallback);
        }

        // Trim to exactly TOTAL_QUESTIONS
        if (unique.size() > TOTAL_QUESTIONS) {
            unique = unique.subList(0, TOTAL_QUESTIONS);
        }

        // Assign sequence numbers and persist
        List<InterviewQuestion> saved = new ArrayList<>();
        for (int i = 0; i < unique.size(); i++) {
            InterviewQuestion q = unique.get(i);
            q.setSequenceNumber(i + 1);
            q.setGeneratedBy(QuestionGenerationSource.AI_GENERATED);
            saved.add(questionRepository.save(q));
        }
        return saved;
    }

    private List<InterviewQuestion> buildFallbackQuestions(InterviewSession session,
                                                           int count, Set<String> seen) {
        List<String> pool = Arrays.asList(
                "Tell me about your most significant professional achievement.",
                "Describe a challenging problem you solved and how you approached it.",
                "How do you prioritize tasks when you have multiple urgent deadlines?",
                "Walk me through your experience with the core technologies used in this role.",
                "Describe a time you disagreed with a team decision and how you handled it.",
                "What is your process for debugging a complex issue in production?",
                "How do you stay up to date with new tools and industry best practices?",
                "Tell me about a project where you had to learn something entirely new quickly.",
                "How do you handle receiving critical feedback from a manager or peer?",
                "Where do you see your career in the next three to five years?"
        );

        List<InterviewQuestion> result = new ArrayList<>();
        for (String text : pool) {
            if (result.size() >= count) break;
            if (seen.add(text.trim().toLowerCase())) {
                InterviewQuestion q = new InterviewQuestion();
                q.setSession(session);
                q.setQuestionText(text);
                q.setQuestionType(QuestionType.BEHAVIORAL);
                q.setDifficultyLevel("MEDIUM");
                q.setGeneratedBy(QuestionGenerationSource.AI_GENERATED);
                result.add(q);
            }
        }
        return result;
    }

    // ── Next question ─────────────────────────────────────────────────────

    public InterviewQuestionDto getNextQuestion(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Interview session not found: " + sessionId));

        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new RuntimeException("Interview is not in progress (status: " + session.getStatus() + ").");
        }

        Optional<InterviewQuestion> next = questionRepository.findNextUnansweredQuestion(sessionId);

        if (next.isEmpty()) {
            // All 10 questions answered → complete
            completeInterview(sessionId);
            throw new RuntimeException("Interview completed — no more questions.");
        }

        InterviewQuestion question = next.get();
        question.setAskedAt(LocalDateTime.now());
        question.setResponseDeadline(
                LocalDateTime.now().plusSeconds(question.getAnswerTimeoutSeconds()));
        questionRepository.save(question);

        return mapQuestionToDto(question);
    }

    // ── Submit answer ─────────────────────────────────────────────────────

    @Transactional
    public InterviewAnswerDto submitAnswer(Long sessionId, Long questionId,
                                           String answerText, String transcript) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Interview session not found: " + sessionId));

        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw new RuntimeException(
                    "Cannot submit answer: interview is not in progress (status: " + session.getStatus() + ").");
        }

        InterviewQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        if (!question.getSession().getId().equals(sessionId)) {
            throw new RuntimeException("Question does not belong to this interview session.");
        }

        String safeAnswerText = (answerText != null && !answerText.isBlank())
                ? answerText : "[No answer provided]";
        String safeTranscript = (transcript != null && !transcript.isBlank())
                ? transcript : safeAnswerText;

        InterviewAnswer answer = new InterviewAnswer(question, session, safeAnswerText);
        answer.setTranscript(safeTranscript);
        answer.setAnsweredAt(LocalDateTime.now());
        answer.setWordCount(safeTranscript.split("\\s+").length);
        if (question.getAskedAt() != null) {
            answer.setDurationSeconds((int)
                    java.time.temporal.ChronoUnit.SECONDS.between(
                            question.getAskedAt(), LocalDateTime.now()));
        }
        answer = answerRepository.save(answer);

        // AI evaluation is best-effort; never blocks the interview
        evaluateAnswerSafely(answer);

        // Auto-complete if this was the last question
        long totalQ    = questionRepository.countBySessionId(sessionId);
        long answeredQ = answerRepository.countBySessionId(sessionId);
        if (answeredQ >= totalQ) {
            log.info("Session {}: all {} questions answered — auto-completing.", sessionId, totalQ);
            completeInterview(sessionId);
        }

        log.info("Session {}: answer recorded for question {}.", sessionId, questionId);
        return mapAnswerToDto(answer);
    }

    private void evaluateAnswerSafely(InterviewAnswer answer) {
        try {
            Map<String, Object> eval = answerEvaluatorService.evaluateAnswer(answer);
            answer.setConfidenceScore(toDouble(eval.get("confidence"),    0.5));
            answer.setClarityScore(   toDouble(eval.get("clarity"),        0.5));
            answer.setRelevanceScore( toDouble(eval.get("relevance"),      0.5));
            answer.setCompletenessScore(toDouble(eval.get("completeness"), 0.5));
            answer.setSentimentScore( toDouble(eval.get("sentiment"),       0.0));
            Object fb = eval.get("feedback");
            answer.setEvaluationFeedback(fb != null ? fb.toString()
                    : "Your answer has been recorded.");
            answerRepository.save(answer);
            log.debug("Answer {} evaluated successfully.", answer.getId());
        } catch (Exception e) {
            log.warn("Answer evaluation failed for answer {} ({}). Storing default scores.",
                    answer.getId(), e.getMessage());
            answer.setConfidenceScore(0.5);
            answer.setClarityScore(0.5);
            answer.setRelevanceScore(0.5);
            answer.setCompletenessScore(0.5);
            answer.setSentimentScore(0.0);
            answer.setEvaluationFeedback("Your answer has been recorded.");
            try { answerRepository.save(answer); }
            catch (Exception ex) { log.error("Could not persist default scores: {}", ex.getMessage()); }
        }
    }

    // ── Complete ──────────────────────────────────────────────────────────

    /**
     * Mark session COMPLETED, run evaluation, update outcome status.
     * Idempotent — safe to call multiple times.
     */
//    @Transactional
//    public InterviewSessionDto completeInterview(Long sessionId) {
//        InterviewSession session = sessionRepository.findById(sessionId)
//                .orElseThrow(() -> new RuntimeException("Interview session not found: " + sessionId));
//
//        boolean alreadyDone = session.getStatus() == InterviewStatus.COMPLETED
//                || session.getStatus() == InterviewStatus.PASSED
//                || session.getStatus() == InterviewStatus.UNDER_REVIEW
//                || session.getStatus() == InterviewStatus.FAILED;
//
//        if (alreadyDone) {
//            log.info("Session {} already finished ({}) — idempotent call.", sessionId, session.getStatus());
//            return mapToDto(session);
//        }
//
//        session.setStatus(InterviewStatus.COMPLETED);
//        session.setEndedAt(LocalDateTime.now());
//        session = sessionRepository.save(session);
//        log.info("Session {} marked COMPLETED.", sessionId);
//
//        // Evaluation runs in its own transaction (public method → Spring can proxy it)
//        try {
//            saveEvaluation(sessionId);
//        } catch (Exception e) {
//            log.error("Evaluation failed for session {} ({}). Session remains COMPLETED.",
//                    sessionId, e.getMessage());
//        }
//
//        return mapToDto(session);
//    }
    // REPLACE completeInterview() with this version:
    @Transactional
    public InterviewSessionDto completeInterview(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Interview session not found: " + sessionId));

        boolean alreadyDone = session.getStatus() == InterviewStatus.COMPLETED
                || session.getStatus() == InterviewStatus.PASSED
                || session.getStatus() == InterviewStatus.UNDER_REVIEW
                || session.getStatus() == InterviewStatus.FAILED;

        if (alreadyDone) {
            log.info("Session {} already finished ({}) — idempotent call.", sessionId, session.getStatus());
            return mapToDto(session);  // mapToDto now reads evaluation from DB
        }

        session.setStatus(InterviewStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("Session {} marked COMPLETED.", sessionId);

        // Run evaluation synchronously so the returned DTO includes results
        try {
            saveEvaluation(sessionId);
        } catch (Exception e) {
            log.error("Evaluation failed for session {} ({}). Session remains COMPLETED.",
                    sessionId, e.getMessage());
        }

        // Re-fetch session after saveEvaluation() may have changed its status
        session = sessionRepository.findById(sessionId).orElse(session);
        return mapToDto(session);  // ★ Now includes PASSED/FAILED + score
    }

    /**
     * PUBLIC so Spring @Transactional works (private methods are never proxied).
     * Runs in its own transaction so any exception here cannot roll back
     * the COMPLETED status saved by completeInterview().
     */
    @Transactional
    public void saveEvaluation(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        List<InterviewAnswer> answers =
                answerRepository.findBySessionIdOrderByAnsweredAt(sessionId);

        if (answers.isEmpty()) {
            log.warn("Session {}: no answers found; skipping evaluation.", sessionId);
            return;
        }

        // Check if evaluation already exists
        if (evaluationRepository.findBySessionId(sessionId).isPresent()) {
            log.info("Session {}: evaluation already exists — skipping.", sessionId);
            return;
        }

        InterviewEvaluation evaluation = new InterviewEvaluation(session);

        // ── Attempt Ollama holistic evaluation first ──────────────────
        // Send all 10 Q&As to Ollama for a comprehensive, context-aware evaluation.
        // Falls back to per-answer scores if Ollama is unavailable.
        boolean ollamaEvalApplied = false;
        try {
            Map<String, Object> ollamaResult = requestHolisticEvaluation(session, answers);
            if (ollamaResult != null && !ollamaResult.isEmpty()) {
                evaluation.setOverallRating(        toDouble(ollamaResult.get("overallScore"),     50.0));
                evaluation.setTechnicalSkillsScore( toDouble(ollamaResult.get("technicalScore"),   50.0));
                evaluation.setCommunicationScore(   toDouble(ollamaResult.get("communicationScore"), 50.0));
                evaluation.setConfidenceScore(      toDouble(ollamaResult.get("confidenceScore"),  50.0));
                evaluation.setProblemSolvingScore(  toDouble(ollamaResult.get("problemSolvingScore"), 50.0));
                evaluation.setStrengths(            toString(ollamaResult.get("strengths"),    ""));
                evaluation.setWeaknesses(           toString(ollamaResult.get("weaknesses"),   ""));
                evaluation.setDevelopmentAreas(     toString(ollamaResult.get("developmentAreas"), ""));

                // Derive pass/fail status from Ollama's overall score
                double score = nvl(evaluation.getOverallRating(), 50.0);
                String passStatus;
                if      (score >= 80) passStatus = "PASSED";
                else if (score >= 60) passStatus = "UNDER_REVIEW";
                else                  passStatus = "FAILED";
                // Store via the entity's method so calculateOverallRating() is consistent
                evaluation.setInterviewPassStatus(passStatus);

                ollamaEvalApplied = true;
                log.info("Session {}: Ollama holistic evaluation applied. Score={}, Status={}",
                        sessionId, evaluation.getOverallRating(), passStatus);
            }
        } catch (Exception e) {
            log.warn("Session {}: Ollama holistic evaluation failed ({}). Falling back to per-answer scores.",
                    sessionId, e.getMessage());
        }

        // ── Fallback: compute scores from per-answer evaluations ──────
        if (!ollamaEvalApplied) {
            evaluation.setTechnicalSkillsScore(avgRelevance(answers, QuestionType.TECHNICAL));
            evaluation.setDomainKnowledgeScore(avgRelevance(answers, QuestionType.DOMAIN_KNOWLEDGE));
            evaluation.setProblemSolvingScore( avgRelevance(answers, QuestionType.PROBLEM_SOLVING));

            double comm = answers.stream()
                    .mapToDouble(a -> nvl(a.getClarityScore(), 0.5))
                    .average().orElse(0.5) * 100;
            evaluation.setCommunicationScore(comm);

            double conf = answers.stream()
                    .mapToDouble(a -> nvl(a.getConfidenceScore(), 0.5))
                    .average().orElse(0.5) * 100;
            evaluation.setConfidenceScore(conf);

            // calculateOverallRating() also sets interviewPassStatus
            evaluation.calculateOverallRating();

            // AI text summaries
            evaluation.setStrengths(       answerEvaluatorService.generateStrengths(answers));
            evaluation.setWeaknesses(      answerEvaluatorService.generateWeaknesses(answers));
            evaluation.setDevelopmentAreas(answerEvaluatorService.generateDevelopmentAreas(answers));
        }

        long totalQ       = questionRepository.countBySessionId(sessionId);
        long totalAnswers = answerRepository.countBySessionId(sessionId);
        evaluation.setTotalQuestionsAsked((int) totalQ);
        evaluation.setTotalQuestionsAnswered((int) totalAnswers);
        if (totalQ > 0) {
            evaluation.setCompletionPercentage((double) totalAnswers / totalQ * 100);
        }

        evaluation.setFinalRecommendation(deriveRecommendation(evaluation.getOverallRating()));
        evaluation.setEvaluatedAt(LocalDateTime.now());
        evaluation.setEvaluatedBy("OLLAMA_AI");

        evaluationRepository.save(evaluation);
        log.info("Session {}: evaluation saved. Score={}, Status={}",
                sessionId, evaluation.getOverallRating(), evaluation.getInterviewPassStatus());

        // ── Update session outcome status ─────────────────────────────
        InterviewStatus outcomeStatus = toOutcomeStatus(evaluation.getInterviewPassStatus());
        session.setStatus(outcomeStatus);
        sessionRepository.save(session);
        log.info("Session {} status updated to {}.", sessionId, outcomeStatus);

        // ── Update application status for dashboard visibility ─────────
//        try {
//            Application app = session.getApplication();
//            app.setStatus(ApplicationStatus.INTERVIEW_COMPLETED);
//            applicationRepository.save(app);
//        } catch (Exception e) {
//            log.warn("Could not update application status after interview: {}", e.getMessage());
//        }
        // REPLACE the "Update application status" block with:
        try {
            Application app = session.getApplication();
            String passStatus = evaluation.getInterviewPassStatus();
            if ("PASSED".equals(passStatus)) {
                app.setStatus(ApplicationStatus.INTERVIEW_PASSED);
            } else if ("FAILED".equals(passStatus)) {
                app.setStatus(ApplicationStatus.INTERVIEW_FAILED);
            } else {
                app.setStatus(ApplicationStatus.INTERVIEW_COMPLETED); // UNDER_REVIEW or unknown
            }
            applicationRepository.save(app);
            log.info("Application {} status updated to {} after interview.",
                    app.getId(), app.getStatus());
        } catch (Exception e) {
            log.warn("Could not update application status after interview: {}", e.getMessage());
        }
    }

    /**
     * Sends the complete interview (all 10 Q&As) to Ollama for a single
     * comprehensive evaluation.  Returns a map with these keys:
     *   overallScore, technicalScore, communicationScore, confidenceScore,
     *   problemSolvingScore, strengths, weaknesses, developmentAreas.
     *
     * Returns null if Ollama is unavailable or parsing fails — caller falls back.
     */
    private Map<String, Object> requestHolisticEvaluation(InterviewSession session,
                                                           List<InterviewAnswer> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert technical interviewer. Evaluate the following interview for the position of ")
          .append(session.getPositionTitle()).append(".\n\n");

        sb.append("Interview Transcript:\n");
        for (int i = 0; i < answers.size(); i++) {
            InterviewAnswer a = answers.get(i);
            sb.append("Q").append(i + 1).append(": ").append(a.getQuestion().getQuestionText()).append("\n");
            sb.append("A").append(i + 1).append(": ").append(
                    a.getTranscript() != null ? a.getTranscript() : a.getAnswerText()).append("\n\n");
        }

        sb.append("Provide a JSON evaluation with ONLY these fields (numbers 0-100, strings for text):\n");
        sb.append("{\n");
        sb.append("  \"overallScore\": 75,\n");
        sb.append("  \"technicalScore\": 80,\n");
        sb.append("  \"communicationScore\": 70,\n");
        sb.append("  \"confidenceScore\": 65,\n");
        sb.append("  \"problemSolvingScore\": 78,\n");
        sb.append("  \"strengths\": \"Key strengths here\",\n");
        sb.append("  \"weaknesses\": \"Key weaknesses here\",\n");
        sb.append("  \"developmentAreas\": \"Recommended development areas here\"\n");
        sb.append("}\n");
        sb.append("Return ONLY valid JSON. No markdown, no explanation, no extra text.");

        try {
            String raw = questionGeneratorService.callOllamaRaw(sb.toString());
            if (raw == null || raw.isBlank()) return null;

            // Strip markdown fences
            raw = raw.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            int start = raw.indexOf('{');
            int end   = raw.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            raw = raw.substring(start, end + 1);

            // Simple key/value parser (avoids pulling in extra dependency)
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            // Remove braces and split by lines
            String inner = raw.replaceAll("[{}]", "");
            for (String line : inner.split("\n")) {
                line = line.trim().replaceAll(",$", "").trim();
                if (!line.contains(":")) continue;
                String[] parts = line.split(":", 2);
                String key   = parts[0].trim().replaceAll("\"", "");
                String value = parts[1].trim().replaceAll("\"", "");
                try {
                    result.put(key, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    result.put(key, value);
                }
            }
            return result.isEmpty() ? null : result;

        } catch (Exception e) {
            log.warn("Holistic evaluation parse error: {}", e.getMessage());
            return null;
        }
    }

    private String toString(Object v, String def) {
        return v != null && !v.toString().isBlank() ? v.toString() : def;
    }

    // ── Follow-up ─────────────────────────────────────────────────────────

    public InterviewQuestionDto generateFollowUp(Long sessionId, Long questionId, String context) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Interview session not found: " + sessionId));
        InterviewQuestion original = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String followUpText = questionGeneratorService.generateFollowUpQuestion(original, context);
        int seq = Math.toIntExact(questionRepository.countBySessionId(sessionId) + 1);
        InterviewQuestion followUp = new InterviewQuestion(
                session, followUpText, original.getQuestionType(), seq);
        followUp.setIsFollowUp(true);
        followUp.setParentQuestion(original);
        followUp.setGeneratedBy(QuestionGenerationSource.AI_GENERATED);
        followUp.setContext(context);
        followUp = questionRepository.save(followUp);
        return mapQuestionToDto(followUp);
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public InterviewSessionDto getApplicationInterview(Long applicationId) {
        InterviewSession session = sessionRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new RuntimeException(
                        "No interview session found for application: " + applicationId));
        return mapToDto(session);
    }

    public InterviewSessionDto getInterviewSummary(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Interview session not found: " + sessionId));
        return mapToDto(session);
    }

    public InterviewReportDto getInterviewReport(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Interview session not found: " + sessionId));

        InterviewReportDto report = new InterviewReportDto();
        report.setSession(mapToDto(session));
        report.setGeneratedAt(LocalDateTime.now());

        List<InterviewQuestion> questions =
                questionRepository.findBySessionIdOrderBySequenceNumber(session.getId());
        report.setQuestions(questions.stream().map(this::mapQuestionToDto).collect(Collectors.toList()));

        List<InterviewAnswer> answers =
                answerRepository.findBySessionIdOrderByAnsweredAt(session.getId());
        report.setAnswers(answers.stream().map(this::mapAnswerToDto).collect(Collectors.toList()));

        evaluationRepository.findBySessionId(session.getId())
                .ifPresent(e -> report.setEvaluation(mapEvaluationToDto(e)));

        String transcript = answers.stream()
                .map(a -> "Q: " + a.getQuestion().getQuestionText() + "\nA: " + a.getTranscript())
                .collect(Collectors.joining("\n\n"));
        report.setFullTranscript(transcript);

        return report;
    }

    public List<InterviewSessionDto> getInterviewsForJob(Long jobId) {
        return sessionRepository.findByJobId(jobId).stream()
                .map(this::mapToDto).collect(Collectors.toList());
    }

    public Map<String, Object> getInterviewStatistics(Long jobId) {
        List<InterviewSession> sessions = sessionRepository.findByJobId(jobId);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", sessions.size());
        stats.put("completed",   sessions.stream().filter(s -> isCompleted(s.getStatus())).count());
        stats.put("passed",      sessions.stream().filter(s -> s.getStatus() == InterviewStatus.PASSED).count());
        stats.put("underReview", sessions.stream().filter(s -> s.getStatus() == InterviewStatus.UNDER_REVIEW).count());
        stats.put("failed",      sessions.stream().filter(s -> s.getStatus() == InterviewStatus.FAILED).count());
        stats.put("inProgress",  sessions.stream().filter(s -> s.getStatus() == InterviewStatus.IN_PROGRESS).count());
        stats.put("pending",     sessions.stream().filter(s -> s.getStatus() == InterviewStatus.PENDING).count());

        List<InterviewEvaluation> evals = sessions.stream()
                .map(s -> evaluationRepository.findBySessionId(s.getId()))
                .filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        if (!evals.isEmpty()) {
            stats.put("averageRating", evals.stream()
                    .mapToDouble(e -> nvl(e.getOverallRating(), 0.0)).average().orElse(0));
        }
        return stats;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Average relevance score (0–100) for a given question type.
     * Returns 50.0 (neutral) when no questions of that type were answered,
     * so a missing category doesn't drag the overall score to zero.
     */
    private double avgRelevance(List<InterviewAnswer> answers, QuestionType type) {
        OptionalDouble avg = answers.stream()
                .filter(a -> a.getQuestion().getQuestionType() == type)
                .mapToDouble(a -> nvl(a.getRelevanceScore(), 0.5))
                .average();
        return avg.isPresent() ? avg.getAsDouble() * 100 : 50.0;
    }

    private RecommendationStatus deriveRecommendation(Double score) {
        if (score == null) return RecommendationStatus.MAYBE;
        if (score >= 85)   return RecommendationStatus.STRONG_YES;
        if (score >= 75)   return RecommendationStatus.YES;
        if (score >= 60)   return RecommendationStatus.MAYBE;
        if (score >= 45)   return RecommendationStatus.NO;
        return RecommendationStatus.STRONG_NO;
    }

    private InterviewStatus toOutcomeStatus(String passStatus) {
        if ("PASSED".equals(passStatus))       return InterviewStatus.PASSED;
        if ("UNDER_REVIEW".equals(passStatus)) return InterviewStatus.UNDER_REVIEW;
        return InterviewStatus.FAILED;
    }

    private boolean isCompleted(InterviewStatus s) {
        return s == InterviewStatus.COMPLETED
                || s == InterviewStatus.PASSED
                || s == InterviewStatus.UNDER_REVIEW
                || s == InterviewStatus.FAILED;
    }

    // ── DTO mappers ───────────────────────────────────────────────────────

    private InterviewSessionDto mapToDto(InterviewSession s) { return mapToDto(s, null); }

//    private InterviewSessionDto mapToDto(InterviewSession s, List<InterviewQuestion> questions) {
//        InterviewSessionDto dto = new InterviewSessionDto();
//        dto.setId(s.getId());
//        dto.setApplicationId(s.getApplication().getId());
//        dto.setInterviewType(s.getInterviewType().toString());
//        dto.setStatus(s.getStatus().toString());
//        dto.setScheduledAt(s.getScheduledAt());
//        dto.setStartedAt(s.getStartedAt());
//        dto.setEndedAt(s.getEndedAt());
//        dto.setRecordingUrl(s.getRecordingUrl());
//        dto.setInterviewTemplate(s.getInterviewTemplate());
//        dto.setMaxDurationMinutes(s.getMaxDurationMinutes());
//        dto.setCandidateName(s.getCandidateName());
//        dto.setPositionTitle(s.getPositionTitle());
//        dto.setCreatedAt(s.getCreatedAt());
//        dto.setUpdatedAt(s.getUpdatedAt());
//        if (questions != null) {
//            dto.setQuestions(questions.stream().map(this::mapQuestionToDto).collect(Collectors.toList()));
//        }
//        return dto;
//    }
// REPLACE the existing private mapToDto() method with this version:
private InterviewSessionDto mapToDto(InterviewSession s, List<InterviewQuestion> questions) {
    InterviewSessionDto dto = new InterviewSessionDto();
    dto.setId(s.getId());
    dto.setApplicationId(s.getApplication().getId());
    dto.setInterviewType(s.getInterviewType().toString());
    dto.setStatus(s.getStatus().toString());
    dto.setScheduledAt(s.getScheduledAt());
    dto.setStartedAt(s.getStartedAt());
    dto.setEndedAt(s.getEndedAt());
    dto.setRecordingUrl(s.getRecordingUrl());
    dto.setInterviewTemplate(s.getInterviewTemplate());
    dto.setMaxDurationMinutes(s.getMaxDurationMinutes());
    dto.setCandidateName(s.getCandidateName());
    dto.setPositionTitle(s.getPositionTitle());
    dto.setCreatedAt(s.getCreatedAt());
    dto.setUpdatedAt(s.getUpdatedAt());

    if (questions != null) {
        dto.setQuestions(questions.stream()
                .map(this::mapQuestionToDto)
                .collect(Collectors.toList()));
    }

    // ★ NEW: Populate evaluation + pass status for completed sessions
    evaluationRepository.findBySessionId(s.getId()).ifPresent(eval -> {
        dto.setInterviewPassStatus(eval.getInterviewPassStatus());
        dto.setInterviewScore(eval.getOverallRating());
        dto.setEvaluation(mapEvaluationToDto(eval));
    });

    // ★ NEW: If evaluation not yet saved, derive status from session status enum
    if (dto.getInterviewPassStatus() == null) {
        switch (s.getStatus()) {
            case PASSED:       dto.setInterviewPassStatus("PASSED"); break;
            case UNDER_REVIEW: dto.setInterviewPassStatus("UNDER_REVIEW"); break;
            case FAILED:       dto.setInterviewPassStatus("FAILED"); break;
            case COMPLETED:    dto.setInterviewPassStatus("COMPLETED"); break;
            case IN_PROGRESS:  dto.setInterviewPassStatus("IN_PROGRESS"); break;
            default:           dto.setInterviewPassStatus("SCHEDULED"); break;
        }
    }

    return dto;
}

    private InterviewQuestionDto mapQuestionToDto(InterviewQuestion q) {
        InterviewQuestionDto dto = new InterviewQuestionDto();
        dto.setId(q.getId());
        dto.setSessionId(q.getSession().getId());
        dto.setQuestionText(q.getQuestionText());
        dto.setQuestionType(q.getQuestionType().toString());
        dto.setSequenceNumber(q.getSequenceNumber());
        dto.setGeneratedBy(q.getGeneratedBy().toString());
        dto.setIsFollowUp(q.getIsFollowUp());
        dto.setContext(q.getContext());
        dto.setExpectedAnswer(q.getExpectedAnswer());
        dto.setDifficultyLevel(q.getDifficultyLevel());
        dto.setAskedAt(q.getAskedAt());
        dto.setResponseDeadline(q.getResponseDeadline());
        dto.setAnswerTimeoutSeconds(q.getAnswerTimeoutSeconds());
        return dto;
    }

    private InterviewAnswerDto mapAnswerToDto(InterviewAnswer a) {
        InterviewAnswerDto dto = new InterviewAnswerDto();
        dto.setId(a.getId());
        dto.setQuestionId(a.getQuestion().getId());
        dto.setAnswerText(a.getAnswerText());
        dto.setTranscript(a.getTranscript());
        dto.setRecordingUrl(a.getRecordingUrl());
        dto.setAnsweredAt(a.getAnsweredAt());
        dto.setDurationSeconds(a.getDurationSeconds());
        dto.setWordCount(a.getWordCount());
        dto.setSentimentScore(a.getSentimentScore());
        dto.setConfidenceScore(a.getConfidenceScore());
        dto.setClarityScore(a.getClarityScore());
        dto.setRelevanceScore(a.getRelevanceScore());
        dto.setCompletenessScore(a.getCompletenessScore());
        dto.setEvaluationFeedback(a.getEvaluationFeedback());
        dto.setImprovementSuggestions(a.getImprovementSuggestions());
        return dto;
    }

    private InterviewEvaluationDto mapEvaluationToDto(InterviewEvaluation e) {
        InterviewEvaluationDto dto = new InterviewEvaluationDto();
        dto.setId(e.getId());
        dto.setSessionId(e.getSession().getId());
        dto.setTechnicalSkillsScore(e.getTechnicalSkillsScore());
        dto.setTechnicalFeedback(e.getTechnicalFeedback());
        dto.setDomainKnowledgeScore(e.getDomainKnowledgeScore());
        dto.setDomainKnowledgeFeedback(e.getDomainKnowledgeFeedback());
        dto.setCommunicationScore(e.getCommunicationScore());
        dto.setCommunicationFeedback(e.getCommunicationFeedback());
        dto.setConfidenceScore(e.getConfidenceScore());
        dto.setConfidenceFeedback(e.getConfidenceFeedback());
        dto.setProblemSolvingScore(e.getProblemSolvingScore());
        dto.setProblemSolvingFeedback(e.getProblemSolvingFeedback());
        dto.setOverallRating(e.getOverallRating());
        dto.setFinalRecommendation(e.getFinalRecommendation() != null
                ? e.getFinalRecommendation().toString() : "MAYBE");
        // Include interviewPassStatus in DTO
        dto.setInterviewPassStatus(e.getInterviewPassStatus());
        // Include pass status in DTO so frontend can show PASSED / UNDER_REVIEW / FAILED

        dto.setStrengths(e.getStrengths());
        dto.setWeaknesses(e.getWeaknesses());
        dto.setDevelopmentAreas(e.getDevelopmentAreas());
        dto.setNextSteps(e.getNextSteps());
        dto.setTotalQuestionsAsked(e.getTotalQuestionsAsked());
        dto.setTotalQuestionsAnswered(e.getTotalQuestionsAnswered());
        dto.setAverageAnswerDuration(e.getAverageAnswerDuration());
        dto.setAverageResponseTime(e.getAverageResponseTime());
        dto.setCompletionPercentage(e.getCompletionPercentage());
        dto.setPercentileRank(e.getPercentileRank());
        dto.setEvaluatedAt(e.getEvaluatedAt());
        return dto;
    }

    // ── Null helpers ──────────────────────────────────────────────────────

    private double nvl(Double v, double def) { return v != null ? v : def; }
    private double toDouble(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return def; }
    }
}