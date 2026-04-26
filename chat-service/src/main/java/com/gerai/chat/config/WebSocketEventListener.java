package com.gerai.chat.config;

import com.gerai.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final SimpMessageSendingOperations messagingTemplate;

    @EventListener
    public void handleWebSocketConnect(SessionConnectedEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        Principal user = accessor.getUser();

        if (user != null) {
            String userId = user.getName();
            presenceService.utilisateurConnecte(userId);
            log.info("Connecte : {}", userId);

            messagingTemplate.convertAndSend(
                    "/topic/presence",
                    presenceService.getUtilisateursEnLigne());
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        Principal user = accessor.getUser();

        if (user != null) {
            String userId = user.getName();
            presenceService.utilisateurDeconnecte(userId);
            log.info("Deconnecte : {}", userId);

            messagingTemplate.convertAndSend(
                    "/topic/presence",
                    presenceService.getUtilisateursEnLigne());
        }
    }
}