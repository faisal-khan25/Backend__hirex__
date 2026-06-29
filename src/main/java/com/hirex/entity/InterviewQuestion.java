package com.hirex.entity;



import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "interview_questions",
        indexes = {
                @Index(name = "idx_question_session", columnList = "session_id"),
                @Index(name = "idx_question_sequence", columnList = "session_id, sequence_number")
        }
)
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ADDED: Foreign Key to InterviewSession
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    // ADDED: Question Details
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    @Column(nullable = false)
    private Integer sequenceNumber;

    // ADDED: Question Generation Info
    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by")
    private QuestionGenerationSource generatedBy;

    @Column(name = "is_follow_up")
    private Boolean isFollowUp;

    // ADDED: Parent Question (for follow-ups)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_question_id")
    private InterviewQuestion parentQuestion;

    // ADDED: Question Context
    @Column(columnDefinition = "LONGTEXT")
    private String context;

    @Column(columnDefinition = "LONGTEXT")
    private String expectedAnswer;

    @Column(name = "difficulty_level")
    private String difficultyLevel;

    // ADDED: Timing
    @Column(name = "asked_at")
    private LocalDateTime askedAt;

    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;

    @Column(name = "answer_timeout_seconds")
    private Integer answerTimeoutSeconds;

    // ADDED: Metadata
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isFollowUp == null) {
            isFollowUp = false;
        }
        if (generatedBy == null) {
            generatedBy = QuestionGenerationSource.TEMPLATE;
        }
        if (answerTimeoutSeconds == null) {
            answerTimeoutSeconds = 300; // 5 minutes default
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ADDED: Constructors
    public InterviewQuestion() {}

    public InterviewQuestion(InterviewSession session, String questionText, QuestionType questionType, Integer sequenceNumber) {
        this.session = session;
        this.questionText = questionText;
        this.questionType = questionType;
        this.sequenceNumber = sequenceNumber;
        this.isFollowUp = false;
    }

    // ADDED: Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InterviewSession getSession() { return session; }
    public void setSession(InterviewSession session) { this.session = session; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }

    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public QuestionGenerationSource getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(QuestionGenerationSource generatedBy) { this.generatedBy = generatedBy; }

    public Boolean getIsFollowUp() { return isFollowUp; }
    public void setIsFollowUp(Boolean followUp) { isFollowUp = followUp; }

    public InterviewQuestion getParentQuestion() { return parentQuestion; }
    public void setParentQuestion(InterviewQuestion parentQuestion) { this.parentQuestion = parentQuestion; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getExpectedAnswer() { return expectedAnswer; }
    public void setExpectedAnswer(String expectedAnswer) { this.expectedAnswer = expectedAnswer; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public LocalDateTime getAskedAt() { return askedAt; }
    public void setAskedAt(LocalDateTime askedAt) { this.askedAt = askedAt; }

    public LocalDateTime getResponseDeadline() { return responseDeadline; }
    public void setResponseDeadline(LocalDateTime responseDeadline) { this.responseDeadline = responseDeadline; }

    public Integer getAnswerTimeoutSeconds() { return answerTimeoutSeconds; }
    public void setAnswerTimeoutSeconds(Integer answerTimeoutSeconds) { this.answerTimeoutSeconds = answerTimeoutSeconds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

// ADDED: Question Type Enum


// ADDED: Question Generation Source
