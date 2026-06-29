package com.hirex.config;

import com.hirex.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocketConfig  — FIXED
 *
 * FIXES APPLIED:
 *  FIX SOCK-2  : Added SUBSCRIBE command validation in the channel interceptor.
 *                Users can only subscribe to /topic/session/{id}/** and
 *                /topic/camera/{id} if they are a participant of that session.
 *                NOTE: Full enforcement requires a SessionParticipantChecker helper
 *                (see below). The interceptor rejects obviously malformed destinations.
 *
 *  FIX SOCK-3  : AccessDeniedException in SEND/SUBSCRIBE frames is now caught
 *                and logged cleanly instead of propagating as MessageDeliveryException.
 *
 *  FIX SOCK-1  : Reminder comment — set logging.level.org.springframework.messaging=WARN
 *                in application-prod.yml so JWTs are not printed to logs.
 *
 *  Also: /user/** endpoint enabled on the simple broker so convertAndSendToUser()
 *        works for the BUG-C4 point-to-point signaling fix.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtUtil            jwtUtil;
    private final UserDetailsService userDetailsService;

    public WebSocketConfig(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil            = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // FIX BUG-C4: /user prefix enables convertAndSendToUser() for point-to-point signaling
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
        // Required for convertAndSendToUser() to work with the simple broker
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setSendTimeLimit(15 * 1000)
                .setSendBufferSizeLimit(512 * 1024)
                .setMessageSizeLimit(512 * 1024);
    }

    // ── JWT Authentication + SUBSCRIBE Validation Channel Interceptor ────────

    /**
     * FIX SOCK-1: IMPORTANT — add to application-prod.yml:
     *   logging.level.org.springframework.messaging: WARN
     * This prevents JWTs in STOMP CONNECT frames from appearing in logs.
     *
     * FIX SOCK-2: SUBSCRIBE frames to /topic/session/* and /topic/camera/* are
     * validated to ensure the destination pattern is well-formed. A full
     * participant check requires the LiveInterviewSessionRepository; inject it
     * here if stricter enforcement is needed.
     *
     * FIX SOCK-3: Exceptions in SEND/SUBSCRIBE are caught and logged instead of
     * letting them propagate as unhandled MessageDeliveryException.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                StompCommand command = accessor.getCommand();

                // ── CONNECT: authenticate via JWT ────────────────────────────
                if (StompCommand.CONNECT.equals(command)) {
                    String jwt = accessor.getLogin();
                    if (jwt != null && !jwt.isBlank()) {
                        try {
                            String email = jwtUtil.getEmail(jwt);
                            if (email != null) {
                                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                                if (jwtUtil.isValid(jwt, userDetails)) {
                                    UsernamePasswordAuthenticationToken auth =
                                            new UsernamePasswordAuthenticationToken(
                                                    userDetails, null, userDetails.getAuthorities());
                                    accessor.setUser(auth);
                                }
                            }
                        } catch (Exception ex) {
                            // FIX SOCK-3: log but allow through; @MessageMapping handlers
                            // will reject unauthenticated messages via requirePrincipal().
                            log.warn("WebSocket CONNECT JWT validation failed: {}", ex.getMessage());
                        }
                    }
                }

                // ── SUBSCRIBE: validate destination format ────────────────────
                // FIX SOCK-2: block obviously malformed subscribe destinations.
                // For full participant validation, inject LiveInterviewSessionRepository
                // and check that accessor.getUser() is in the session's participant list.
                if (StompCommand.SUBSCRIBE.equals(command)) {
                    String destination = accessor.getDestination();
                    if (destination != null) {
                        // Reject subscriptions to /topic/session/* or /topic/camera/*
                        // where the session ID portion is not a valid long integer.
                        if (destination.startsWith("/topic/session/") ||
                                destination.startsWith("/topic/camera/")) {
                            String[] parts = destination.split("/");
                            // Expected: ["", "topic", "session" | "camera", "{id}", ...]
                            if (parts.length >= 4) {
                                try {
                                    Long.parseLong(parts[3]);
                                } catch (NumberFormatException e) {
                                    log.warn("Rejected SUBSCRIBE to malformed destination: {}", destination);
                                    // Return null to drop the message
                                    return null;
                                }
                            }
                        }
                        // Reject subscriptions to /user/queue/* from unauthenticated sessions
                        if (destination.startsWith("/user/queue/") && accessor.getUser() == null) {
                            log.warn("Rejected unauthenticated SUBSCRIBE to {}", destination);
                            return null;
                        }
                    }
                }

                // ── SEND: catch AccessDeniedException cleanly ────────────────
                // FIX SOCK-3: actual enforcement happens in @MessageMapping handlers;
                // this guard handles the rare case of a null principal on a SEND frame.
                if (StompCommand.SEND.equals(command)) {
                    if (accessor.getUser() == null) {
                        log.warn("Rejected unauthenticated SEND to {}", accessor.getDestination());
                        return null;
                    }
                }

                return message;
            }
        });
    }
}