package com.gerai_backend.gerai.controllers;

import com.gerai_backend.gerai.repositories.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST exposant la liste des utilisateurs Keycloak à partir des données Oracle.
 * Fournit un proxy léger vers la liste des employés formatée comme des utilisateurs Keycloak.
 *
 * <p>@RestController : sérialise automatiquement toutes les réponses en JSON.</p>
 * <p>Route : {@code GET /api/keycloak/users} — proxifiée depuis Angular
 * via la règle {@code /api/keycloak → http://localhost:8081}.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/keycloak")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class KeycloakUserController {

    private final EmployeeRepository employeeRepository;

    /**
     * Retourne la liste de tous les employés ayant un compte Keycloak,
     * au format compatible avec la représentation utilisateur Keycloak
     * ({@code id}, {@code username}, {@code firstName}, {@code lastName}, {@code email}).
     *
     * @return une réponse HTTP 200 avec la liste des utilisateurs Keycloak
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        List<Map<String, Object>> users = employeeRepository.findAll().stream()
                .filter(e -> e.getKeycloakUserId() != null)
                .map(e -> {
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("id",        e.getKeycloakUserId());
                    u.put("username",  e.getEmail() != null ? e.getEmail() : "");
                    u.put("firstName", e.getFirstName() != null ? e.getFirstName() : "");
                    u.put("lastName",  e.getLastName()  != null ? e.getLastName()  : "");
                    u.put("email",     e.getEmail()     != null ? e.getEmail()     : "");
                    return u;
                })
                .toList();
        return ResponseEntity.ok(users);
    }
}
