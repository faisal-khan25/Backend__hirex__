package com.hirex.dto;

import java.time.LocalDateTime;

public class InterviewEvaluationDto {
    private Long id;
    private Long sessionId;
    private Double technicalSkillsScore;
    private String technicalFeedback;
    private Double domainKnowledgeScore;
    private String domainKnowledgeFeedback;
    private Double communicationScore;
    private String communicationFeedback;
    private Double confidenceScore;
    private String confidenceFeedback;
    private Double problemSolvingScore;
    private String problemSolvingFeedback;
    private Double overallRating;
    private String finalRecommendation;
    private String interviewPassStatus;
    private String strengths;
    private String weaknesses;
    private String developmentAreas;
    private String nextSteps;
    private Integer totalQuestionsAsked;
    private Integer totalQuestionsAnswered;
    private Double averageAnswerDuration;
    private Double averageResponseTime;
    private Double completionPercentage;
    private Double percentileRank;
    private LocalDateTime evaluatedAt;

    public InterviewEvaluationDto() {}

    public InterviewEvaluationDto(Long id, Double overallRating, String finalRecommendation) {
        this.id = id;
        this.overallRating = overallRating;
        this.finalRecommendation = finalRecommendation;
    }

    // Getters and Setters (abbreviated for space - include all)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInterviewPassStatus() { return interviewPassStatus; }
    public void setInterviewPassStatus(String interviewPassStatus) {
        this.interviewPassStatus = interviewPassStatus;
    }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Double getTechnicalSkillsScore() { return technicalSkillsScore; }
    public void setTechnicalSkillsScore(Double technicalSkillsScore) { this.technicalSkillsScore = technicalSkillsScore; }

    public String getTechnicalFeedback() { return technicalFeedback; }
    public void setTechnicalFeedback(String technicalFeedback) { this.technicalFeedback = technicalFeedback; }

    public Double getDomainKnowledgeScore() { return domainKnowledgeScore; }
    public void setDomainKnowledgeScore(Double domainKnowledgeScore) { this.domainKnowledgeScore = domainKnowledgeScore; }

    public String getDomainKnowledgeFeedback() { return domainKnowledgeFeedback; }
    public void setDomainKnowledgeFeedback(String domainKnowledgeFeedback) { this.domainKnowledgeFeedback = domainKnowledgeFeedback; }

    public Double getCommunicationScore() { return communicationScore; }
    public void setCommunicationScore(Double communicationScore) { this.communicationScore = communicationScore; }

    public String getCommunicationFeedback() { return communicationFeedback; }
    public void setCommunicationFeedback(String communicationFeedback) { this.communicationFeedback = communicationFeedback; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getConfidenceFeedback() { return confidenceFeedback; }
    public void setConfidenceFeedback(String confidenceFeedback) { this.confidenceFeedback = confidenceFeedback; }

    public Double getProblemSolvingScore() { return problemSolvingScore; }
    public void setProblemSolvingScore(Double problemSolvingScore) { this.problemSolvingScore = problemSolvingScore; }

    public String getProblemSolvingFeedback() { return problemSolvingFeedback; }
    public void setProblemSolvingFeedback(String problemSolvingFeedback) { this.problemSolvingFeedback = problemSolvingFeedback; }

    public Double getOverallRating() { return overallRating; }
    public void setOverallRating(Double overallRating) { this.overallRating = overallRating; }

    public String getFinalRecommendation() { return finalRecommendation; }
    public void setFinalRecommendation(String finalRecommendation) { this.finalRecommendation = finalRecommendation; }

    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }

    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }

    public String getDevelopmentAreas() { return developmentAreas; }
    public void setDevelopmentAreas(String developmentAreas) { this.developmentAreas = developmentAreas; }

    public String getNextSteps() { return nextSteps; }
    public void setNextSteps(String nextSteps) { this.nextSteps = nextSteps; }

    public Integer getTotalQuestionsAsked() { return totalQuestionsAsked; }
    public void setTotalQuestionsAsked(Integer totalQuestionsAsked) { this.totalQuestionsAsked = totalQuestionsAsked; }

    public Integer getTotalQuestionsAnswered() { return totalQuestionsAnswered; }
    public void setTotalQuestionsAnswered(Integer totalQuestionsAnswered) { this.totalQuestionsAnswered = totalQuestionsAnswered; }

    public Double getAverageAnswerDuration() { return averageAnswerDuration; }
    public void setAverageAnswerDuration(Double averageAnswerDuration) { this.averageAnswerDuration = averageAnswerDuration; }

    public Double getAverageResponseTime() { return averageResponseTime; }
    public void setAverageResponseTime(Double averageResponseTime) { this.averageResponseTime = averageResponseTime; }

    public Double getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(Double completionPercentage) { this.completionPercentage = completionPercentage; }

    public Double getPercentileRank() { return percentileRank; }
    public void setPercentileRank(Double percentileRank) { this.percentileRank = percentileRank; }

    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
