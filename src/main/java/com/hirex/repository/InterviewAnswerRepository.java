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
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {

    // ADDED: Find answers by session
    List<InterviewAnswer> findBySessionIdOrderByAnsweredAt(Long sessionId);

    // ADDED: Find answers by question
    List<InterviewAnswer> findByQuestionId(Long questionId);

    // ADDED: Find answer by session and question
    Optional<InterviewAnswer> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    // ADDED: Find answered questions count
    long countBySessionId(Long sessionId);

    // ADDED: Find by status
    List<InterviewAnswer> findByAnswerStatus(AnswerStatus status);

    // ADDED: Find answers needing review
    @Query("SELECT a FROM InterviewAnswer a WHERE a.session.id = :sessionId AND a.answerStatus = 'UNDER_REVIEW'")
    List<InterviewAnswer> findAnswersUnderReview(@Param("sessionId") Long sessionId);

    // ADDED: Calculate average duration for session
    @Query("SELECT AVG(a.durationSeconds) FROM InterviewAnswer a WHERE a.session.id = :sessionId")
    Double getAverageAnswerDuration(@Param("sessionId") Long sessionId);

    // ADDED: Calculate average response time
    @Query("SELECT AVG(CAST((a.answeredAt - q.askedAt) AS LONG)) FROM InterviewAnswer a JOIN InterviewQuestion q ON a.question.id = q.id WHERE a.session.id = :sessionId")
    Double getAverageResponseTime(@Param("sessionId") Long sessionId);

    // ADDED: Calculate average confidence
    @Query("SELECT AVG(a.confidenceScore) FROM InterviewAnswer a WHERE a.session.id = :sessionId")
    Double getAverageConfidenceScore(@Param("sessionId") Long sessionId);

    // ADDED: Calculate average relevance
    @Query("SELECT AVG(a.relevanceScore) FROM InterviewAnswer a WHERE a.session.id = :sessionId")
    Double getAverageRelevanceScore(@Param("sessionId") Long sessionId);
}