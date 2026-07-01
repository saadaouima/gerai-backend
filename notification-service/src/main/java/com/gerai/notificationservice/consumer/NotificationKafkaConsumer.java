package com.gerai.notificationservice.consumer;

import com.gerai.notificationservice.dto.NotificationDTO;
import com.gerai.notificationservice.entity.Notification;
import com.gerai.notificationservice.event.NotificationEvent;
import com.gerai.notificationservice.mapper.NotificationMapper;
import com.gerai.notificationservice.service.NotificationService;
import com.gerai.notificationservice.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Consommateur Kafka chargé de traiter les événements de notification
 * publiés sur le topic {@code notification-events}.
 * <p>
 * {@code @Component} : enregistre ce bean dans le contexte Spring pour
 * l'injection de dépendances.<br>
 * {@code @RequiredArgsConstructor} : génère un constructeur injectant
 * {@code NotificationService}, {@code WebSocketNotificationService} et
 * {@code NotificationMapper}.<br>
 * {@code @Slf4j} : fournit un logger Lombok pour tracer les messages reçus et
 * les erreurs de traitement.
 * </p>
 * <p>
 * Deux modes de traitement sont gérés :
 * <ul>
 *   <li><strong>Personnel</strong> : si {@code employeeId} est renseigné,
 *       la notification est persistée en base puis poussée via WebSocket
 *       à l'employé destinataire.</li>
 *   <li><strong>Broadcast</strong> : si {@code employeeId} est absent mais
 *       {@code role} est présent, la notification est persistée et diffusée
 *       à tous les utilisateurs du rôle via le topic WebSocket dédié.</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    /** Service métier de gestion des notifications (persistance, lecture, suppression). */
    private final NotificationService          notificationService;

    /** Service d'envoi de notifications en temps réel via STOMP WebSocket. */
    private final WebSocketNotificationService webSocketService;

    /** Mapper MapStruct pour convertir les entités et événements en DTOs. */
    private final NotificationMapper           notificationMapper;

    /**
     * Point d'entrée du consommateur Kafka : reçoit un {@link NotificationEvent}
     * depuis le topic {@code notification-events}, le persiste en base de données
     * et le pousse en temps réel via WebSocket vers l'utilisateur ou le rôle cible.
     * <p>
     * La factory {@code kafkaListenerContainerFactory} (définie dans {@code KafkaConfig})
     * gère les ré-essais automatiques en cas d'erreur.
     * </p>
     *
     * @param event     l'événement de notification désérialisé depuis Kafka
     * @param partition numéro de la partition Kafka source (pour la traçabilité)
     * @param offset    offset du message dans la partition (pour la traçabilité)
     */
    @KafkaListener(
            topics   = "${app.kafka.topic.notifications}",
            groupId  = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset) {

        log.info("[KafkaConsumer] Reçu | employeeId={} | type={} | source={} | partition={} | offset={}",
                event.getEmployeeId(), event.getType(), event.getSourceService(), partition, offset);

        // Role broadcast: persist to DB (survives refresh) then push via WebSocket
        if (event.getEmployeeId() == null) {
            if (event.getRole() != null && !event.getRole().isBlank()) {
                try {
                    Notification saved = notificationService.saveBroadcastNotification(event);
                    NotificationDTO roleDto = notificationMapper.toDTO(saved);
                    if (roleDto.getCreatedAt() == null) roleDto.setCreatedAt(LocalDateTime.now());
                    webSocketService.sendToRole(event.getRole(), roleDto);
                    log.info("[KafkaConsumer] Broadcast persisté (id={}) et envoyé rôle={}", saved.getNotificationId(), event.getRole());
                } catch (Exception e) {
                    log.error("[KafkaConsumer] Erreur broadcast rôle={} : {}", event.getRole(), e.getMessage(), e);
                }
            } else {
                log.warn("[KafkaConsumer] employeeId et role absents, message ignoré | offset={}", offset);
            }
            return;
        }

        try {
            Notification saved = notificationService.processNotificationEvent(event);
            if (saved != null && saved.getEmployeeId() != null) {
                NotificationDTO dto = notificationMapper.toDTO(saved);
                // createdAt is Oracle-managed (DEFAULT SYSTIMESTAMP); approximate it if null
                if (dto.getCreatedAt() == null) {
                    dto.setCreatedAt(LocalDateTime.now());
                }
                webSocketService.sendToUser(saved.getEmployeeId().toString(), dto);
            }
        } catch (Exception e) {
            log.error("[KafkaConsumer] Erreur traitement | employeeId={} | offset={} : {}",
                    event.getEmployeeId(), offset, e.getMessage(), e);
        }
    }

}
