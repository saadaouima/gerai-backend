package com.gerai.projetsservice.service;

import com.gerai.projetsservice.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Producteur Kafka du projet-service.
 *
 * Publie sur le topic "notification-events" — le même topic
 * qu'écoutent notification-service et analytics-service.
 * Pas de topic séparé "project-notifications" : un seul bus d'événements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Topic unique pour toutes les notifications de l'application.
     * Valeur par défaut si la variable d'env KAFKA_NOTIF_TOPIC n'est pas définie.
     */
    @Value("${app.kafka.topic.notifications:notification-events}")
    private String notificationTopic;

    /**
     * Envoie une notification à un destinataire unique.
     * La clé Kafka est l'employeeId pour que les messages d'un même
     * destinataire arrivent toujours dans la même partition (ordre garanti).
     */
    public void emit(NotificationEvent event) {
        if (event.getEmployeeId() == null) {
            log.warn("[ProjectEventProducer] employeeId null, notification ignorée");
            return;
        }

        try {
            kafkaTemplate.send(
                    notificationTopic,
                    event.getEmployeeId().toString(), // clé de partition
                    event
            );
            log.info("[ProjectEventProducer] Notification envoyée | dest={} | type={} | ref={}",
                    event.getEmployeeId(), event.getType(), event.getReferenceId());
        } catch (Exception e) {
            // Ne pas bloquer la transaction métier si Kafka est indisponible
            log.error("[ProjectEventProducer] Échec envoi Kafka | dest={} : {}",
                    event.getEmployeeId(), e.getMessage());
        }
    }
}