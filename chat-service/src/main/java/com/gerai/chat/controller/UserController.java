package com.gerai.chat.controller;

import com.gerai.chat.dto.UserDTO;
import com.gerai.chat.service.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * Contrôleur REST exposant la liste des utilisateurs (contacts) du système chat.
 * <p>
 * {@code @RestController} : combine {@code @Controller} et {@code @ResponseBody},
 * toutes les méthodes retournent directement du JSON.
 * <br>
 * {@code @RequestMapping("/api/chat")} : préfixe de base partagé avec {@link ChatController}.
 * <br>
 * {@code @RequiredArgsConstructor} (Lombok) : génère l'injection du {@link KeycloakAdminService} par constructeur.
 * <p>
 * Cet endpoint est utilisé par le frontend Angular pour afficher la liste de contacts
 * lors de l'initiation d'une nouvelle conversation.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class UserController {

    /** Service d'accès à Keycloak et à la table EMPLOYEES pour la liste des contacts. */
    private final KeycloakAdminService keycloakAdminService;

    /**
     * Retourne la liste de tous les employés actifs avec leur statut de présence en ligne.
     * <p>
     * La source primaire est la table Oracle {@code EMPLOYEES}. Keycloak est interrogé
     * en complément pour résoudre les identifiants manquants.
     *
     * @return {@code 200 OK} avec la liste des {@link UserDTO} (employés actifs)
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getUsers(Principal principal) {
        Long excludeId = extractEmployeeId(principal);
        return ResponseEntity.ok(keycloakAdminService.getAllUsers(excludeId));
    }

    private Long extractEmployeeId(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken jwtToken)) return null;
        Jwt jwt = jwtToken.getToken();

        Object val = jwt.getClaim("employee_id");
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }

        String sub = jwt.getSubject();
        if (sub != null) {
            Optional<Long> byKcId = keycloakAdminService.findEmployeeIdByKeycloakId(sub);
            if (byKcId.isPresent()) return byKcId.get();
        }

        String email = jwt.getClaim("email");
        if (email != null) {
            Optional<Long> byEmail = keycloakAdminService.findEmployeeIdByEmail(email);
            if (byEmail.isPresent()) return byEmail.get();
        }

        return null;
    }
}