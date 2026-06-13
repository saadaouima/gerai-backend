package com.gerai.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.*;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder   jwtDecoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("*");
        // No SockJS — frontend uses RxStomp with native WebSocket (brokerURL)
    }

    /**
     * Reads the Bearer token from the STOMP CONNECT frame and sets the session
     * principal to the Oracle EMPLOYEE_ID so that convertAndSendToUser() can
     * route the message to the right WebSocket session.
     *
     * Resolution order:
     *  1. employee_id JWT claim (fast – requires a Keycloak custom mapper).
     *  2. DB lookup on EMPLOYEES.USER_ID = Keycloak sub (works without any
     *     Keycloak mapper because notification-service shares the same Oracle DB).
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        try {
                            Jwt jwt = jwtDecoder.decode(authHeader.substring(7));
                            String principal = resolveEmployeeId(jwt);
                            if (principal != null) {
                                accessor.setUser(() -> principal);
                                log.info("[WebSocket] STOMP CONNECT authenticated | principal={}", principal);
                            }
                        } catch (Exception e) {
                            log.warn("[WebSocket] JWT decode failed on CONNECT: {}", e.getMessage());
                        }
                    }
                }
                return message;
            }
        });
    }

    /**
     * Resolves the Oracle EMPLOYEE_ID string to use as the STOMP session principal.
     * 1. Reads the employee_id custom JWT claim (no DB hit, preferred).
     * 2. Falls back to querying EMPLOYEES.USER_ID = Keycloak sub.
     */
    private String resolveEmployeeId(Jwt jwt) {
        Object empIdClaim = jwt.getClaim("employee_id");
        if (empIdClaim != null && !empIdClaim.toString().isBlank()) {
            return empIdClaim.toString();
        }

        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) return null;

        try {
            Long oracleId = jdbcTemplate.queryForObject(
                    "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE USER_ID = ?",
                    Long.class, sub);
            if (oracleId != null) {
                log.info("[WebSocket] DB resolved employee_id={} for sub={}", oracleId, sub);
                return oracleId.toString();
            }
        } catch (Exception e) {
            log.warn("[WebSocket] DB lookup failed for sub={}: {}", sub, e.getMessage());
        }
        return null;
    }
}
