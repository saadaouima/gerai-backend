package com.gerai.chat.controller;

import com.gerai.chat.dto.ai.AiChatRequest;
import com.gerai.chat.dto.ai.AiChatResponse;
import com.gerai.chat.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST exposant l'interface du chatbot IA RH (SYNAPSE Assistant).
 * <p>
 * {@code @RestController} : combine {@code @Controller} et {@code @ResponseBody},
 * toutes les méthodes retournent directement du JSON.
 * <br>
 * {@code @RequestMapping("/api/chat/ai")} : préfixe de base de toutes les routes de ce contrôleur.
 * <br>
 * {@code @RequiredArgsConstructor} (Lombok) : génère l'injection du {@link AiChatService} par constructeur.
 * <p>
 * Le chatbot utilise le modèle Groq {@code llama-3.3-70b-versatile} et peut exécuter
 * des outils (tool use) pour consulter le solde de congés ou soumettre des demandes RH.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/chat/ai")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    /** Service de chatbot IA délégant à l'API Groq. */
    private final AiChatService aiChatService;

    /**
     * Traite un message utilisateur destiné au chatbot IA RH.
     * <p>
     * Extrait le nom de l'employé depuis le JWT pour personnaliser les réponses du chatbot.
     * Délègue le traitement à {@link AiChatService#chat(String, java.util.List, String, String)}.
     *
     * @param jwt     le jeton JWT de l'employé connecté (injecté par Spring Security)
     * @param request le corps de la requête contenant le message et l'historique de conversation
     * @return {@code 200 OK} avec la réponse textuelle du chatbot encapsulée dans {@link AiChatResponse}
     */
    @PostMapping("/message")
    public ResponseEntity<AiChatResponse> chat(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AiChatRequest request) {

        String  token      = jwt.getTokenValue();
        String  nom        = resolveName(jwt);
        boolean isEmploye  = isEmploye(jwt);

        log.info("[AI Chat] Message reçu de '{}' (employé={})", nom, isEmploye);

        AiChatResponse response = aiChatService.chat(
                request.getMessage(),
                request.getHistory(),
                nom,
                token,
                isEmploye
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Résout le nom d'affichage de l'employé depuis les claims du JWT.
     * <p>
     * Ordre de priorité :
     * <ol>
     *   <li>Claim {@code name} (nom complet).</li>
     *   <li>Concaténation de {@code given_name} et {@code family_name}.</li>
     *   <li>Claim {@code preferred_username} en dernier recours.</li>
     * </ol>
     *
     * @param jwt le jeton JWT Keycloak de l'utilisateur connecté
     * @return le nom d'affichage résolu, jamais {@code null}
     */
    @SuppressWarnings("unchecked")
    private boolean isEmploye(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return true;
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles == null) return true;
            return roles.stream().map(String::toLowerCase)
                        .noneMatch(r -> r.equals("admin") || r.equals("admin_rh")
                                     || r.equals("chef") || r.equals("comite")
                                     || r.equals("directeur_general"));
        } catch (Exception e) {
            return true;
        }
    }

    private String resolveName(Jwt jwt) {
        if (jwt.hasClaim("name") && jwt.getClaimAsString("name") != null
                && !jwt.getClaimAsString("name").isBlank()) {
            return jwt.getClaimAsString("name");
        }
        if (jwt.hasClaim("given_name")) {
            String given  = jwt.getClaimAsString("given_name");
            String family = jwt.getClaimAsString("family_name");
            if (given != null && !given.isBlank()) {
                return family != null ? given + " " + family : given;
            }
        }
        return jwt.getClaimAsString("preferred_username");
    }
}
