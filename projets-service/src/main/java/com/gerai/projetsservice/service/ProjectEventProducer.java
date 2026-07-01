package com.gerai.projetsservice.service;

import com.gerai.projetsservice.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Producteur Kafka pour la publication d'événements de notification liés aux projets.
 * <p>
 * Publie des {@link NotificationEvent} sur le topic Kafka {@code notification-events}
 * (configurable via {@code app.kafka.topic.notifications}). Supporte à la fois les
 * notifications individuelles (par {@code employeeId}) et les broadcasts par rôle.
 * </p>
 * <p>
 * {@code @Service} : composant Spring géré par le conteneur IoC.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectEventProducer {

    /** Template Kafka pour la sérialisation et l'envoi des événements JSON. */
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    /** Nom du topic Kafka cible (configuré via {@code app.kafka.topic.notifications}). */
    @Value("${app.kafka.topic.notifications}")
    private String topic;

    /**
     * Publie un événement de notification sur le topic Kafka.
     * <p>
     * La clé de partition est l'identifiant de l'employé destinataire, ou
     * {@code role-<ROLE>} pour les broadcasts par rôle.
     * Les événements sans destinataire ({@code employeeId} et {@code role} tous deux absents)
     * sont ignorés avec un avertissement.
     * </p>
     *
     * @param event l'événement de notification à publier
     */
    public void emit(NotificationEvent event) {
        // Allow role-broadcast events (employeeId is null, role is set)
        if (event.getEmployeeId() == null && (event.getRole() == null || event.getRole().isBlank())) {
            log.warn("[ProjectEventProducer] employeeId et role absents, notification ignorée");
            return;
        }
        String key = event.getEmployeeId() != null
                ? String.valueOf(event.getEmployeeId())
                : "role-" + event.getRole();
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("[ProjectEventProducer] Échec envoi Kafka | dest={} role={} : {}",
                                event.getEmployeeId(), event.getRole(), ex.getMessage());
                    } else {
                        SendResult<String, NotificationEvent> sr = result;
                        log.info("[ProjectEventProducer] Notification publiée | dest={} role={} | type={} | partition={} | offset={}",
                                event.getEmployeeId(), event.getRole(), event.getType(),
                                sr.getRecordMetadata().partition(),
                                sr.getRecordMetadata().offset());
                    }
                });
    }
}
