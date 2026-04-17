package com.gerai.notificationservice.kafka;

import com.gerai.notificationservice.entity.Notification;
import com.gerai.notificationservice.event.NotificationEvent;
import com.gerai.notificationservice.service.NotificationService;
import com.gerai.notificationservice.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationService          notificationService;
    private final WebSocketNotificationService webSocketService;

    @KafkaListener(
            topics           = "${app.kafka.topic.notifications:notification-events}",
            groupId          = "${spring.kafka.consumer.group-id:notification-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload NotificationEvent event) {

        log.info("[Kafka] Event reçu | Source: {} | EmployeeId: {} | Type: {}",
                event.getSourceService(),
                event.getEmployeeId(),
                event.getType());

        try {
            if (event.getEmployeeId() == null) {
                log.warn("[Kafka] Notification ignorée : employeeId absent");
                return;
            }

            // 1. Sauvegarde DB + envoi email
            Notification saved = notificationService.processNotificationEvent(event);

            if (saved == null) {
                log.warn("[Kafka] processNotificationEvent a retourné null");
                return;
            }

            // 2. Push WebSocket vers l'employé
            // L'employeeId Oracle est converti en String pour STOMP.
            // Angular s'abonne à /user/{employeeId}/queue/notifications
            if (saved.getEmployeeId() != null) {
                webSocketService.sendToUser(
                        saved.getEmployeeId().toString(),
                        saved
                );
            }

            log.info("[Kafka] Notification traitée | ID: {} | emp: {}",
                    saved.getNotificationId(), saved.getEmployeeId());

        } catch (Exception e) {
            log.error("[Kafka] Erreur consumer : {}", e.getMessage(), e);
            throw e;
        }
    }
}