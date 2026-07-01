package com.gerai.chat.config;

import com.gerai.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Écouteur d'événements de cycle de vie des sessions WebSocket STOMP.
 * <p>
 * {@code @Component} : déclare ce bean comme composant Spring géré automatiquement.
 * <br>
 * {@code @RequiredArgsConstructor} (Lombok) : génère l'injection du {@link PresenceService} par constructeur.
 * <p>
 * Cette classe réagit aux événements Spring WebSocket pour maintenir à jour
 * le statut de présence en temps réel des utilisateurs connectés via WebSocket.
 * Elle délègue la logique de présence à {@link PresenceService}.
 *
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    /** Service de gestion de la présence des utilisateurs. */
    private final PresenceService presenceService;

    /**
     * Gère l'événement de connexion WebSocket STOMP.
     * Marque l'utilisateur identifié par son {@code employeeId} Oracle comme connecté
     * et diffuse son nouveau statut via {@code /topic/presence}.
     *
     * @param event l'événement Spring déclenché lors d'une connexion STOMP réussie
     */
    @EventListener
    public void handleWebSocketConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user != null) {
            presenceService.utilisateurConnecte(user.getName());
            log.info("Connecte : {}", user.getName());
        }
    }

    /**
     * Gère l'événement de déconnexion WebSocket STOMP.
     * Marque l'utilisateur comme hors ligne si et seulement s'il n'a plus
     * de session Keycloak active (double couche de présence).
     *
     * @param event l'événement Spring déclenché lors d'une déconnexion STOMP
     */
    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user != null) {
            presenceService.utilisateurDeconnecte(user.getName());
            log.info("Deconnecte : {}", user.getName());
        }
    }
}