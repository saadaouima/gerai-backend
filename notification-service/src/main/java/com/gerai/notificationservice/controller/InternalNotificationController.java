package com.gerai.notificationservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur stub pour l'endpoint HTTP interne de notifications (déprécié).
 * <p>
 * {@code @RestController} : indique que cette classe est un contrôleur REST dont
 * les méthodes retournent directement des objets sérialisés en JSON.<br>
 * {@code @RequestMapping("/internal/notifications")} : préfixe d'URL réservé
 * aux appels inter-services internes (non soumis à authentification JWT).
 * </p>
 * <p>
 * Ce contrôleur est conservé en stub de transition pour que les producteurs
 * encore en migration reçoivent un HTTP 410 Gone explicite plutôt qu'un 404,
 * signalant clairement que l'endpoint HTTP est remplacé par Kafka.
 * À supprimer une fois que tous les producteurs utilisent le topic Kafka.
 * </p>
 *
 * @since 1.0
 * @deprecated Remplacé par le topic Kafka {@code notification-events}.
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    /**
     * Retourne systématiquement HTTP 410 Gone pour signaler aux producteurs
     * que cet endpoint HTTP est obsolète et doit être remplacé par Kafka.
     *
     * @return une réponse HTTP 410 Gone sans corps
     * @deprecated Utiliser le topic Kafka {@code notification-events} à la place.
     */
    @PostMapping
    public ResponseEntity<Void> receive() {
        log.warn("[InternalNotificationController] HTTP endpoint called — all producers should now use Kafka.");
        return ResponseEntity.status(410).build(); // 410 Gone
    }
}
