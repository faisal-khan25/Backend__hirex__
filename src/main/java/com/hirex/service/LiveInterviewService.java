package com.hirex.service;

import com.hirex.dto.LiveInterviewDto.*;
import com.hirex.entity.*;
import com.hirex.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class LiveInterviewService {

    private static final Logger log = LoggerFactory.getLogger(LiveInterviewService.class);

    private static final String QUEUE_OFFER   = "/queue/offer";
    private static final String QUEUE_ANSWER  = "/queue/answer";
    private static final String QUEUE_ICE     = "/queue/ice";
    private static final String TOPIC_SESSION = "/topic/session/";
    private static final String TOPIC_CAMERA  = "/topic/camera/";

    private final ConcurrentHashMap<Long, Set<String>> presenceMap   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LiveInterviewSession> sessionCache = new ConcurrentHashMap<>();

    private final LiveInterviewSessionRepository liveRepo;
    private final InterviewSessionRepository     interviewRepo;
    private final UserRepository                 userRepo;
    private final ApplicationRepository          applicationRepo;
    private final SimpMessagingTemplate          messaging;

    public LiveInterviewService(LiveInterviewSessionRepository liveRepo,
                                InterviewSessionRepository interviewRepo,
                                UserRepository userRepo,
                                ApplicationRepository applicationRepo,
                                SimpMessagingTemplate messaging) {
        this.liveRepo        = liveRepo;
        this.interviewRepo   = interviewRepo;
        this.userRepo        = userRepo;
        this.applicationRepo = applicationRepo;
        this.messaging       = messaging;
    }

    // =========================================================================
    // 1.  Create session  (Recruiter)
    //
    //  UPDATED: Now accepts `assignedApplicantIds` from the request.
    //  The recruiter explicitly selects which applicants can join this session.
    //  All assigned applicant IDs are stored in the join table.
    //  If no assignedApplicantIds are provided, falls back to deriving the
    //  single candidate from the interview's application (backward-compat).
    // =========================================================================

    public LiveSessionResponse createLiveSession(CreateLiveSessionRequest request,
                                                 String recruiterEmail) {

        User recruiter = userRepo.findByEmail(recruiterEmail)
                .orElseThrow(() -> new NoSuchElementException("Recruiter not found: " + recruiterEmail));

        if (recruiter.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Only MANAGER users can create live sessions.");
        }

        // ── Resolve interview session ─────────────────────────────────────────
        // Path A: interviewSessionId provided (existing flow)
        // Path B: jobId provided without interviewSessionId — look up or auto-create
        //         an interview session for the first assigned applicant's application.
        //         This allows live interviews to be started even when no AI interview
        //         has been scheduled yet (fixes the 404 from /api/interview/application/{id}).
        InterviewSession interview;

        if (request.getInterviewSessionId() != null) {
            interview = interviewRepo.findById(request.getInterviewSessionId())
                    .orElseThrow(() -> new NoSuchElementException(
                            "InterviewSession not found: " + request.getInterviewSessionId()));
        } else if (request.getJobId() != null && request.getAssignedApplicantIds() != null
                && !request.getAssignedApplicantIds().isEmpty()) {
            // Find the application for the first assigned applicant on this job
            Long primaryApplicantId = request.getAssignedApplicantIds().get(0);
            com.hirex.entity.Application application = applicationRepo
                    .findByJobId(request.getJobId())
                    .stream()
                    .filter(a -> a.getApplicant().getId().equals(primaryApplicantId))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException(
                            "Application not found for applicant " + primaryApplicantId
                                    + " and job " + request.getJobId()));

            // Re-use existing interview session if one exists, otherwise create one
            interview = interviewRepo.findByApplicationId(application.getId())
                    .orElseGet(() -> {
                        InterviewSession newSession = new InterviewSession(
                                application,
                                com.hirex.entity.InterviewType.AI,
                                "DEFAULT_AI_INTERVIEW");
                        newSession.setScheduledAt(java.time.LocalDateTime.now());
                        newSession.setMaxDurationMinutes(60);
                        newSession.setStatus(com.hirex.entity.InterviewStatus.PENDING);
                        newSession.setCandidateName(application.getApplicant().getName());
                        newSession.setPositionTitle(application.getJob().getTitle());
                        newSession.setJobDescription(application.getJob().getDescription());
                        return interviewRepo.save(newSession);
                    });
        } else {
            throw new IllegalArgumentException(
                    "Either interviewSessionId or (jobId + assignedApplicantIds) must be provided.");
        }

        // ── Resolve assigned applicants ───────────────────────────────────
        // Priority 1: explicit assignedApplicantIds from recruiter UI
        // Priority 2: fallback — derive from interview.application.applicant
        Set<Long> assignedIds = new HashSet<>();

        if (request.getAssignedApplicantIds() != null && !request.getAssignedApplicantIds().isEmpty()) {
            // Validate all provided IDs exist as JOBSEEKER users
            for (Long applicantId : request.getAssignedApplicantIds()) {
                User applicant = userRepo.findById(applicantId)
                        .orElseThrow(() -> new NoSuchElementException(
                                "Applicant user not found: " + applicantId));
                if (applicant.getRole() != Role.JOBSEEKER) {
                    throw new IllegalArgumentException(
                            "User " + applicantId + " is not a JOBSEEKER.");
                }
                assignedIds.add(applicantId);
            }
            log.debug("Recruiter explicitly assigned {} applicant(s): {}", assignedIds.size(), assignedIds);
        } else {
            // Backward-compat: derive from the interview's application
            User derivedCandidate = interview.getApplication().getApplicant();
            if (derivedCandidate == null) {
                throw new NoSuchElementException(
                        "No applicant linked to interview " + request.getInterviewSessionId());
            }
            assignedIds.add(derivedCandidate.getId());
            log.debug("No assignedApplicantIds provided — falling back to candidateId={} from interview",
                    derivedCandidate.getId());
        }

        // ── The primary candidate for WebRTC signaling ─────────────────────
        // When only one applicant is assigned, use them. When multiple are
        // assigned, use the interview's application's applicant as the primary
        // candidate (they will all see the join button but WebRTC is 1-on-1).
        User primaryCandidate = interview.getApplication().getApplicant();
        if (primaryCandidate == null || !assignedIds.contains(primaryCandidate.getId())) {
            // Primary candidate is whoever is the first in the assigned set
            Long firstId = assignedIds.iterator().next();
            primaryCandidate = userRepo.findById(firstId)
                    .orElseThrow(() -> new NoSuchElementException("Applicant not found: " + firstId));
        }

        // ── Prevent duplicate active sessions ─────────────────────────────
        liveRepo.findByInterviewSessionId(interview.getId()).ifPresent(existing -> {
            if (existing.getSessionStatus() == LiveSessionStatus.WAITING
                    || existing.getSessionStatus() == LiveSessionStatus.ACTIVE) {
                throw new IllegalStateException(
                        "A live session is already active for interview " + interview.getId());
            }
        });

        String token = UUID.randomUUID().toString().replace("-", "");

        LiveInterviewSession session = new LiveInterviewSession(
                interview, recruiter, primaryCandidate, token, assignedIds);
        liveRepo.save(session);

        log.info("Live session {} created for interview {} by recruiter {} — assigned applicants: {}",
                session.getId(), interview.getId(), recruiterEmail, assignedIds);

        // ── Push real-time invite to ALL assigned applicants via WebSocket ─
        pushInviteToAssignedApplicants(session, interview);

        return toFullResponse(session);
    }

    // =========================================================================
    // 2.  Get session by token  (used by both participants on join)
    //     UPDATED: assertParticipantOrAssigned checks assignedApplicantIds too
    // =========================================================================

    @Transactional(readOnly = true)
    public LiveSessionResponse getSessionByToken(String token, String userEmail) {
        LiveInterviewSession session = requireActiveSession(token);
        assertParticipantOrAssigned(session, userEmail);
        return toFullResponse(session);
    }

    // =========================================================================
    // 3.  Get session by DB id  (recruiter re-opens panel)
    // =========================================================================

    @Transactional(readOnly = true)
    public LiveSessionSummaryResponse getSessionById(Long id, String userEmail) {
        LiveInterviewSession session = liveRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Live session not found: " + id));
        assertParticipantOrAssigned(session, userEmail);
        return toSummaryResponse(session);
    }

    // =========================================================================
    // 3b. Candidate polls for an active live session on their application
    //     UPDATED: uses findActiveForAssignedCandidate — checks assignedApplicantIds
    //
    //     Only returns a token if the logged-in user's ID is in the assigned set.
    //     This is the gate that controls the "Join Live Interview" button.
    // =========================================================================

    @Transactional(readOnly = true)
    public Optional<String> getActiveSessionForCandidate(Long applicationId,
                                                         String candidateEmail) {
        User candidate = userRepo.findByEmail(candidateEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + candidateEmail));

        // SECURITY: Only return a token if this user's ID is in assignedApplicantIds
        return liveRepo.findActiveForAssignedCandidate(
                candidate.getId(),
                applicationId,
                List.of(LiveSessionStatus.WAITING, LiveSessionStatus.ACTIVE),
                LocalDateTime.now()
        ).map(LiveInterviewSession::getSessionToken);
    }

    // =========================================================================
    // 4.  Participant joins — activates ONLY when BOTH are present
    //     UPDATED: assertParticipantOrAssigned allows any assigned applicant to join
    // =========================================================================

    public void onParticipantJoin(JoinSessionMessage msg, String userEmail) {
        LiveInterviewSession session = requireActiveSession(msg.getSessionToken());
        assertParticipantOrAssigned(session, userEmail);

        boolean isCandidate = isAssignedApplicant(session, userEmail);
        boolean isRecruiter = session.getRecruiter().getEmail().equals(userEmail);
        String claimedRole  = msg.getRole();

        if (isCandidate && !"CANDIDATE".equals(claimedRole)) {
            throw new AccessDeniedException("Role mismatch: you are the CANDIDATE for this session.");
        }
        if (isRecruiter && !"RECRUITER".equals(claimedRole)) {
            throw new AccessDeniedException("Role mismatch: you are the RECRUITER for this session.");
        }

        Set<String> joined = presenceMap.computeIfAbsent(
                session.getId(), id -> ConcurrentHashMap.newKeySet());
        joined.add(userEmail);

        log.info("Session {} — {} joined. Present: {}", session.getId(), userEmail, joined.size());

        sessionCache.put(msg.getSessionToken(), session);

        if (joined.size() >= 2 && session.getSessionStatus() == LiveSessionStatus.WAITING) {
            session.setSessionStatus(LiveSessionStatus.ACTIVE);
            session.setInterviewStartTime(LocalDateTime.now());
            liveRepo.save(session);

            log.info("Session {} is now ACTIVE — both participants connected.", session.getId());
            broadcastSessionStatus(session, "Interview started – both participants connected.");
        }
    }

    // =========================================================================
    // 5.  Camera status update — BOTH candidate AND recruiter
    // =========================================================================

    public void updateCameraStatus(CameraStatusMessage msg, String userEmail) {
        LiveInterviewSession session = requireActiveSession(msg.getSessionToken());
        assertParticipantOrAssigned(session, userEmail);

        boolean isCandidate = isAssignedApplicant(session, userEmail);
        boolean isRecruiter = session.getRecruiter().getEmail().equals(userEmail);

        if (isCandidate && !"CANDIDATE".equals(msg.getRole())) {
            throw new AccessDeniedException("Role mismatch for camera update.");
        }
        if (isRecruiter && !"RECRUITER".equals(msg.getRole())) {
            throw new AccessDeniedException("Role mismatch for camera update.");
        }

        int updatedOffCount;

        if (isCandidate) {
            boolean wasEnabled = session.isCameraEnabled();
            session.setCameraEnabled(msg.isCameraEnabled());
            if (wasEnabled && !msg.isCameraEnabled()) {
                session.setCameraOffCount(session.getCameraOffCount() + 1);
                session.setCameraDisabledAt(LocalDateTime.now());
            }
            updatedOffCount = session.getCameraOffCount();
        } else {
            boolean wasEnabled = session.isRecruiterCameraEnabled();
            session.setRecruiterCameraEnabled(msg.isCameraEnabled());
            if (wasEnabled && !msg.isCameraEnabled()) {
                session.setRecruiterCameraOffCount(session.getRecruiterCameraOffCount() + 1);
                session.setRecruiterCameraDisabledAt(LocalDateTime.now());
            }
            updatedOffCount = session.getRecruiterCameraOffCount();
        }

        liveRepo.save(session);
        sessionCache.put(msg.getSessionToken(), session);

        CameraStatusNotification notification = new CameraStatusNotification();
        notification.setLiveSessionId(session.getId());
        notification.setParticipantRole(msg.getRole());
        notification.setCameraEnabled(msg.isCameraEnabled());
        notification.setReason(msg.getReason());
        notification.setCameraOffCount(updatedOffCount);
        notification.setTimestamp(LocalDateTime.now());

        messaging.convertAndSend(TOPIC_CAMERA + session.getId(), notification);
    }

    // =========================================================================
    // 6.  WebRTC Signaling — point-to-point relay (UNCHANGED)
    // =========================================================================

    public void relayOffer(WebRtcOfferMessage msg, String senderEmail) {
        LiveInterviewSession session = requireActiveSessionCached(msg.getSessionToken());
        assertParticipantOrAssigned(session, senderEmail);

        if (!isAssignedApplicant(session, senderEmail)) {
            throw new AccessDeniedException("Only the CANDIDATE can send the WebRTC offer.");
        }

        String targetEmail = session.getRecruiter().getEmail();
        messaging.convertAndSendToUser(targetEmail, QUEUE_OFFER, msg);
        log.debug("Relayed offer from {} to {}", senderEmail, targetEmail);
    }

    public void relayAnswer(WebRtcAnswerMessage msg, String senderEmail) {
        LiveInterviewSession session = requireActiveSessionCached(msg.getSessionToken());
        assertParticipantOrAssigned(session, senderEmail);

        if (!session.getRecruiter().getEmail().equals(senderEmail)) {
            throw new AccessDeniedException("Only the RECRUITER can send the WebRTC answer.");
        }

        String targetEmail = session.getCandidate().getEmail();
        messaging.convertAndSendToUser(targetEmail, QUEUE_ANSWER, msg);
        log.debug("Relayed answer from {} to {}", senderEmail, targetEmail);
    }

    public void relayIceCandidate(IceCandidateMessage msg, String senderEmail) {
        LiveInterviewSession session = requireActiveSessionCached(msg.getSessionToken());
        assertParticipantOrAssigned(session, senderEmail);

        String targetEmail = isAssignedApplicant(session, senderEmail)
                ? session.getRecruiter().getEmail()
                : session.getCandidate().getEmail();

        messaging.convertAndSendToUser(targetEmail, QUEUE_ICE, msg);
    }

    // =========================================================================
    // 7.  End interview  (Recruiter)
    // =========================================================================

    public LiveSessionResponse endInterview(Long liveSessionId,
                                            EndInterviewRequest request,
                                            String recruiterEmail) {

        LiveInterviewSession session = liveRepo.findById(liveSessionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "LiveInterviewSession not found: " + liveSessionId));

        if (!session.getSessionToken().equals(request.getSessionToken())) {
            throw new AccessDeniedException("Invalid session token.");
        }

        if (!session.getRecruiter().getEmail().equals(recruiterEmail)) {
            throw new AccessDeniedException("Only the assigned recruiter can end this session.");
        }

        session.setSessionStatus(LiveSessionStatus.ENDED);
        session.setInterviewEndTime(LocalDateTime.now());

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            session.setRecruiterNotes(request.getNotes());
        }

        liveRepo.save(session);

        presenceMap.remove(session.getId());
        sessionCache.remove(session.getSessionToken());

        log.info("Session {} ended by recruiter {}", session.getId(), recruiterEmail);
        broadcastSessionStatus(session, "Interview ended by recruiter.");

        return toFullResponse(session);
    }

    // =========================================================================
    // 8.  Candidate disconnect — wired via WebSocketEventListener
    // =========================================================================

    @Transactional
    public void onParticipantDisconnect(String sessionToken, String userEmail) {
        liveRepo.findBySessionToken(sessionToken).ifPresent(session -> {
            if (session.getSessionStatus() == LiveSessionStatus.ACTIVE
                    || session.getSessionStatus() == LiveSessionStatus.WAITING) {

                boolean isCandidateSide = isAssignedApplicant(session, userEmail);

                if (isCandidateSide) {
                    session.setSessionStatus(LiveSessionStatus.ABANDONED);
                    if (session.isCameraEnabled()) {
                        session.setCameraEnabled(false);
                        session.setCameraOffCount(session.getCameraOffCount() + 1);
                        session.setCameraDisabledAt(LocalDateTime.now());
                    }
                    liveRepo.save(session);

                    presenceMap.remove(session.getId());
                    sessionCache.remove(sessionToken);

                    log.info("Session {} ABANDONED — candidate {} disconnected.",
                            session.getId(), userEmail);
                    broadcastSessionStatus(session, "Candidate disconnected unexpectedly.");

                } else {
                    Set<String> joined = presenceMap.get(session.getId());
                    if (joined != null) joined.remove(userEmail);

                    log.info("Session {} — recruiter {} disconnected.", session.getId(), userEmail);
                    broadcastSessionStatus(session, "Recruiter disconnected — waiting for reconnect.");
                }
            }
        });
    }

    // =========================================================================
    // 9.  ICE server configuration endpoint
    // =========================================================================

    @Transactional(readOnly = true)
    public List<IceServerConfig> getIceServers() {
        return List.of(
                new IceServerConfig("stun:stun.l.google.com:19302"),
                new IceServerConfig("stun:stun1.l.google.com:19302")
        );
    }

    // =========================================================================
    // 10. Get assignable applicants for a given interview session
    //     Called by the recruiter UI before creating a live session so the
    //     recruiter can pick which applicants to assign.
    //     Returns all shortlisted applicants for the same job.
    // =========================================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAssignableApplicants(Long interviewSessionId,
                                                             String recruiterEmail) {
        User recruiter = userRepo.findByEmail(recruiterEmail)
                .orElseThrow(() -> new NoSuchElementException("Recruiter not found: " + recruiterEmail));

        if (recruiter.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Only MANAGER users can fetch assignable applicants.");
        }

        InterviewSession interview = interviewRepo.findById(interviewSessionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "InterviewSession not found: " + interviewSessionId));

        Long jobId = interview.getApplication().getJob().getId();

        return getShortlistedApplicantsForJob(jobId);
    }

    // =========================================================================
    // 11. Get assignable applicants directly by jobId (no AI session required)
    //     This is the primary entry point from the recruiter chat panel —
    //     the recruiter may not have run AI interviews yet, but still wants
    //     to start a live video interview.
    // =========================================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAssignableApplicantsByJob(Long jobId,
                                                                  String recruiterEmail) {
        User recruiter = userRepo.findByEmail(recruiterEmail)
                .orElseThrow(() -> new NoSuchElementException("Recruiter not found: " + recruiterEmail));

        if (recruiter.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Only MANAGER users can fetch assignable applicants.");
        }

        return getShortlistedApplicantsForJob(jobId);
    }

    /** Shared helper: map shortlisted applications for a job to picker DTOs. */
    private List<Map<String, Object>> getShortlistedApplicantsForJob(Long jobId) {
        // Fetch both SHORTLISTED (by status) applicants for the job
        List<com.hirex.entity.Application> applications = applicationRepo.findByJobId(jobId)
                .stream()
                .filter(a -> a.getStatus() == com.hirex.entity.ApplicationStatus.SHORTLISTED
                        || a.getStatus() == com.hirex.entity.ApplicationStatus.HIRED)
                .collect(java.util.stream.Collectors.toList());

        return applications.stream()
                .map(app -> {
                    Map<String, Object> info = new java.util.LinkedHashMap<>();
                    info.put("applicantId", app.getApplicant().getId());
                    info.put("name", app.getApplicant().getName());
                    info.put("email", app.getApplicant().getEmail());
                    info.put("applicationId", app.getId());
                    return info;
                })
                .collect(java.util.stream.Collectors.toList());
    }



    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Push a LIVE_INVITE WebSocket notification to every assigned applicant.
     * Each applicant receives it at /user/queue/live-interview-invite.
     */
    private void pushInviteToAssignedApplicants(LiveInterviewSession session,
                                                InterviewSession interview) {
        LiveInterviewNotification notification = new LiveInterviewNotification();
        notification.setLiveSessionId(session.getId());
        notification.setSessionToken(session.getSessionToken());
        notification.setRecruiterName(session.getRecruiter().getName());
        notification.setType("INVITE");
        notification.setTimestamp(LocalDateTime.now());

        // Try to set company and job title from the interview
        try {
            var application = interview.getApplication();
            if (application != null && application.getJob() != null) {
                var job = application.getJob();
                notification.setJobTitle(job.getTitle());
                if (job.getCompany() != null) {
                    notification.setCompanyName(job.getCompany().getName());
                }
            }
        } catch (Exception e) {
            log.warn("Could not enrich notification with job/company data: {}", e.getMessage());
        }

        for (Long applicantId : session.getAssignedApplicantIds()) {
            try {
                userRepo.findById(applicantId).ifPresent(applicant -> {
                    messaging.convertAndSendToUser(
                            applicant.getEmail(),
                            "/queue/live-interview-invite",
                            notification
                    );
                    log.debug("Sent live-interview invite to applicant {} ({})",
                            applicantId, applicant.getEmail());
                });
            } catch (Exception e) {
                log.warn("Failed to push invite to applicantId={}: {}", applicantId, e.getMessage());
            }
        }
    }

    private LiveInterviewSession requireActiveSession(String token) {
        LiveInterviewSession session = liveRepo.findBySessionToken(token)
                .orElseThrow(() -> new NoSuchElementException(
                        "Live session not found for the provided token."));

        if (session.getSessionStatus() == LiveSessionStatus.ENDED) {
            throw new AccessDeniedException("ENDED: This interview has already been completed.");
        }
        if (LocalDateTime.now().isAfter(session.getTokenExpiresAt())) {
            throw new AccessDeniedException("EXPIRED: This interview link has expired.");
        }
        return session;
    }

    private LiveInterviewSession requireActiveSessionCached(String token) {
        LiveInterviewSession cached = sessionCache.get(token);
        if (cached != null
                && cached.getSessionStatus() != LiveSessionStatus.ENDED
                && LocalDateTime.now().isBefore(cached.getTokenExpiresAt())) {
            return cached;
        }
        LiveInterviewSession session = requireActiveSession(token);
        sessionCache.put(token, session);
        return session;
    }

    /**
     * UPDATED: Allows access if:
     *  - user is the recruiter, OR
     *  - user's ID is in the assignedApplicantIds set (any assigned applicant)
     */
    private void assertParticipantOrAssigned(LiveInterviewSession session, String email) {
        boolean isRecruiter = session.getRecruiter().getEmail().equals(email);
        if (isRecruiter) return;

        if (isAssignedApplicant(session, email)) return;

        throw new AccessDeniedException(
                "Access denied: you are not assigned to this live interview session.");
    }

    /**
     * Returns true if the given email belongs to a user whose ID is in
     * the session's assignedApplicantIds set.
     */
    private boolean isAssignedApplicant(LiveInterviewSession session, String email) {
        return userRepo.findByEmail(email)
                .map(user -> session.getAssignedApplicantIds().contains(user.getId()))
                .orElse(false);
    }

    private void broadcastSessionStatus(LiveInterviewSession session, String message) {
        SessionStatusNotification note = new SessionStatusNotification();
        note.setLiveSessionId(session.getId());
        note.setSessionStatus(session.getSessionStatus());
        note.setMessage(message);
        note.setTimestamp(LocalDateTime.now());
        messaging.convertAndSend(TOPIC_SESSION + session.getId() + "/status", note);
    }

    private LiveSessionResponse toFullResponse(LiveInterviewSession s) {
        LiveSessionResponse r = new LiveSessionResponse();
        r.setLiveSessionId(s.getId());
        r.setSessionToken(s.getSessionToken());
        r.setRecruiterId(s.getRecruiter().getId());
        r.setRecruiterName(s.getRecruiter().getName());
        r.setCandidateId(s.getCandidate().getId());
        r.setCandidateName(s.getCandidate().getName());
        r.setSessionStatus(s.getSessionStatus());
        r.setCameraEnabled(s.isCameraEnabled());
        r.setRecruiterCameraEnabled(s.isRecruiterCameraEnabled());
        r.setCreatedAt(s.getCreatedAt());
        r.setInterviewStartTime(s.getInterviewStartTime());
        r.setInterviewEndTime(s.getInterviewEndTime());
        r.setRecruiterNotes(s.getRecruiterNotes());
        r.setAssignedApplicantIds(new HashSet<>(s.getAssignedApplicantIds()));
        if (s.getInterviewStartTime() != null && s.getInterviewEndTime() != null) {
            r.setDurationSeconds(ChronoUnit.SECONDS.between(
                    s.getInterviewStartTime(), s.getInterviewEndTime()));
        }
        return r;
    }

    private LiveSessionSummaryResponse toSummaryResponse(LiveInterviewSession s) {
        LiveSessionSummaryResponse r = new LiveSessionSummaryResponse();
        r.setLiveSessionId(s.getId());
        r.setRecruiterId(s.getRecruiter().getId());
        r.setRecruiterName(s.getRecruiter().getName());
        r.setCandidateId(s.getCandidate().getId());
        r.setCandidateName(s.getCandidate().getName());
        r.setSessionStatus(s.getSessionStatus());
        r.setCameraEnabled(s.isCameraEnabled());
        r.setRecruiterCameraEnabled(s.isRecruiterCameraEnabled());
        r.setCreatedAt(s.getCreatedAt());
        r.setInterviewStartTime(s.getInterviewStartTime());
        r.setInterviewEndTime(s.getInterviewEndTime());
        r.setAssignedApplicantIds(new HashSet<>(s.getAssignedApplicantIds()));
        if (s.getInterviewStartTime() != null && s.getInterviewEndTime() != null) {
            r.setDurationSeconds(ChronoUnit.SECONDS.between(
                    s.getInterviewStartTime(), s.getInterviewEndTime()));
        }
        return r;
    }
}