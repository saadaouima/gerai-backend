package com.gerai.notificationservice.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /* ==============================
       📤 Envoi à un utilisateur précis
       ============================== */
    public void sendToUser(String userId, Object payload) {

        if (userId == null || userId.isBlank()) {
            log.warn("[WebSocket] ⚠️ userId null, envoi ignoré.");
            return;
        }

        // Primary: user-queue channel — requires STOMP session principal to match userId
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                payload
        );

        // Fallback: per-employee topic — always delivered regardless of principal setup
        messagingTemplate.convertAndSend("/topic/employee." + userId, payload);

        log.info("[WebSocket] 📡 Notification envoyée à l'utilisateur {}", userId);
    }

    /* ==============================
       📤 Envoi par rôle (topic public)
       ============================== */
    public void sendToRole(String role, Object payload) {

        if (role == null || role.isBlank()) {
            log.warn("[WebSocket] ⚠️ role null, envoi ignoré.");
            return;
        }

        // Must match frontend: this.stomp.watch(`/topic/notifications.${role.toLowerCase()}`)
        messagingTemplate.convertAndSend(
                "/topic/notifications." + role.toLowerCase(),
                payload
        );

        log.info("[WebSocket] 📡 Notification envoyée au rôle {}", role);
    }
}