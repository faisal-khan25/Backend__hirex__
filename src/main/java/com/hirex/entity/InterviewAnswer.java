package com.hirex.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "interview_answers",
        indexes = {
                @Index(name = "idx_answer_question", columnList = "question_id"),
                @Index(name = "idx_answer_session", columnList = "session_id")
        }
)
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ADDED: Foreign Keys
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private InterviewQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    // ADDED: Answer Content
    @Column(columnDefinition = "LONGTEXT")
    private String answerText;

    @Column(columnDefinition = "LONGTEXT")
    private String transcript;

    @Column(name = "recording_url")
    private String recordingUrl;

    // ADDED: Answer Metadata
    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "word_count")
    private Integer wordCount;

    // ADDED: Sentiment & Analysis
    @Column(name = "sentiment_score")
    private Double sentimentScore; // -1 to 1

    @Column(name = "confidence_score")
    private Double confidenceScore; // 0 to 1

    @Column(name = "clarity_score")
    private Double clarityScore; // 0 to 1

    // ADDED: Answer Evaluation
    @Column(name = "relevance_score")
    private Double relevanceScore; // 0 to 1

    @Column(name = "completeness_score")
    private Double completenessScore; // 0 to 1

    @Column(columnDefinition = "LONGTEXT")
    private String evaluationFeedback;

    @Column(columnDefinition = "LONGTEXT")
    private String improvementSuggestions;

    // ADDED: Answer Status
    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status")
    private AnswerStatus answerStatus;

    // ADDED: Timestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (answerStatus == null) {
            answerStatus = AnswerStatus.SUBMITTED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ADDED: Constructors
    public InterviewAnswer() {}

    public InterviewAnswer(InterviewQuestion question, InterviewSession session, String answerText) {
        this.question = question;
        this.session = session;
        this.answerText = answerText;
        this.answerStatus = AnswerStatus.SUBMITTED;
    }

    // ADDED: Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InterviewQuestion getQuestion() { return question; }
    public void setQuestion(InterviewQuestion question) { this.question = question; }

    public InterviewSession getSession() { return session; }
    public void setSession(InterviewSession session) { this.session = session; }

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

    public AnswerStatus getAnswerStatus() { return answerStatus; }
    public void setAnswerStatus(AnswerStatus answerStatus) { this.answerStatus = answerStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

