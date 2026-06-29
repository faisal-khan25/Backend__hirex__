package com.hirex.repository;
import com.hirex.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    // ADDED: Find by session
    Optional<InterviewEvaluation> findBySessionId(Long sessionId);

    // ADDED: Find by recommendation
    List<InterviewEvaluation> findByFinalRecommendation(RecommendationStatus recommendation);

    // ADDED: Find pending reviews
    @Query("SELECT e FROM InterviewEvaluation e WHERE e.finalRecommendation = 'PENDING_REVIEW'")
    List<InterviewEvaluation> findPendingReviews();

    // ADDED: Find high performers (overall rating >= 80)
    @Query("SELECT e FROM InterviewEvaluation e WHERE e.overallRating >= :minRating")
    List<InterviewEvaluation> findHighPerformers(@Param("minRating") Double minRating);

    // ADDED: Find candidates for specific job with high overall rating
    @Query("SELECT e FROM InterviewEvaluation e WHERE e.session.application.job.id = :jobId AND e.overallRating >= :minRating ORDER BY e.overallRating DESC")
    List<InterviewEvaluation> findQualifiedCandidatesForJob(@Param("jobId") Long jobId, @Param("minRating") Double minRating);

    // ADDED: Find by technical skills threshold
    @Query("SELECT e FROM InterviewEvaluation e WHERE e.technicalSkillsScore >= :minScore ORDER BY e.technicalSkillsScore DESC")
    List<InterviewEvaluation> findByTechnicalSkillsThreshold(@Param("minScore") Double minScore);

    // ADDED: Find by communication skills
    @Query("SELECT e FROM InterviewEvaluation e WHERE e.communicationScore >= :minScore ORDER BY e.communicationScore DESC")
    List<InterviewEvaluation> findByCommunicationSkillsThreshold(@Param("minScore") Double minScore);

    // ADDED: Calculate average score for job position
    @Query("SELECT AVG(e.overallRating) FROM InterviewEvaluation e WHERE e.session.application.job.id = :jobId")
    Double getAverageScoreForJob(@Param("jobId") Long jobId);

    // ADDED: Count by recommendation
    long countByFinalRecommendation(RecommendationStatus recommendation);

    // ADDED: Get top candidates by overall rating
    @Query("SELECT e FROM InterviewEvaluation e ORDER BY e.overallRating DESC LIMIT :limit")
    List<InterviewEvaluation> getTopCandidates(@Param("limit") int limit);
}