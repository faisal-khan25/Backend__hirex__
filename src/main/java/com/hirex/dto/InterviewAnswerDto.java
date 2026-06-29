package com.hirex.dto;

import java.time.LocalDateTime;

// ADDED: Interview Answer DTO
public class InterviewAnswerDto {
    private Long id;
    private Long questionId;
    private String answerText;
    private String transcript;
    private String recordingUrl;
    private LocalDateTime answeredAt;
    private Integer durationSeconds;
    private Integer wordCount;
    private Double sentimentScore;
    private Double confidenceScore;
    private Double clarityScore;
    private Double relevanceScore;
    private Double completenessScore;
    private String evaluationFeedback;
    private String improvementSuggestions;

    public InterviewAnswerDto() {}

    public InterviewAnswerDto(Long id, String answerText, String transcript) {
        this.id = id;
        this.answerText = answerText;
        this.transcript = transcript;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }

    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }

    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(LocalDateTime answeredAt) { this.answeredAt = answeredAt; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public Double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(Double sentimentScore) { this.sentimentScore = sentimentScore; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public Double getClarityScore() { return clarityScore; }
    public void setClarityScore(Double clarityScore) { this.clarityScore = clarityScore; }

    public Double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }

    public Double getCompletenessScore() { return completenessScore; }
    public void setCompletenessScore(Double completenessScore) { this.completenessScore = completenessScore; }

    public String getEvaluationFeedback() { return evaluationFeedback; }
    public void setEvaluationFeedback(String evaluationFeedback) { this.evaluationFeedback = evaluationFeedback; }

    public String getImprovementSuggestions() { return improvementSuggestions; }
    public void setImprovementSuggestions(String improvementSuggestions) { this.improvementSuggestions = improvementSuggestions; }
}
