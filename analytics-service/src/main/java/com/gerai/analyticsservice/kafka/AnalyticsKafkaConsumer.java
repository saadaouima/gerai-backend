package com.gerai.analyticsservice.kafka;

import com.gerai.analyticsservice.event.DemandeEvent;
import com.gerai.analyticsservice.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consommateur Kafka du analytics-service.
 *
 * Écoute le topic notification-events publié par demandes-service.
 * Délègue intégralement à StatsService.saveEvent() qui :
 *  1. Met à jour ABSENCE_STATS (congés validés ou demandes refusées)
 *  2. Invalide les caches Caffeine
 *
 * CORRECTION : on utilise event.getStatut() (champ ajouté dans DemandeEvent)
 * et non event.getType() qui représente le type d'event Kafka, pas le statut RH.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsKafkaConsumer {

    private final StatsService statsService;

    @KafkaListener(
            topics           = "${app.kafka.topic.notifications:notification-events}",
            groupId          = "${spring.kafka.consumer.group-id:analytics-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload DemandeEvent event) {

        if (!"DEMANDES-SERVICE".equals(event.getSourceService())) {
            log.debug("[Analytics-Kafka] Event ignoré (source={})", event.getSourceService());
            return;
        }

        log.info("[Analytics-Kafka] Event reçu | typeDemande={} | statut={} | ref={}",
                event.getTypeDemande(),
                event.getStatut(),
                event.getReferenceId());

        try {
            statsService.saveEvent(event);
        } catch (Exception e) {
            log.error("[Analytics-Kafka] Erreur traitement event ref={} : {}",
                    event.getReferenceId(), e.getMessage(), e);
            // DefaultErrorHandler gère les 3 retries (voir KafkaConfig)
        }
    }
}