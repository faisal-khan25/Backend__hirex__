package com.hirex.repository;

import com.hirex.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface InterviewAnalyticsRepository extends JpaRepository<InterviewSession, Long> {

    // ADDED: Get interview completion statistics
    @Query("SELECT COUNT(*) FROM InterviewSession WHERE status = 'COMPLETED'")
    long getCompletedInterviewCount();

    // ADDED: Get average interview duration
//    @Query("SELECT AVG(DATEDIFF(MINUTE, startedAt, endedAt)) FROM InterviewSession WHERE status = 'COMPLETED'")
//    Double getAverageInterviewDuration();
    @Query(value = """
    SELECT AVG(TIMESTAMPDIFF(MINUTE, started_at, ended_at))
    FROM interview_session
    WHERE status = 'COMPLETED'
      AND started_at IS NOT NULL
      AND ended_at IS NOT NULL
    """, nativeQuery = true)
    Double getAverageInterviewDuration();

    // ADDED: Get interviews by date range
    @Query("SELECT COUNT(*) FROM InterviewSession WHERE createdAt BETWEEN :startDate AND :endDate")
    long countInterviewsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}