package com.gerai.chat.config;

import lombok.Getter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Intercepteur STOMP adapté à la nouvelle architecture.
 *
 * Extrait du JWT :
 *  - sub       → id Keycloak (pour le routing STOMP /user/{id}/...)
 *  - employee_id → ID Oracle (pour les opérations métier ChatService)
 *  - name / preferred_username → nom d'affichage
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

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
            } else {
                System.out.println("[Chat-WS] Aucun token STOMP");
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

                // sub = UUID Keycloak (pour le routing STOMP)
                String keycloakId = node.get("sub").asText();

                // employee_id Oracle (pour le métier)
                String employeeId = keycloakId; // fallback
                if (node.has("employee_id") && !node.get("employee_id").isNull()) {
                    employeeId = node.get("employee_id").asText();
                }

                // Nom d'affichage
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
            return keycloakId; // STOMP routing par UUID Keycloak
        }
    }

}