package com.gerai_backend.gerai.controllers;

import com.gerai_backend.gerai.repositories.EmployeeRepository;
import com.gerai_backend.gerai.services.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST d'administration Keycloak permettant de gérer les accès
 * (rôles, groupes, activation, réinitialisation de mot de passe) par employé.
 *
 * <p>@RestController : sérialise toutes les réponses en JSON.</p>
 * <p>Route de base : {@code /admin/keycloak/employes/{id}/...} — proxifiée depuis Angular
 * via la règle {@code /api/admin/keycloak} vers le port 8081 ({@code employe-service}).</p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/admin/keycloak/employes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class KeycloakAdminController {

    private final EmployeeRepository employeeRepository;
    private final KeycloakUserService keycloakService;

    /**
     * Retourne le snapshot d'accès Keycloak d'un employé : rôles, groupes,
     * statut du compte et vérification email.
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @return une réponse HTTP 200 avec la map d'accès Keycloak,
     *         404 si l'employé n'a pas de compte Keycloak,
     *         ou 502 si Keycloak est inaccessible
     */
    @GetMapping("/{id}/access")
    public ResponseEntity<?> getAccess(@PathVariable Long id) {
        return employeeRepository.findById(id)
                .filter(e -> e.getKeycloakUserId() != null && !e.getKeycloakUserId().isBlank())
                .map(e -> {
                    try {
                        Map<String, Object> access = keycloakService.getUserAccess(e.getKeycloakUserId());
                        access.put("employeId", id);
                        return ResponseEntity.ok(access);
                    } catch (Exception ex) {
                        log.warn("[KC Admin] getAccess failed for employee {} : {}", id, ex.getMessage());
                        return ResponseEntity.status(502).<Object>body(
                                Map.of("error", "Keycloak unavailable: " + ex.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().<Object>build());
    }

    /**
     * Remplace la liste des rôles realm Keycloak d'un employé.
     *
     * @param id   l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @param body un corps JSON contenant la clé {@code roles} avec la liste des nouveaux rôles
     * @return une réponse HTTP 200 avec le snapshot d'accès mis à jour,
     *         404 si l'employé n'a pas de compte Keycloak,
     *         ou 502 si Keycloak est inaccessible
     */
    @PutMapping("/{id}/roles")
    public ResponseEntity<?> updateRoles(@PathVariable Long id,
                                         @RequestBody Map<String, List<String>> body) {
        return employeeRepository.findById(id)
                .filter(e -> e.getKeycloakUserId() != null)
                .map(e -> {
                    try {
                        keycloakService.updateRealmRoles(e.getKeycloakUserId(),
                                body.getOrDefault("roles", List.of()));
                        Map<String, Object> access = keycloakService.getUserAccess(e.getKeycloakUserId());
                        access.put("employeId", id);
                        return ResponseEntity.ok(access);
                    } catch (Exception ex) {
                        log.warn("[KC Admin] updateRoles failed for employee {} : {}", id, ex.getMessage());
                        return ResponseEntity.status(502).<Object>body(
                                Map.of("error", "Keycloak unavailable: " + ex.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().<Object>build());
    }

    /**
     * Remplace la liste des groupes Keycloak d'un employé.
     *
     * @param id   l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @param body un corps JSON contenant la clé {@code groups} avec les chemins des groupes
     *             (ex. {@code ["/Informatique"]})
     * @return une réponse HTTP 200 avec le snapshot d'accès mis à jour,
     *         404 si l'employé n'a pas de compte Keycloak,
     *         ou 502 si Keycloak est inaccessible
     */
    @PutMapping("/{id}/groups")
    public ResponseEntity<?> updateGroups(@PathVariable Long id,
                                          @RequestBody Map<String, List<String>> body) {
        return employeeRepository.findById(id)
                .filter(e -> e.getKeycloakUserId() != null)
                .map(e -> {
                    try {
                        keycloakService.updateGroups(e.getKeycloakUserId(),
                                body.getOrDefault("groups", List.of()));
                        Map<String, Object> access = keycloakService.getUserAccess(e.getKeycloakUserId());
                        access.put("employeId", id);
                        return ResponseEntity.ok(access);
                    } catch (Exception ex) {
                        log.warn("[KC Admin] updateGroups failed for employee {} : {}", id, ex.getMessage());
                        return ResponseEntity.status(502).<Object>body(
                                Map.of("error", "Keycloak unavailable: " + ex.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().<Object>build());
    }

    /**
     * Active ou désactive le compte Keycloak d'un employé.
     *
     * @param id   l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @param body un corps JSON contenant {@code accountEnabled: true|false}
     * @return une réponse HTTP 200 avec le snapshot d'accès mis à jour,
     *         404 si l'employé n'a pas de compte Keycloak,
     *         ou 502 si Keycloak est inaccessible
     */
    @PutMapping("/{id}/account")
    public ResponseEntity<?> toggleAccount(@PathVariable Long id,
                                           @RequestBody Map<String, Boolean> body) {
        return employeeRepository.findById(id)
                .filter(e -> e.getKeycloakUserId() != null)
                .map(e -> {
                    try {
                        boolean enabled = Boolean.TRUE.equals(body.get("accountEnabled"));
                        keycloakService.setAccountEnabled(e.getKeycloakUserId(), enabled);
                        Map<String, Object> access = keycloakService.getUserAccess(e.getKeycloakUserId());
                        access.put("employeId", id);
                        return ResponseEntity.ok(access);
                    } catch (Exception ex) {
                        log.warn("[KC Admin] toggleAccount failed for employee {} : {}", id, ex.getMessage());
                        return ResponseEntity.status(502).<Object>body(
                                Map.of("error", "Keycloak unavailable: " + ex.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().<Object>build());
    }

    /**
     * Génère un nouveau mot de passe temporaire pour l'employé et le réinitialise dans Keycloak.
     * Le mot de passe est retourné en clair dans la réponse (affiché une seule fois).
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @return une réponse HTTP 200 avec le nouveau mot de passe temporaire
     *         et l'indicateur {@code emailEnvoye},
     *         404 si l'employé n'a pas de compte Keycloak,
     *         ou 502 si Keycloak est inaccessible
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        return employeeRepository.findById(id)
                .filter(e -> e.getKeycloakUserId() != null)
                .map(e -> {
                    try {
                        String newPassword = keycloakService.generateAndResetPassword(e.getKeycloakUserId());
                        return ResponseEntity.ok(Map.of(
                                "motDePasse",  newPassword,
                                "emailEnvoye", false
                        ));
                    } catch (Exception ex) {
                        log.warn("[KC Admin] resetPassword failed for employee {} : {}", id, ex.getMessage());
                        return ResponseEntity.status(502).<Object>body(
                                Map.of("error", "Keycloak unavailable: " + ex.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().<Object>build());
    }
}
