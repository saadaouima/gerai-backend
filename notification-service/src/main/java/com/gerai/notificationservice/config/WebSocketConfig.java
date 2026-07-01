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

/**
 * Configuration du broker de messages STOMP sur WebSocket.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.<br>
 * {@code @EnableWebSocketMessageBroker} : active l'infrastructure de messagerie
 * WebSocket avec broker STOMP intégré.<br>
 * {@code @RequiredArgsConstructor} : injecte {@link JwtDecoder} et {@link JdbcTemplate}
 * via constructeur Lombok.
 * </p>
 * <p>
 * Destinations STOMP exposées :
 * <ul>
 *   <li>{@code /user/queue/notifications} — file personnelle par employé
 *       (routage via principal STOMP = {@code EMPLOYEE_ID}).</li>
 *   <li>{@code /topic/notifications.{role}} — broadcast par rôle (ADMIN, CHEF, EMPLOYE).</li>
 *   <li>{@code /topic/employee.{id}} — fallback par ID employé.</li>
 * </ul>
 * Le principal STOMP est résolu à partir du token JWT Bearer présent dans le frame
 * STOMP CONNECT, avec repli sur une requête SQL si le claim {@code employee_id} est absent.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Décodeur JWT Keycloak utilisé pour valider le token Bearer du frame STOMP CONNECT. */
    private final JwtDecoder   jwtDecoder;

    /** Template JDBC pour la résolution de l'EMPLOYEE_ID via la table EMPLOYEES en fallback. */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Configure le broker de messages STOMP simple en mémoire.
     * <p>
     * Destinations de souscription : {@code /topic} (broadcast) et {@code /queue} (file personnelle).<br>
     * Préfixe des destinations applicatives : {@code /app} (messages traités par {@code @MessageMapping}).<br>
     * Préfixe des destinations utilisateur : {@code /user} (routage vers un utilisateur spécifique).
     * </p>
     *
     * @param config le registre de configuration du broker de messages
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Enregistre l'endpoint WebSocket natif {@code /ws-notifications} accessible
     * par le frontend Angular via RxStomp (WebSocket natif, sans SockJS).
     * <p>
     * Toutes les origines sont autorisées ({@code *}) en développement ;
     * à restreindre en production.
     * </p>
     *
     * @param registry le registre des endpoints STOMP
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("*");
        // No SockJS — frontend uses RxStomp with native WebSocket (brokerURL)
    }

    /**
     * Intercepte le canal entrant STOMP pour authentifier les connexions WebSocket.
     * <p>
     * Lors de la réception d'un frame STOMP {@code CONNECT}, le token Bearer est extrait
     * de l'en-tête {@code Authorization}, décodé via Keycloak, puis l'identifiant Oracle
     * {@code EMPLOYEE_ID} est résolu et défini comme principal de la session STOMP.
     * Ce principal permet à {@code convertAndSendToUser()} de router les messages
     * vers le bon client WebSocket.
     * </p>
     * <p>
     * Ordre de résolution du principal :
     * <ol>
     *   <li>Claim JWT {@code employee_id} (chemin rapide, nécessite un mapper Keycloak custom).</li>
     *   <li>Requête SQL {@code EMPLOYEES.USER_ID = sub} (fallback sans mapper Keycloak).</li>
     * </ol>
     * </p>
     *
     * @param registration le registre de configuration du canal entrant
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
     * Résout l'identifiant Oracle {@code EMPLOYEE_ID} à utiliser comme principal
     * de la session STOMP à partir d'un token JWT Keycloak décodé.
     * <p>
     * Stratégie (dans l'ordre) :
     * <ol>
     *   <li>Lecture du claim JWT {@code employee_id} (sans accès base de données, préféré).</li>
     *   <li>Requête SQL {@code SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE USER_ID = :sub}
     *       en fallback si le claim est absent.</li>
     * </ol>
     * </p>
     *
     * @param jwt le token JWT Keycloak décodé et validé
     * @return l'identifiant Oracle de l'employé sous forme de chaîne,
     *         ou {@code null} si la résolution échoue
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
