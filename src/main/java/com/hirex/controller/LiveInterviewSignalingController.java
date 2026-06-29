package com.hirex.controller;

import com.hirex.dto.LiveInterviewDto.*;
import com.hirex.service.LiveInterviewService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * LiveInterviewSignalingController  — FIXED
 *
 * STOMP WebSocket message handlers for the Live Camera Monitoring feature.
 *
 * FIXES APPLIED:
 *  FIX BUG-C4  : Offer, answer, and ICE relay now use convertAndSendToUser()
 *                (point-to-point) in the service layer. Frontend must subscribe
 *                to /user/queue/offer, /user/queue/answer, /user/queue/ice
 *                instead of the old broadcast topics.
 *  FIX BUG-H1  : Camera handler now accepts BOTH CANDIDATE and RECRUITER roles.
 *  FIX BUG-H4  : @Valid added to all @Payload parameters.
 *
 * Updated subscription table:
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  Direction    Destination                  Payload                       │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │  → Server     /app/live/join               JoinSessionMessage            │
 * │  → Server     /app/live/offer              WebRtcOfferMessage            │
 * │  → Server     /app/live/answer             WebRtcAnswerMessage           │
 * │  → Server     /app/live/ice                IceCandidateMessage           │
 * │  → Server     /app/live/camera             CameraStatusMessage           │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │  ← Unicast    /user/queue/offer            WebRtcOfferMessage   (recruiter only)  │
 * │  ← Unicast    /user/queue/answer           WebRtcAnswerMessage  (candidate only)  │
 * │  ← Unicast    /user/queue/ice              IceCandidateMessage  (target only)     │
 * │  ← Broadcast  /topic/session/{id}/status  SessionStatusNotification              │
 * │  ← Broadcast  /topic/camera/{id}          CameraStatusNotification               │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * IMPORTANT — Frontend migration from old broadcast topics:
 *   OLD: stompClient.subscribe('/topic/session/{id}/offer',  handler)
 *   NEW: stompClient.subscribe('/user/queue/offer',          handler)
 *
 *   OLD: stompClient.subscribe('/topic/session/{id}/answer', handler)
 *   NEW: stompClient.subscribe('/user/queue/answer',         handler)
 *
 *   OLD: stompClient.subscribe('/topic/session/{id}/ice',    handler)
 *   NEW: stompClient.subscribe('/user/queue/ice',            handler)
 */
@Controller
public class LiveInterviewSignalingController {

    private final LiveInterviewService liveService;

    public LiveInterviewSignalingController(LiveInterviewService liveService) {
        this.liveService = liveService;
    }

    // =========================================================================
    // 1.  Join  – participant announces they are in the room
    // =========================================================================
    /**
     * /app/live/join
     *
     * Sent by both the recruiter and the candidate immediately after they
     * subscribe to the session topics.
     * FIX BUG-C1: Session becomes ACTIVE only when BOTH parties have joined.
     */
    @MessageMapping("/live/join")
    public void handleJoin(@Valid @Payload JoinSessionMessage msg, Principal principal) {
        requirePrincipal(principal);
        liveService.onParticipantJoin(msg, principal.getName());
    }

    // =========================================================================
    // 2.  WebRTC SDP Offer  – candidate → server → recruiter (unicast)
    // =========================================================================
    /**
     * /app/live/offer
     *
     * FIX BUG-C4: Service uses convertAndSendToUser() — only the recruiter
     * receives the offer on /user/queue/offer.
     * FIX BUG-M3: Only CANDIDATE may send an offer.
     */
    @MessageMapping("/live/offer")
    public void handleOffer(@Valid @Payload WebRtcOfferMessage msg, Principal principal) {
        requirePrincipal(principal);
        liveService.relayOffer(msg, principal.getName());
    }

    // =========================================================================
    // 3.  WebRTC SDP Answer  – recruiter → server → candidate (unicast)
    // =========================================================================
    /**
     * /app/live/answer
     *
     * FIX BUG-C4: Service uses convertAndSendToUser() — only the candidate
     * receives the answer on /user/queue/answer.
     * FIX BUG-M3: Only RECRUITER may send an answer.
     */
    @MessageMapping("/live/answer")
    public void handleAnswer(@Valid @Payload WebRtcAnswerMessage msg, Principal principal) {
        requirePrincipal(principal);
        liveService.relayAnswer(msg, principal.getName());
    }

    // =========================================================================
    // 4.  ICE Candidates  – either direction (unicast to the other party)
    // =========================================================================
    /**
     * /app/live/ice
     *
     * FIX BUG-C4: Service routes each ICE candidate to the OTHER participant
     * on /user/queue/ice — the sender does not receive it back.
     */
    @MessageMapping("/live/ice")
    public void handleIceCandidate(@Valid @Payload IceCandidateMessage msg, Principal principal) {
        requirePrincipal(principal);
        liveService.relayIceCandidate(msg, principal.getName());
    }

    // =========================================================================
    // 5.  Camera status  – FIX BUG-H1: either direction → broadcast to both
    // =========================================================================
    /**
     * /app/live/camera
     *
     * FIX BUG-H1: Sent by EITHER participant when their camera toggles.
     * The service validates the claimed role and persists the correct DB field.
     * Broadcast goes to /topic/camera/{sessionId} so both parties stay in sync.
     */
    @MessageMapping("/live/camera")
    public void handleCameraStatus(@Valid @Payload CameraStatusMessage msg, Principal principal) {
        requirePrincipal(principal);
        liveService.updateCameraStatus(msg, principal.getName());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void requirePrincipal(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException(
                    "Unauthenticated WebSocket message rejected. Include JWT as STOMP login header.");
        }
    }
}