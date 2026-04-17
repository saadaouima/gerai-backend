package com.gerai.chat.service;

import com.gerai.chat.dto.UserDTO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service d'accès à l'API Admin Keycloak
 */
@Slf4j
@Service
public class KeycloakAdminService {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-username}")
    private String adminUsername;

    @Value("${keycloak.admin-password}")
    private String adminPassword;

    private Keycloak keycloak;

    /** Cache employee_id (Oracle) → nom complet */
    private final Map<Long, String> nomCache = new ConcurrentHashMap<>();

    /* ── API Publique ─────────────────────────────────── */

    /**
     * Résout le nom complet d'un employé depuis son ID Oracle.
     */
    public String getNomByEmployeeId(Long employeeId) {
        if (employeeId == null) return "Inconnu";

        return nomCache.computeIfAbsent(employeeId, id -> {
            try {
                // Recherche par attribut personnalisé dans Keycloak
                List<UserRepresentation> users = getClient().realm(realm).users()
                        .searchByAttributes("employee_id:" + id);

                if (!users.isEmpty()) {
                    UserRepresentation u = users.get(0);
                    return buildFullName(u);
                }
            } catch (Exception e) {
                log.warn("[Keycloak] Impossible de résoudre employee_id={} : {}", id, e.getMessage());
            }
            return "Employé #" + id;
        });
    }

    /**
     * Liste tous les utilisateurs du realm pour la liste de contacts.
     */
    public List<UserDTO> getAllUsers() {
        try {
            return getClient().realm(realm).users().list()
                    .stream()
                    .map(u -> {
                        Long empId = extractEmployeeId(u);
                        String nomComplet = buildFullName(u);

                        if (empId != null) {
                            nomCache.put(empId, nomComplet);
                        }

                        return new UserDTO(
                                u.getId(),
                                empId,
                                u.getLastName() != null ? u.getLastName() : "",
                                u.getFirstName() != null ? u.getFirstName() : "",
                                nomComplet,
                                u.getUsername(),
                                false // Par défaut, la gestion de présence est gérée par le Front
                        );
                    })
                    .toList();
        } catch (Exception e) {
            log.error("[Keycloak] Erreur lors de la récupération de la liste des utilisateurs : {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * SYNCHRONISATION AUTOMATIQUE :
     * Met à jour l'ID Oracle dans les attributs Keycloak.
     * À appeler lors du CRUD Employé.
     */
    public void updateEmployeeIdAttribute(String keycloakUserId, Long oracleEmployeeId) {
        try {
            UserResource userResource = getClient().realm(realm).users().get(keycloakUserId);
            UserRepresentation user = userResource.toRepresentation();

            if (user.getAttributes() == null) {
                user.setAttributes(new HashMap<>());
            }

            user.getAttributes().put("employee_id", List.of(oracleEmployeeId.toString()));
            userResource.update(user);

            log.info("[Keycloak] Attribut employee_id={} synchronisé pour {}", oracleEmployeeId, user.getUsername());
        } catch (Exception e) {
            log.error("[Keycloak] Échec de la synchronisation de l'attribut pour {}: {}", keycloakUserId, e.getMessage());
        }
    }

    /* ── Helpers ──────────────────────────────────────── */

    private synchronized Keycloak getClient() {
        if (keycloak == null || keycloak.isClosed()) {
            keycloak = KeycloakBuilder.builder()
                    .serverUrl(serverUrl)
                    .realm("master") // Authentification admin via master
                    .clientId("admin-cli")
                    .username(adminUsername)
                    .password(adminPassword)
                    .build();
        }
        return keycloak;
    }

    private Long extractEmployeeId(UserRepresentation u) {
        if (u.getAttributes() == null) return null;
        List<String> vals = u.getAttributes().get("employee_id");
        if (vals == null || vals.isEmpty()) return null;
        try {
            return Long.parseLong(vals.get(0));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildFullName(UserRepresentation u) {
        String prenom = u.getFirstName() != null ? u.getFirstName() : "";
        String nom = u.getLastName() != null ? u.getLastName() : "";
        String full = (prenom + " " + nom).trim();
        return full.isEmpty() ? u.getUsername() : full;
    }
    /**
     * Recherche l'ID Oracle d'un utilisateur par son email (Fallback pour anciens tokens).
     */
    public java.util.Optional<Long> findEmployeeIdByEmail(String email) {
        try {
            // searchByEmail avec 'true' pour une correspondance exacte
            List<UserRepresentation> users = getClient().realm(realm).users()
                    .searchByEmail(email, true);

            if (!users.isEmpty()) {
                UserRepresentation u = users.get(0);

                // On réutilise la logique d'extraction d'attribut que tu as déjà écrite
                if (u.getAttributes() != null && u.getAttributes().containsKey("employee_id")) {
                    String idStr = u.getAttributes().get("employee_id").get(0);
                    return java.util.Optional.of(Long.parseLong(idStr));
                }
            }
        } catch (Exception e) {
            log.error("[Keycloak] Erreur fallback email {}: {}", email, e.getMessage());
        }
        return java.util.Optional.empty();
    }

    @PreDestroy
    public void close() {
        if (keycloak != null && !keycloak.isClosed()) {
            keycloak.close();
        }
    }
}