package com.hirex.repository;

import com.hirex.entity.LiveInterviewSession;
import com.hirex.entity.LiveSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiveInterviewSessionRepository extends JpaRepository<LiveInterviewSession, Long> {

    // Used by createLiveSession() to prevent duplicate active sessions
    Optional<LiveInterviewSession> findByInterviewSessionId(Long interviewSessionId);

    // Used by requireActiveSession() and onParticipantDisconnect()
    Optional<LiveInterviewSession> findBySessionToken(String sessionToken);

    /**
     * UPDATED: Candidate polls for an active session assigned to them.
     *
     * Checks the `live_interview_assigned_applicants` join table to verify
     * that the logged-in candidate's userId is in the assigned set.
     * This replaces the old single-candidate check against `candidate_id`.
     *
     * Called by GET /api/live-interview/candidate/{applicationId}
     */
    @Query("SELECT s FROM LiveInterviewSession s " +
            "JOIN s.assignedApplicantIds a " +
            "WHERE a = :candidateUserId " +
            "AND s.interviewSession.application.id = :applicationId " +
            "AND s.sessionStatus IN :activeStatuses " +
            "AND s.tokenExpiresAt > :now")
    Optional<LiveInterviewSession> findActiveForAssignedCandidate(
            @Param("candidateUserId")  Long candidateUserId,
            @Param("applicationId")    Long applicationId,
            @Param("activeStatuses")   List<LiveSessionStatus> activeStatuses,
            @Param("now")              LocalDateTime now);

    /**
     * Kept for backward-compat — used by onParticipantDisconnect which
     * checks candidateId directly on the session entity.
     */
    @Query("SELECT s FROM LiveInterviewSession s " +
            "WHERE s.candidate.id = :candidateId " +
            "AND s.interviewSession.application.id = :applicationId " +
            "AND s.sessionStatus IN :activeStatuses " +
            "AND s.tokenExpiresAt > :now")
    Optional<LiveInterviewSession> findActiveForCandidate(
            @Param("candidateId")      Long candidateId,
            @Param("applicationId")    Long applicationId,
            @Param("activeStatuses")   List<LiveSessionStatus> activeStatuses,
            @Param("now")              LocalDateTime now);
}