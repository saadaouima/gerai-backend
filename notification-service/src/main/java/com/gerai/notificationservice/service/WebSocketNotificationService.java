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

        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                payload
        );

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

        messagingTemplate.convertAndSend(
                "/topic/role/" + role.toUpperCase(),
                payload
        );

        log.info("[WebSocket] 📡 Notification envoyée au rôle {}", role);
    }
}