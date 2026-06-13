package com.gerai.chat.config;

import com.gerai.chat.service.KeycloakAdminService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

/**
 * Intercepteur STOMP.
 *
 * Extrait du JWT :
 *  - sub         → keycloakId (UUID)
 *  - employee_id → ID Oracle ; si absent, résolu depuis EMPLOYEES.USER_ID via DB
 *  - name / preferred_username → nom d'affichage
 *
 * getName() retourne toujours l'ID Oracle (numérique) pour que le routing STOMP
 * /user/{id}/queue/... corresponde à ce que broadcastToConversation() envoie.
 */
@Slf4j
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    // @Lazy to avoid potential circular dependency through WebSocketConfig
    @Lazy
    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {

            String token = accessor.getFirstNativeHeader("token");
            if (token == null) {
                String auth = accessor.getFirstNativeHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7);
                }
            }

            if (token != null) {
                TokenInfo info = extractTokenInfo(token);
                accessor.setUser(new StompPrincipal(info.keycloakId(), info.employeeId(), info.nom()));
                log.debug("[Chat-WS] CONNECT keycloakId={} employeeId={}", info.keycloakId(), info.employeeId());
            } else {
                log.warn("[Chat-WS] CONNECT sans token STOMP");
            }
        }
        return message;
    }

    private TokenInfo extractTokenInfo(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(payload);

                String keycloakId = node.get("sub").asText();

                // Try employee_id claim first
                String employeeId = null;
                if (node.has("employee_id") && !node.get("employee_id").isNull()) {
                    String raw = node.get("employee_id").asText();
                    if (!raw.isBlank() && !raw.equals("null")) {
                        employeeId = raw;
                    }
                }

                // employee_id not in JWT — resolve from DB using the Keycloak sub
                if (employeeId == null) {
                    try {
                        Optional<Long> dbId = keycloakAdminService.findEmployeeIdByKeycloakId(keycloakId);
                        if (dbId.isPresent()) {
                            employeeId = dbId.get().toString();
                            log.info("[Chat-WS] Resolved employee_id={} from DB for sub={}", employeeId, keycloakId);
                        }
                    } catch (Exception ex) {
                        log.warn("[Chat-WS] DB lookup for employee_id failed (sub={}): {}", keycloakId, ex.getMessage());
                    }
                }

                // Last resort: use Keycloak UUID — message will be dropped by envoyerMessageWs
                // but at least the connection succeeds
                if (employeeId == null) {
                    employeeId = keycloakId;
                    log.warn("[Chat-WS] No employee_id found for sub={} — WS send will fail", keycloakId);
                }

                String nom = keycloakId;
                if (node.has("name") && !node.get("name").asText().isBlank()) {
                    nom = node.get("name").asText();
                } else if (node.has("preferred_username")
                        && !node.get("preferred_username").asText().isBlank()) {
                    nom = node.get("preferred_username").asText();
                }

                return new TokenInfo(keycloakId, employeeId, nom);
            }
        } catch (Exception e) {
            throw new RuntimeException("JWT STOMP invalide", e);
        }
        return new TokenInfo(token, token, token);
    }

    private record TokenInfo(String keycloakId, String employeeId, String nom) {}

    /**
     * Principal STOMP enrichi avec l'employee_id Oracle.
     *
     * getName() retourne le UUID Keycloak (sub) — utilisé par STOMP pour
     * le routing /user/{name}/queue/... côté Angular.
     *
     * getEmployeeId() retourne l'ID Oracle — utilisé par ChatService.
     */
    public static class StompPrincipal implements Principal {

        private final String keycloakId;
        @Getter private final String employeeId;
        @Getter private final String nom;

        public StompPrincipal(String keycloakId, String employeeId, String nom) {
            this.keycloakId = keycloakId;
            this.employeeId = employeeId;
            this.nom        = nom;
        }

        @Override
        public String getName() {
            // STOMP user routing keyed by Oracle employee_id (falls back to Keycloak UUID
            // for users without an employee_id JWT claim, e.g. the first admin login)
            return employeeId;
        }
    }

}