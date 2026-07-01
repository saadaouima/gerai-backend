package com.gerai.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service de gestion de la présence en temps réel des utilisateurs du chat.
 * <p>
 * {@code @Service} : déclare ce bean comme service Spring géré par le conteneur IoC.
 * <br>
 * {@code @RequiredArgsConstructor} (Lombok) : génère l'injection du {@link SimpMessagingTemplate} par constructeur.
 * <br>
 * {@code @Slf4j} (Lombok) : injecte un logger SLF4J pour la traçabilité des transitions de présence.
 * <p>
 * Implémente une présence à deux couches :
 * <ul>
 *   <li><b>Couche WebSocket</b> ({@code wsOnlineIds}) : mis à jour instantanément
 *       lors de la connexion/déconnexion STOMP via {@link com.gerai.chat.config.WebSocketEventListener}.
 *       Reflète les utilisateurs ayant le chat ouvert dans leur navigateur.</li>
 *   <li><b>Couche Keycloak</b> ({@code sessionOnlineIds}) : rafraîchi toutes les 60 secondes
 *       via {@link #refreshFromKeycloak()} en seulement 2 appels API Keycloak.
 *       Reflète les utilisateurs connectés à la plateforme (même sans chat ouvert).</li>
 * </ul>
 * {@link #estConnecte(String)} retourne {@code true} si l'une ou l'autre couche signale l'utilisateur connecté.
 * <p>
 * L'injection de {@link KeycloakAdminService} est différée ({@code @Lazy}) pour briser le cycle de dépendance.
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    /** Template STOMP pour diffuser les changements de statut de présence sur {@code /topic/presence}. */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Service Keycloak Admin, injecté avec {@code @Lazy} pour briser le cycle :
     * {@code PresenceService(constructor)} → {@code KeycloakAdminService} → {@code PresenceService(@Lazy)}.
     */
    @Lazy
    @Autowired
    private KeycloakAdminService keycloakAdminService;

    /** Ensemble thread-safe des IDs Oracle (String) des utilisateurs avec une connexion WebSocket active. */
    private final Set<String> wsOnlineIds = ConcurrentHashMap.newKeySet();

    /**
     * Ensemble thread-safe des IDs Oracle (String) des utilisateurs avec une session Keycloak active.
     * Mis à jour atomiquement lors du rafraîchissement planifié pour éviter les états incohérents.
     */
    private final AtomicReference<Set<String>> sessionOnlineIds =
            new AtomicReference<>(ConcurrentHashMap.newKeySet());

    // ── Real-time: WebSocket connect / disconnect ──────────────────

    /**
     * Marque un utilisateur comme connecté (couche WebSocket) et diffuse son nouveau statut.
     * Appelé par {@link com.gerai.chat.config.WebSocketEventListener#handleWebSocketConnect}.
     *
     * @param employeeId l'identifiant Oracle (String) de l'employé qui vient de se connecter
     */
    public void utilisateurConnecte(String employeeId) {
        wsOnlineIds.add(employeeId);
        broadcast(employeeId, true);
        log.debug("[Presence] WS connected: {}", employeeId);
    }

    /**
     * Marque un utilisateur comme déconnecté (couche WebSocket).
     * Ne diffuse le statut hors-ligne que si l'utilisateur n'a également aucune session Keycloak active
     * (double vérification pour éviter un faux statut hors-ligne).
     * Appelé par {@link com.gerai.chat.config.WebSocketEventListener#handleWebSocketDisconnect}.
     *
     * @param employeeId l'identifiant Oracle (String) de l'employé qui vient de se déconnecter
     */
    public void utilisateurDeconnecte(String employeeId) {
        wsOnlineIds.remove(employeeId);
        // Only broadcast offline if they also have no active Keycloak session
        if (!sessionOnlineIds.get().contains(employeeId)) {
            broadcast(employeeId, false);
        }
        log.debug("[Presence] WS disconnected: {}", employeeId);
    }

    /**
     * Indique si un utilisateur est actuellement en ligne.
     * Retourne {@code true} si l'utilisateur est présent dans la couche WebSocket
     * <strong>ou</strong> dans la couche Keycloak (sessions actives).
     *
     * @param employeeId l'identifiant Oracle (String) de l'employé à vérifier
     * @return {@code true} si l'employé est en ligne selon au moins une des deux couches de présence
     */
    public boolean estConnecte(String employeeId) {
        return wsOnlineIds.contains(employeeId)
                || sessionOnlineIds.get().contains(employeeId);
    }

    // ── Batch Keycloak refresh (2 API calls, runs every 60 s) ──────

    /**
     * Synchronise périodiquement la couche Keycloak des sessions actives.
     * <p>
     * {@code @Scheduled} : déclenché par le moteur de tâches Spring activé via {@code @EnableScheduling}
     * dans {@link com.gerai.chat.ChatServiceApplication}. Intervalle configurable via
     * {@code presence.refresh-interval-ms} (défaut : 60 000 ms). Délai initial : 5 000 ms.
     * <p>
     * N'effectue que 2 appels API Keycloak par cycle :
     * <ol>
     *   <li>Résolution de l'UUID interne du client Keycloak.</li>
     *   <li>Récupération de toutes les sessions actives en une seule requête.</li>
     * </ol>
     * Diffuse les transitions de statut détectées (connexion/déconnexion de session)
     * uniquement pour les utilisateurs sans connexion WebSocket active (évite les doublons).
     */
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

    /**
     * Diffuse un changement de statut de présence à tous les clients connectés via WebSocket.
     * Publie sur la destination STOMP {@code /topic/presence} un objet JSON
     * {@code { "userId": "123", "connecte": true/false }}.
     *
     * @param employeeId l'identifiant Oracle (String) de l'employé dont le statut change
     * @param online     {@code true} si l'employé est maintenant en ligne, {@code false} s'il est hors ligne
     */
    private void broadcast(String employeeId, boolean online) {
        messagingTemplate.convertAndSend(
                "/topic/presence",
                Map.of("userId", employeeId, "connecte", online));
    }
}
