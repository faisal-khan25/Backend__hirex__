package com.hirex.repository;


import com.hirex.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// ADDED: Interview Session Repository
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    // ADDED: Find by application
    Optional<InterviewSession> findByApplicationId(Long applicationId);

    // ADDED: Find all sessions for a job
    @Query("SELECT i FROM InterviewSession i WHERE i.application.job.id = :jobId")
    List<InterviewSession> findByJobId(@Param("jobId") Long jobId);

    // ADDED: Find by status
    List<InterviewSession> findByStatus(InterviewStatus status);
    boolean existsByApplicationId(Long applicationId);

    // ADDED: Find pending interviews
    @Query("SELECT i FROM InterviewSession i WHERE i.status = 'PENDING' AND i.scheduledAt <= :now")
    List<InterviewSession> findPendingInterviews(@Param("now") LocalDateTime now);

    // ADDED: Find active interviews
    @Query("SELECT i FROM InterviewSession i WHERE i.status = 'IN_PROGRESS'")
    List<InterviewSession> findActiveInterviews();

    // ADDED: Find completed interviews for reporting
    @Query("SELECT i FROM InterviewSession i WHERE i.status = 'COMPLETED' AND i.application.job.id = :jobId")
    List<InterviewSession> findCompletedInterviewsByJobId(@Param("jobId") Long jobId);

    // ADDED: Count by status
    long countByStatus(InterviewStatus status);

    // ADDED: Find by interview type
    List<InterviewSession> findByInterviewType(InterviewType interviewType);

    // ADDED: Find sessions created in date range
    @Query("SELECT i FROM InterviewSession i WHERE i.createdAt BETWEEN :startDate AND :endDate")
    List<InterviewSession> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // ── Per-job interview counts (used by JobApplicantStatsDto) ─────────

    /** Total interview sessions assigned for a specific job. */
    @Query("SELECT COUNT(i) FROM InterviewSession i WHERE i.application.job.id = :jobId")
    long countByJobId(@Param("jobId") Long jobId);

    /** Completed interview sessions for a specific job. */
//    @Query("SELECT COUNT(i) FROM InterviewSession i WHERE i.application.job.id = :jobId AND i.status = com.hirex.entity.InterviewStatus.COMPLETED")
//    long countCompletedByJobId(@Param("jobId") Long jobId);
    // REPLACE this query:
// @Query("SELECT COUNT(i) FROM InterviewSession i WHERE i.application.job.id = :jobId
//         AND i.status = com.hirex.entity.InterviewStatus.COMPLETED")
// long countCompletedByJobId(@Param("jobId") Long jobId);

// WITH this updated query that includes all post-completion states:
    @Query("""
    SELECT COUNT(i) FROM InterviewSession i
    WHERE i.application.job.id = :jobId
    AND i.status IN (
        com.hirex.entity.InterviewStatus.COMPLETED,
        com.hirex.entity.InterviewStatus.PASSED,
        com.hirex.entity.InterviewStatus.UNDER_REVIEW,
        com.hirex.entity.InterviewStatus.FAILED
    )
    """)
    long countCompletedByJobId(@Param("jobId") Long jobId);
    @Query("SELECT COUNT(i) FROM InterviewSession i " +
            "WHERE i.application.job.id = :jobId " +
            "AND i.status = com.hirex.entity.InterviewStatus.PASSED")
    long countPassedByJobId(@Param("jobId") Long jobId);

    @Query("SELECT COUNT(i) FROM InterviewSession i " +
            "WHERE i.application.job.id = :jobId " +
            "AND i.status = com.hirex.entity.InterviewStatus.FAILED")
    long countFailedByJobId(@Param("jobId") Long jobId);

    @Query("SELECT COUNT(i) FROM InterviewSession i " +
            "WHERE i.application.job.id = :jobId " +
            "AND i.status = com.hirex.entity.InterviewStatus.UNDER_REVIEW")
    long countUnderReviewByJobId(@Param("jobId") Long jobId);

}
