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
 * Intercepteur de canal STOMP chargé de l'authentification des connexions WebSocket.
 * <p>
 * {@code @Component} : déclare ce bean comme composant Spring détectable par le scan de composants.
 * <br>
 * {@code ChannelInterceptor} : interface Spring Messaging permettant d'intercepter les messages
 * transitant par un canal, ici le canal entrant STOMP.
 * <p>
 * Lors d'une trame {@code CONNECT} STOMP, cet intercepteur :
 * <ol>
 *   <li>Extrait le JWT depuis l'en-tête STOMP {@code token} ou {@code Authorization: Bearer ...}.</li>
 *   <li>Décode le payload Base64 du JWT pour en extraire :
 *     <ul>
 *       <li>{@code sub} → UUID Keycloak (keycloakId)</li>
 *       <li>{@code employee_id} → ID Oracle de l'employé (si absent, résolu depuis {@code EMPLOYEES.USER_ID})</li>
 *       <li>{@code name} / {@code preferred_username} → nom d'affichage</li>
 *     </ul>
 *   </li>
 *   <li>Injecte un {@link StompPrincipal} enrichi dans l'accesseur de la trame.</li>
 * </ol>
 * <p>
 * {@code getName()} du principal retourne l'ID Oracle (numérique) afin que le routing STOMP
 * {@code /user/{id}/queue/...} corresponde aux identifiants utilisés par
 * {@code ChatController#broadcastToConversation()}.
 *
 * @since 1.0
 */
@Slf4j
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    // @Lazy pour éviter la dépendance circulaire potentielle via WebSocketConfig
    @Lazy
    @Autowired
    private KeycloakAdminService keycloakAdminService;

    /**
     * Intercepte chaque message avant son envoi sur le canal.
     * Pour les trames STOMP {@code CONNECT}, extrait et valide le JWT
     * puis associe un {@link StompPrincipal} à la session WebSocket.
     *
     * @param message le message STOMP entrant
     * @param channel le canal de messagerie cible
     * @return le message (potentiellement enrichi avec le principal), jamais {@code null}
     */
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

    /**
     * Décode le JWT et extrait les informations d'identité nécessaires au routing STOMP.
     * <p>
     * Ordre de résolution de l'employee_id :
     * <ol>
     *   <li>Claim {@code employee_id} présent dans le JWT.</li>
     *   <li>Requête {@code EMPLOYEES.USER_ID} via {@link KeycloakAdminService}.</li>
     *   <li>Utilisation du UUID Keycloak en dernier recours (la connexion WS réussit
     *       mais l'envoi de messages échouera).</li>
     * </ol>
     *
     * @param token le jeton JWT brut (sans préfixe "Bearer ")
     * @return un enregistrement {@link TokenInfo} contenant keycloakId, employeeId et nom
     * @throws RuntimeException si le JWT est mal formé ou illisible
     */
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
     * Principal STOMP enrichi avec les données d'identité Oracle et Keycloak.
     * <p>
     * Implémente {@link java.security.Principal} pour s'intégrer dans le mécanisme
     * de sécurité Spring Messaging.
     * <p>
     * {@code getName()} retourne l'ID Oracle de l'employé (utilisé par STOMP pour
     * le routing {@code /user/{id}/queue/...} côté Angular et backend).
     * <br>
     * {@code getEmployeeId()} retourne le même ID Oracle — utilisé par {@link com.gerai.chat.service.ChatService}.
     * <br>
     * {@code getNom()} retourne le nom d'affichage extrait du JWT.
     */
    public static class StompPrincipal implements Principal {

        /** UUID Keycloak (claim {@code sub}) de l'utilisateur connecté. */
        private final String keycloakId;

        /** ID Oracle de l'employé (utilisé pour le routing STOMP et les opérations métier). */
        @Getter private final String employeeId;

        /** Nom d'affichage extrait du JWT (claim {@code name} ou {@code preferred_username}). */
        @Getter private final String nom;

        /**
         * Construit un principal STOMP enrichi.
         *
         * @param keycloakId UUID Keycloak (claim {@code sub})
         * @param employeeId ID Oracle de l'employé
         * @param nom        nom d'affichage de l'utilisateur
         */
        public StompPrincipal(String keycloakId, String employeeId, String nom) {
            this.keycloakId = keycloakId;
            this.employeeId = employeeId;
            this.nom        = nom;
        }

        /**
         * Retourne l'identifiant utilisé par STOMP pour le routing des messages personnels.
         * Correspond à l'ID Oracle de l'employé, ce qui permet l'alignement avec
         * {@code broadcastToConversation()} dans {@link com.gerai.chat.controller.ChatController}.
         *
         * @return l'ID Oracle de l'employé sous forme de chaîne
         */
        @Override
        public String getName() {
            return employeeId;
        }
    }

}