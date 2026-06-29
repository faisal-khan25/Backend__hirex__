package com.hirex.listener;

import com.hirex.repository.LiveInterviewSessionRepository;
import com.hirex.service.LiveInterviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocketEventListener  — NEW (FIX BUG-C2)
 *
 * BUG-C2 ROOT CAUSE:
 *   LiveInterviewService.onCandidateDisconnect() existed but there was no
 *   @EventListener wired to Spring's SessionDisconnectEvent. When a participant
 *   closed their browser or lost network, the live session stayed ACTIVE forever
 *   and the other participant received no notification.
 *
 * FIX:
 *   This component listens to STOMP connect/disconnect lifecycle events.
 *   On connect, it maps the STOMP sessionId → (sessionToken, userEmail) so
 *   that on disconnect we know which live session to close and for which user.
 *
 * How the sessionToken is stored:
 *   The frontend must include the live session token as a custom STOMP CONNECT
 *   header named "live-session-token":
 *
 *     const client = new Client({
 *       connectHeaders: {
 *         login:              localStorage.getItem('hirex_token'),
 *         'live-session-token': sessionToken          // <-- add this
 *       }
 *     });
 *
 *   This is read in handleConnect() below and stored in stompSessionRegistry.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    /**
     * Maps STOMP sessionId → SessionInfo (liveSessionToken + userEmail).
     * Populated on CONNECT, removed on DISCONNECT.
     */
    private final ConcurrentHashMap<String, SessionInfo> stompSessionRegistry =
            new ConcurrentHashMap<>();

    private final LiveInterviewService               liveService;
    private final LiveInterviewSessionRepository     liveRepo;

    public WebSocketEventListener(LiveInterviewService liveService,
                                  LiveInterviewSessionRepository liveRepo) {
        this.liveService = liveService;
        this.liveRepo    = liveRepo;
    }

    // =========================================================================
    // CONNECT — register the STOMP session
    // =========================================================================

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String stompSessionId = accessor.getSessionId();

        // Read the live-session-token header sent by the frontend
        String liveSessionToken = accessor.getFirstNativeHeader("live-session-token");

        // The Principal is set by the JWT channel interceptor in WebSocketConfig
        String userEmail = (accessor.getUser() != null)
                ? accessor.getUser().getName()
                : null;

        if (stompSessionId != null && liveSessionToken != null && userEmail != null) {
            stompSessionRegistry.put(stompSessionId, new SessionInfo(liveSessionToken, userEmail));
            log.debug("STOMP CONNECT registered: stompSession={} token={} user={}",
                    stompSessionId, liveSessionToken, userEmail);
        }
    }

    // =========================================================================
    // DISCONNECT — notify the service so it can transition session state
    // =========================================================================

    /**
     * FIX BUG-C2: This method is the missing link that wires
     * LiveInterviewService.onParticipantDisconnect() to real WebSocket events.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor  = StompHeaderAccessor.wrap(event.getMessage());
        String stompSessionId         = accessor.getSessionId();

        SessionInfo info = stompSessionRegistry.remove(stompSessionId);
        if (info == null) {
            // Not a live-interview session — ignore
            return;
        }

        log.info("STOMP DISCONNECT: user={} token={}", info.userEmail, info.liveSessionToken);

        try {
            liveService.onParticipantDisconnect(info.liveSessionToken, info.userEmail);
        } catch (Exception ex) {
            // Log and swallow — we must not crash the WebSocket event thread
            log.error("Error handling participant disconnect for token {}: {}",
                    info.liveSessionToken, ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // Inner record
    // =========================================================================

    private static final class SessionInfo {
        final String liveSessionToken;
        final String userEmail;

        SessionInfo(String liveSessionToken, String userEmail) {
            this.liveSessionToken = liveSessionToken;
            this.userEmail        = userEmail;
        }
    }
}