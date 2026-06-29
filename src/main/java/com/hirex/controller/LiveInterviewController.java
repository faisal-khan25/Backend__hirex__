package com.hirex.controller;

import com.hirex.dto.LiveInterviewDto.*;
import com.hirex.service.LiveInterviewService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/live-interview")
public class LiveInterviewController {

    private static final Logger log = LoggerFactory.getLogger(LiveInterviewController.class);

    private final LiveInterviewService liveService;

    public LiveInterviewController(LiveInterviewService liveService) {
        this.liveService = liveService;
        log.info("✓ LiveInterviewController initialized successfully");
    }

    // =========================================================================
    // 1.  Recruiter creates a live session
    //     POST /api/live-interview/create
    //     Security: MANAGER only (enforced in SecurityConfig)
    //
    //     UPDATED: request body now accepts `assignedApplicantIds` (List<Long>)
    //     which the recruiter selects from the applicant picker in the UI.
    // =========================================================================
    @PostMapping("/create")
    public ResponseEntity<LiveSessionResponse> createSession(
            @Valid @RequestBody CreateLiveSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("POST /api/live-interview/create called by: {}",
                userDetails != null ? userDetails.getUsername() : "UNKNOWN");
        log.debug("Request payload — interviewSessionId: {}, assignedApplicantIds: {}",
                request.getInterviewSessionId(), request.getAssignedApplicantIds());

        LiveSessionResponse response = liveService.createLiveSession(
                request, userDetails.getUsername());

        log.info("✓ Live session created: sessionId={}, token={}, assignedApplicants={}",
                response.getLiveSessionId(),
                response.getSessionToken(),
                response.getAssignedApplicantIds());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // 2.  Participant validates token and fetches session info
    //     GET /api/live-interview/join/{token}
    //
    //     SECURITY: Only the recruiter OR an assigned applicant can call this.
    //     Any other authenticated user gets 403.
    // =========================================================================
    @GetMapping("/join/{token}")
    public ResponseEntity<LiveSessionResponse> joinSession(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("GET /api/live-interview/join/{} called by: {}", token,
                userDetails != null ? userDetails.getUsername() : "UNKNOWN");

        LiveSessionResponse response = liveService.getSessionByToken(
                token, userDetails.getUsername());

        log.debug("✓ Session joined: sessionId={}", response.getLiveSessionId());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 3.  Recruiter ends the interview
    //     POST /api/live-interview/{id}/end
    //     Security: MANAGER only (enforced in SecurityConfig)
    // =========================================================================
    @PostMapping("/{id:\\d+}/end")
    public ResponseEntity<LiveSessionResponse> endInterview(
            @PathVariable Long id,
            @Valid @RequestBody EndInterviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("POST /api/live-interview/{}/end called by: {}", id,
                userDetails != null ? userDetails.getUsername() : "UNKNOWN");

        LiveSessionResponse response = liveService.endInterview(
                id, request, userDetails.getUsername());

        log.info("✓ Interview ended: sessionId={}", response.getLiveSessionId());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 4.  Get ICE server configuration
    //     GET /api/live-interview/ice-servers
    // =========================================================================
    @GetMapping("/ice-servers")
    public ResponseEntity<List<IceServerConfig>> getIceServers(
            @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("GET /api/live-interview/ice-servers called by: {}",
                userDetails != null ? userDetails.getUsername() : "UNKNOWN");

        List<IceServerConfig> servers = liveService.getIceServers();
        log.debug("✓ Returned {} ICE servers", servers.size());
        return ResponseEntity.ok(servers);
    }

    // =========================================================================
    // 5.  Get session by DB id  (recruiter re-opens panel)
    //     GET /api/live-interview/{id}
    //     Returns LiveSessionSummaryResponse — token NOT included.
    // =========================================================================
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<LiveSessionSummaryResponse> getSession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("GET /api/live-interview/{} called by: {}", id,
                userDetails != null ? userDetails.getUsername() : "UNKNOWN");

        LiveSessionSummaryResponse response = liveService.getSessionById(
                id, userDetails.getUsername());

        log.debug("✓ Session retrieved: sessionId={}", response.getLiveSessionId());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 6.  Candidate polls for an active live session on their application
    //     GET /api/live-interview/candidate/{applicationId}
    //     Security: JOBSEEKER only (enforced in SecurityConfig)
    //
    //     UPDATED: Backend now checks assignedApplicantIds instead of just
    //     the single candidate_id field. Only returns a token if the logged-in
    //     user's ID is in the assigned set — controls the "Join" button.
    // =========================================================================
    @GetMapping("/candidate/{applicationId:\\d+}")
    public ResponseEntity<Map<String, String>> getSessionForCandidate(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("GET /api/live-interview/candidate/{} called by: {}",
                applicationId, userDetails != null ? userDetails.getUsername() : "UNKNOWN");

        return liveService.getActiveSessionForCandidate(applicationId, userDetails.getUsername())
                .map(token -> {
                    Map<String, String> body = new HashMap<>();
                    body.put("sessionToken", token);
                    log.debug("✓ Active live session found for applicationId={}", applicationId);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> {
                    log.debug("No active live session (or not assigned) for applicationId={}", applicationId);
                    return ResponseEntity.noContent().<Map<String, String>>build();
                });
    }

    // =========================================================================
    // 7.  NEW: Recruiter fetches assignable applicants for a given interview session
    //     GET /api/live-interview/assignable-applicants/{interviewSessionId}
    //     Security: MANAGER only
    //
    //     Returns a list of { id, name, email } for all shortlisted applicants
    //     linked to the same job as the given interview session. The recruiter
    //     picks from this list in the UI when creating a live session.
    // =========================================================================
    @GetMapping("/assignable-applicants/{interviewSessionId:\\d+}")
    public ResponseEntity<List<Map<String, Object>>> getAssignableApplicants(
            @PathVariable Long interviewSessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("GET /api/live-interview/assignable-applicants/{} called by: {}",
                interviewSessionId, userDetails != null ? userDetails.getUsername() : "UNKNOWN");

        List<Map<String, Object>> applicants = liveService.getAssignableApplicants(
                interviewSessionId, userDetails.getUsername());

        log.debug("✓ Returned {} assignable applicants for interviewSessionId={}",
                applicants.size(), interviewSessionId);

        return ResponseEntity.ok(applicants);
    }

    // =========================================================================
    // 8.  NEW: Recruiter fetches assignable applicants directly by jobId
    //     GET /api/live-interview/assignable-applicants/by-job/{jobId}
    //     Security: MANAGER only
    //
    //     Does NOT require a prior AI interview session — returns all shortlisted
    //     applicants for the given job. This is the preferred entry point when the
    //     recruiter initiates a live interview from the chat panel, because at that
    //     point an AI interview session may not yet exist.
    // =========================================================================
    @GetMapping("/assignable-applicants/by-job/{jobId:\\d+}")
    public ResponseEntity<List<Map<String, Object>>> getAssignableApplicantsByJob(
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("GET /api/live-interview/assignable-applicants/by-job/{} called by: {}",
                jobId, userDetails != null ? userDetails.getUsername() : "UNKNOWN");

        List<Map<String, Object>> applicants = liveService.getAssignableApplicantsByJob(
                jobId, userDetails.getUsername());

        log.debug("✓ Returned {} assignable applicants for jobId={}", applicants.size(), jobId);

        return ResponseEntity.ok(applicants);
    }
}