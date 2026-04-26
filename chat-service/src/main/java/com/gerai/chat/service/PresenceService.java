package com.gerai.chat.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;

    @Getter
    private final Set<String> utilisateursEnLigne =
            ConcurrentHashMap.newKeySet();

    public void utilisateurConnecte(String userId) {
        utilisateursEnLigne.add(userId);
        // ✅ Broadcaster à tous les clients connectés
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "userId",   userId,
                "connecte", true
        ));
    }

    public void utilisateurDeconnecte(String userId) {
        utilisateursEnLigne.remove(userId);
        // ✅ Broadcaster à tous les clients connectés
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "userId",   userId,
                "connecte", false
        ));
    }

    public boolean estConnecte(String userId) {
        return utilisateursEnLigne.contains(userId);
    }
}