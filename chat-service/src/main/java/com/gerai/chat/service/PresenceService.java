package com.gerai.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Two-layer presence:
 *  - wsOnlineIds    : updated instantly on STOMP connect/disconnect
 *  - sessionOnlineIds: refreshed every 60 s from Keycloak active sessions (batch, 2 calls total)
 *
 * estConnecte() is true when either layer says the user is online.
 * This way, users who are logged in but haven't opened the chat page still appear online.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;

    // @Lazy breaks the cycle: KeycloakAdminService → PresenceService → KeycloakAdminService
    @Lazy
    private final KeycloakAdminService keycloakAdminService;

    /** Users with an active WebSocket connection (chat tab open). */
    private final Set<String> wsOnlineIds = ConcurrentHashMap.newKeySet();

    /** Users with an active Keycloak session (logged in anywhere). Refreshed on schedule. */
    private final AtomicReference<Set<String>> sessionOnlineIds =
            new AtomicReference<>(ConcurrentHashMap.newKeySet());

    // ── Real-time: WebSocket connect / disconnect ──────────────────

    public void utilisateurConnecte(String employeeId) {
        wsOnlineIds.add(employeeId);
        broadcast(employeeId, true);
        log.debug("[Presence] WS connected: {}", employeeId);
    }

    public void utilisateurDeconnecte(String employeeId) {
        wsOnlineIds.remove(employeeId);
        // Only broadcast offline if they also have no active Keycloak session
        if (!sessionOnlineIds.get().contains(employeeId)) {
            broadcast(employeeId, false);
        }
        log.debug("[Presence] WS disconnected: {}", employeeId);
    }

    public boolean estConnecte(String employeeId) {
        return wsOnlineIds.contains(employeeId)
                || sessionOnlineIds.get().contains(employeeId);
    }

    // ── Batch Keycloak refresh (2 API calls, runs every 60 s) ──────

    @Scheduled(fixedDelayString = "${presence.refresh-interval-ms:60000}",
               initialDelayString = "${presence.initial-delay-ms:5000}")
    public void refreshFromKeycloak() {
        try {
            Set<String> fresh = keycloakAdminService.getOnlineEmployeeIds();
            Set<String> previous = sessionOnlineIds.getAndSet(fresh);

            // Broadcast transitions detected by the Keycloak diff
            for (String id : fresh) {
                if (!previous.contains(id) && !wsOnlineIds.contains(id)) {
                    broadcast(id, true);   // newly online via Keycloak session
                }
            }
            for (String id : previous) {
                if (!fresh.contains(id) && !wsOnlineIds.contains(id)) {
                    broadcast(id, false);  // session expired, no WS either
                }
            }

            log.debug("[Presence] Keycloak sync: {} users online", fresh.size());
        } catch (Exception e) {
            log.warn("[Presence] Keycloak refresh failed, keeping previous state: {}", e.getMessage());
        }
    }

    // ── Helper ────────────────────────────────────────────────────

    private void broadcast(String employeeId, boolean online) {
        messagingTemplate.convertAndSend(
                "/topic/presence",
                Map.of("userId", employeeId, "connecte", online));
    }
}
