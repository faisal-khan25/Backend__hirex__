package com.hirex.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "interview_evaluations",
        indexes = {
                @Index(name = "idx_eval_session", columnList = "session_id"),
                @Index(name = "idx_eval_overall_rating", columnList = "overall_rating")
        }
)
public class InterviewEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "interview_pass_status", length = 50)
    private String interviewPassStatus;

    // ADDED: Foreign Key to InterviewSession
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private InterviewSession session;

    // ADDED: Technical & Domain Skills
    @Column(nullable = false)
    private Double technicalSkillsScore; // 0-100

    @Column(columnDefinition = "LONGTEXT")
    private String technicalFeedback;

    @Column(nullable = false)
    private Double domainKnowledgeScore; // 0-100

    @Column(columnDefinition = "LONGTEXT")
    private String domainKnowledgeFeedback;

    // ADDED: Communication & Soft Skills
    @Column(nullable = false)
    private Double communicationScore; // 0-100

    @Column(columnDefinition = "LONGTEXT")
    private String communicationFeedback;

    @Column(nullable = false)
    private Double confidenceScore; // 0-100

    @Column(columnDefinition = "LONGTEXT")
    private String confidenceFeedback;

    // ADDED: Problem Solving & Analytical
    @Column(nullable = false)
    private Double problemSolvingScore; // 0-100

    @Column(columnDefinition = "LONGTEXT")
    private String problemSolvingFeedback;

    // ADDED: Overall Rating
    @Column(nullable = false)
    private Double overallRating; // 0-100

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationStatus finalRecommendation;

    // ADDED: Detailed Analysis
    @Column(columnDefinition = "LONGTEXT")
    private String strengths;

    @Column(columnDefinition = "LONGTEXT")
    private String weaknesses;

    @Column(columnDefinition = "LONGTEXT")
    private String developmentAreas;

    @Column(columnDefinition = "LONGTEXT")
    private String nextSteps;

    // ADDED: Interview Metrics
    @Column(name = "total_questions_asked")
    private Integer totalQuestionsAsked;

    @Column(name = "total_questions_answered")
    private Integer totalQuestionsAnswered;

    @Column(name = "average_answer_duration_seconds")
    private Double averageAnswerDuration;

    @Column(name = "average_response_time_seconds")
    private Double averageResponseTime;

    @Column(name = "completion_percentage")
    private Double completionPercentage;

    // ADDED: Comparative Analysis
    @Column(name = "percentile_rank")
    private Double percentileRank;

    @Column(columnDefinition = "LONGTEXT")
    private String comparisonNotes;

    // ADDED: Custom Evaluation Data (JSON)
    @Column(columnDefinition = "LONGTEXT")
    private String evaluationMetadata;

    // ADDED: Evaluator Info
    @Column(name = "evaluated_by")
    private String evaluatedBy;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    @Column(name = "review_notes")
    private String reviewNotes;

    // ADDED: Timestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (finalRecommendation == null) {
            finalRecommendation = RecommendationStatus.PENDING_REVIEW;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ADDED: Constructors
    public InterviewEvaluation() {}

    public InterviewEvaluation(InterviewSession session) {
        this.session = session;
        this.finalRecommendation = RecommendationStatus.PENDING_REVIEW;
        this.interviewPassStatus = "PENDING";
    }

    // ADDED: Helper Methods
//    public void calculateOverallRating() {
//        double sum = technicalSkillsScore + domainKnowledgeScore + communicationScore +
//                confidenceScore + problemSolvingScore;
//        this.overallRating = sum / 5.0;
//    }
//    public void calculateOverallRating() {
//        double sum = technicalSkillsScore + domainKnowledgeScore + communicationScore +
//                confidenceScore + problemSolvingScore;
//        this.overallRating = sum / 5.0;
//
//        // ADD THESE LINES:
//        if (overallRating >= 80) {
//            this.interviewPassStatus = "PASSED";
//        } else if (overallRating >= 60) {
//            this.interviewPassStatus = "UNDER_REVIEW";
//        } else {
//            this.interviewPassStatus = "FAILED";
//        }
    // FILE: src/main/java/com/hirex/entity/InterviewEvaluation.java
// REPLACE calculateOverallRating() with:

    public void calculateOverallRating() {
        double sum = technicalSkillsScore + domainKnowledgeScore + communicationScore
                + confidenceScore + problemSolvingScore;
        this.overallRating = sum / 5.0;

        // Requirement: score >= 60 → PASS, score < 60 → FAIL
        if (overallRating >= 60) {
            this.interviewPassStatus = "PASSED";
        } else {
            this.interviewPassStatus = "FAILED";
        }

    }





    // ADDED: Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InterviewSession getSession() { return session; }
    public void setSession(InterviewSession session) { this.session = session; }

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

    public RecommendationStatus getFinalRecommendation() { return finalRecommendation; }
    public void setFinalRecommendation(RecommendationStatus finalRecommendation) { this.finalRecommendation = finalRecommendation; }

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

    public String getComparisonNotes() { return comparisonNotes; }
    public void setComparisonNotes(String comparisonNotes) { this.comparisonNotes = comparisonNotes; }

    public String getEvaluationMetadata() { return evaluationMetadata; }
    public void setEvaluationMetadata(String evaluationMetadata) { this.evaluationMetadata = evaluationMetadata; }

    public String getEvaluatedBy() { return evaluatedBy; }
    public void setEvaluatedBy(String evaluatedBy) { this.evaluatedBy = evaluatedBy; }

    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getInterviewPassStatus() { return interviewPassStatus; }
    public void setInterviewPassStatus(String interviewPassStatus) {
        this.interviewPassStatus = interviewPassStatus;
    }
}

