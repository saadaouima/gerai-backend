package com.gerai.notificationservice.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service d'envoi de notifications en temps réel via le broker STOMP WebSocket.
 * <p>
 * {@code @Service} : enregistre ce bean comme composant de la couche service Spring.<br>
 * {@code @RequiredArgsConstructor} : injecte {@link SimpMessagingTemplate} par constructeur Lombok.<br>
 * {@code @Slf4j} : fournit un logger Lombok pour tracer les envois WebSocket.
 * </p>
 * <p>
 * Deux modes d'envoi sont pris en charge :
 * <ul>
 *   <li><strong>Utilisateur précis</strong> : utilise {@code convertAndSendToUser()}
 *       (destination {@code /user/queue/notifications}) avec un fallback sur le topic
 *       {@code /topic/employee.{id}} pour les sessions sans principal STOMP configuré.</li>
 *   <li><strong>Broadcast par rôle</strong> : publie sur le topic
 *       {@code /topic/notifications.{role}} souscrit par tous les clients du rôle.</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    /** Template de messagerie STOMP utilisé pour router les messages vers les sessions WebSocket. */
    private final SimpMessagingTemplate messagingTemplate;

    /* ==============================
       Envoi à un utilisateur précis
       ============================== */

    /**
     * Envoie une notification en temps réel à un employé identifié par son ID Oracle.
     * <p>
     * Stratégie double :
     * <ol>
     *   <li>Canal principal : {@code /user/{userId}/queue/notifications} — nécessite que le
     *       principal STOMP de la session corresponde à {@code userId}.</li>
     *   <li>Fallback : {@code /topic/employee.{userId}} — toujours livré, quelle que soit
     *       la configuration du principal STOMP.</li>
     * </ol>
     * L'envoi est ignoré si {@code userId} est null ou vide.
     * </p>
     *
     * @param userId  l'identifiant Oracle de l'employé destinataire (doit correspondre
     *                au principal STOMP établi lors du CONNECT)
     * @param payload l'objet à sérialiser en JSON et envoyer (généralement un {@code NotificationDTO})
     */
    public void sendToUser(String userId, Object payload) {

        if (userId == null || userId.isBlank()) {
            log.warn("[WebSocket] userId null, envoi ignoré.");
            return;
        }

        // Canal principal : file personnelle par principal STOMP
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                payload
        );

        // Fallback : topic par identifiant d'employé (indépendant du principal)
        messagingTemplate.convertAndSend("/topic/employee." + userId, payload);

        log.info("[WebSocket] Notification envoyée à l'utilisateur {}", userId);
    }

    /* ==============================
       Envoi par rôle (topic public)
       ============================== */

    /**
     * Diffuse une notification en temps réel à tous les utilisateurs connectés du rôle spécifié.
     * <p>
     * Publie sur le topic {@code /topic/notifications.{role}} (en minuscules) souscrit par
     * le frontend Angular via {@code this.stomp.watch(`/topic/notifications.${role.toLowerCase()}`)}.
     * L'envoi est ignoré si {@code role} est null ou vide.
     * </p>
     *
     * @param role    le rôle destinataire en majuscules (ADMIN, CHEF ou EMPLOYE) ;
     *                converti automatiquement en minuscules pour le topic
     * @param payload l'objet à sérialiser en JSON et diffuser (généralement un {@code NotificationDTO})
     */
    public void sendToRole(String role, Object payload) {

        if (role == null || role.isBlank()) {
            log.warn("[WebSocket] role null, envoi ignoré.");
            return;
        }

        // Le frontend souscrit à /topic/notifications.{role.toLowerCase()}
        messagingTemplate.convertAndSend(
                "/topic/notifications." + role.toLowerCase(),
                payload
        );

        log.info("[WebSocket] Notification envoyée au rôle {}", role);
    }
}