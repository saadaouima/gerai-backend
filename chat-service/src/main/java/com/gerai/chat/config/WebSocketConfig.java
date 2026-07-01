package com.gerai.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.*;

/**
 * Configuration du broker de messages WebSocket STOMP pour le microservice chat-service.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * <br>
 * {@code @EnableWebSocketMessageBroker} : active le support du protocole STOMP sur WebSocket,
 * permettant la messagerie bidirectionnelle orientée messages entre le serveur et les clients Angular.
 * <br>
 * {@code @RequiredArgsConstructor} (Lombok) : génère un constructeur injectant {@link WebSocketAuthChannelInterceptor}.
 * <p>
 * Topologie des destinations STOMP configurées :
 * <ul>
 *   <li>{@code /topic/conversation/{id}} — broadcast à tous les participants d'une conversation.</li>
 *   <li>{@code /topic/presence} — diffusion des changements de statut en ligne.</li>
 *   <li>{@code /user/{employeeId}/queue/messages} — messages personnels (hors fil actif).</li>
 *   <li>{@code /user/{employeeId}/queue/typing} — indicateur de frappe en cours.</li>
 *   <li>{@code /app/chat.envoyer} — destination application pour l'envoi de messages.</li>
 *   <li>{@code /app/chat.typing} — destination application pour les indicateurs de frappe.</li>
 * </ul>
 * Point d'entrée WebSocket : {@code /ws} avec fallback SockJS.
 *
 * @since 1.0
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Intercepteur d'authentification STOMP injecté pour valider le JWT à la connexion. */
    private final WebSocketAuthChannelInterceptor authInterceptor;

    /**
     * Configure le broker de messages simple en mémoire.
     * <ul>
     *   <li>Préfixes de destination du broker : {@code /topic} et {@code /queue}.</li>
     *   <li>Préfixe des destinations applicatives (contrôleurs {@code @MessageMapping}) : {@code /app}.</li>
     *   <li>Préfixe des destinations utilisateur (messages personnels) : {@code /user}.</li>
     * </ul>
     *
     * @param registry le registre du broker de messages à configurer
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Enregistre le point d'entrée STOMP WebSocket.
     * L'endpoint {@code /ws} est exposé avec un fallback SockJS pour les navigateurs
     * ne supportant pas les WebSockets natifs. Toutes les origines sont autorisées
     * (le contrôle d'accès fin est assuré par JWT via {@link WebSocketAuthChannelInterceptor}).
     *
     * @param registry le registre des endpoints STOMP à configurer
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Enregistre l'intercepteur d'authentification JWT sur le canal entrant STOMP.
     * Cet intercepteur est exécuté avant le traitement de chaque trame STOMP
     * et valide le JWT lors de la connexion initiale ({@code CONNECT}).
     *
     * @param registration la configuration d'enregistrement du canal entrant
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors((ChannelInterceptor) authInterceptor);
    }
}