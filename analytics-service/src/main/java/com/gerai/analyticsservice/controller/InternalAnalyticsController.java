package com.gerai.analyticsservice.controller;

import com.gerai.analyticsservice.event.DemandeEvent;
import com.gerai.analyticsservice.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur interne pour la réception des événements de demandes RH.
 * Exposé sur {@code /internal/events}, accessible sans authentification
 * (communication inter-services depuis le demandes-service).
 *
 * {@code @RestController} : combine {@code @Controller} et {@code @ResponseBody},
 * les réponses sont sérialisées directement en JSON.
 * {@code @Slf4j} : injecte un logger SLF4J pour la traçabilité des erreurs.
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class InternalAnalyticsController {

    private final StatsService statsService;

    /**
     * Reçoit un événement Kafka relayé par le demandes-service et met à jour
     * les statistiques d'absence en base (table ABSENCE_STATS).
     * Les événements dont la source n'est pas "DEMANDES-SERVICE" sont ignorés silencieusement.
     *
     * @param event l'événement de demande RH reçu en corps de requête JSON
     * @return HTTP 200 dans tous les cas (même en cas d'erreur de traitement)
     */
    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody DemandeEvent event) {
        if (!"DEMANDES-SERVICE".equals(event.getSourceService())) {
            return ResponseEntity.ok().build();
        }
        try {
            statsService.saveEvent(event);
        } catch (Exception e) {
            log.error("[Internal-Analytics] Erreur traitement event ref={} : {}",
                    event.getReferenceId(), e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
