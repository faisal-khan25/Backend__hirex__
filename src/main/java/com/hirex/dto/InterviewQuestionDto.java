package com.hirex.dto;

import java.time.LocalDateTime;

// ADDED: Interview Question DTO
public class InterviewQuestionDto {
    private Long id;
    private Long sessionId;
    private String questionText;
    private String questionType;
    private Integer sequenceNumber;
    private String generatedBy;
    private Boolean isFollowUp;
    private String context;
    private String expectedAnswer;
    private String difficultyLevel;
    private LocalDateTime askedAt;
    private LocalDateTime responseDeadline;
    private Integer answerTimeoutSeconds;
    private InterviewAnswerDto answer; // Optional: nested answer

    public InterviewQuestionDto() {}

    public InterviewQuestionDto(Long id, String questionText, String questionType, Integer sequenceNumber) {
        this.id = id;
        this.questionText = questionText;
        this.questionType = questionType;
        this.sequenceNumber = sequenceNumber;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }

    public Boolean getIsFollowUp() { return isFollowUp; }
    public void setIsFollowUp(Boolean followUp) { isFollowUp = followUp; }

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

    public InterviewAnswerDto getAnswer() { return answer; }
    public void setAnswer(InterviewAnswerDto answer) { this.answer = answer; }
}
