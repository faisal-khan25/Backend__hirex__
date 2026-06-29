package com.hirex.dto;

import java.time.LocalDateTime;
import java.util.List;

// ADDED: Interview Report DTO (for complete report delivery)
public class InterviewReportDto {
    private InterviewSessionDto session;
    private List<InterviewQuestionDto> questions;
    private List<InterviewAnswerDto> answers;
    private InterviewEvaluationDto evaluation;
    private String fullTranscript;
    private LocalDateTime generatedAt;

    public InterviewReportDto() {}

    // Getters and Setters
    public InterviewSessionDto getSession() { return session; }
    public void setSession(InterviewSessionDto session) { this.session = session; }

    public List<InterviewQuestionDto> getQuestions() { return questions; }
    public void setQuestions(List<InterviewQuestionDto> questions) { this.questions = questions; }

    public List<InterviewAnswerDto> getAnswers() { return answers; }
    public void setAnswers(List<InterviewAnswerDto> answers) { this.answers = answers; }

    public InterviewEvaluationDto getEvaluation() { return evaluation; }
    public void setEvaluation(InterviewEvaluationDto evaluation) { this.evaluation = evaluation; }

    public String getFullTranscript() { return fullTranscript; }
    public void setFullTranscript(String fullTranscript) { this.fullTranscript = fullTranscript; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
