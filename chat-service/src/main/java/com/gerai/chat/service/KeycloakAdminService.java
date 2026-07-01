package com.gerai.chat.service;

import com.gerai.chat.dto.UserDTO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service d'accès à l'API d'administration Keycloak et à la table Oracle {@code EMPLOYEES}.
 * <p>
 * {@code @Service} : déclare ce bean comme service Spring géré par le conteneur IoC.
 * <br>
 * {@code @Slf4j} (Lombok) : injecte un logger SLF4J pour la traçabilité des appels Keycloak.
 * <p>
 * Ce service centralise toutes les opérations nécessitant une interaction avec Keycloak Admin
 * ou la table {@code EMPLOYEES} d'Oracle :
 * <ul>
 *   <li>Résolution du nom complet d'un employé depuis son ID Oracle (avec cache en mémoire).</li>
 *   <li>Liste de tous les employés actifs pour la liste de contacts du chat.</li>
 *   <li>Résolution de l'ID Oracle depuis un UUID Keycloak (avec auto-création si nécessaire).</li>
 *   <li>Résolution de l'ID Oracle depuis un email.</li>
 *   <li>Synchronisation de l'attribut {@code employee_id} dans Keycloak.</li>
 *   <li>Récupération des sessions Keycloak actives pour le service de présence.</li>
 * </ul>
 * <p>
 * L'injection de {@link PresenceService} est différée ({@code @Lazy}) pour briser le cycle de
 * dépendance circulaire entre {@link PresenceService} et {@link KeycloakAdminService}.
 *
 * @since 1.0
 */
@Slf4j
@Service
public class KeycloakAdminService {

    /** URL du serveur Keycloak (ex. {@code http://localhost:8180/auth}), configurée via {@code keycloak.server-url}). */
    @Value("${keycloak.server-url}")
    private String serverUrl;

    /** Nom du realm Keycloak de l'application (ex. {@code gerai}), configuré via {@code keycloak.realm}). */
    @Value("${keycloak.realm}")
    private String realm;

    /** Nom d'utilisateur de l'administrateur Keycloak, configuré via {@code keycloak.admin-username}). */
    @Value("${keycloak.admin-username}")
    private String adminUsername;

    /** Mot de passe de l'administrateur Keycloak, configuré via {@code keycloak.admin-password}). */
    @Value("${keycloak.admin-password}")
    private String adminPassword;

    /** Identifiant du client Keycloak de l'application (ex. {@code gerai-client}), configuré via {@code keycloak.client-id}). */
    @Value("${keycloak.client-id}")
    private String clientId;

    /** Instance cliente Keycloak Admin, créée à la demande et réutilisée (pattern lazy singleton). */
    private Keycloak keycloak;

    /** Template JDBC pour les requêtes directes sur la base Oracle {@code EMPLOYEES}. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Service de présence, injecté avec {@code @Lazy} pour briser le cycle :
     * {@code PresenceService} → {@code KeycloakAdminService} → {@code PresenceService}.
     */
    @Lazy
    @Autowired
    private PresenceService presenceService;

    /** Cache en mémoire : {@code employee_id} (Oracle Long) → nom complet de l'employé. */
    private final Map<Long, String> nomCache = new ConcurrentHashMap<>();

    /* ── API Publique ─────────────────────────────────── */

    /**
     * Résout le nom complet d'un employé depuis son ID Oracle.
     */
    public String getNomByEmployeeId(Long employeeId) {
        if (employeeId == null) return "Inconnu";

        return nomCache.computeIfAbsent(employeeId, id -> {
            // 1. Essai via Keycloak Admin
            try {
                List<UserRepresentation> users = getClient().realm(realm).users()
                        .searchByAttributes("employee_id:" + id);
                if (!users.isEmpty()) {
                    return buildFullName(users.get(0));
                }
            } catch (Exception e) {
                log.warn("[Keycloak] Admin indisponible pour employee_id={} : {}", id, e.getMessage());
            }

            // 2. Fallback : EMPLOYEES table
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT FIRST_NAME, LAST_NAME FROM EMPLOYEES WHERE EMPLOYEE_ID = ?", id);
                if (!rows.isEmpty()) {
                    String prenom = String.valueOf(rows.get(0).getOrDefault("FIRST_NAME", ""));
                    String nom    = String.valueOf(rows.get(0).getOrDefault("LAST_NAME",  ""));
                    return (prenom + " " + nom).trim();
                }
            } catch (Exception e) {
                log.warn("[Chat] DB fallback nom échoué pour employee_id={} : {}", id, e.getMessage());
            }

            return "Employé #" + id;
        });
    }

    /**
     * Liste tous les employés actifs pour la liste de contacts.
     * Source primaire : table EMPLOYEES (Oracle). Keycloak est utilisé uniquement
     * pour remplir le champ keycloakId quand USER_ID n'est pas encore lié.
     */
    public List<UserDTO> getAllUsers(Long excludeEmployeeId) {
        try {
            List<Map<String, Object>> rows = excludeEmployeeId != null
                ? jdbcTemplate.queryForList(
                    "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, USER_ID " +
                    "FROM EMPLOYEES WHERE STATUS = 'ACTIF' AND EMPLOYEE_ID != ? ORDER BY LAST_NAME, FIRST_NAME",
                    excludeEmployeeId)
                : jdbcTemplate.queryForList(
                    "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, USER_ID " +
                    "FROM EMPLOYEES WHERE STATUS = 'ACTIF' ORDER BY LAST_NAME, FIRST_NAME");

            return rows.stream().map(r -> {
                Long   empId     = r.get("EMPLOYEE_ID") != null ? ((Number) r.get("EMPLOYEE_ID")).longValue() : null;
                String userId    = r.get("USER_ID")    != null ? r.get("USER_ID").toString()    : "";
                String firstName = r.get("FIRST_NAME") != null ? r.get("FIRST_NAME").toString() : "";
                String lastName  = r.get("LAST_NAME")  != null ? r.get("LAST_NAME").toString()  : "";
                String email     = r.get("EMAIL")      != null ? r.get("EMAIL").toString()       : "";
                String nomComplet = (firstName + " " + lastName).trim();
                if (nomComplet.isBlank()) nomComplet = email;

                if (empId != null) nomCache.put(empId, nomComplet);

                // If USER_ID not set, try to find via Keycloak email lookup and auto-link
                if (userId.isBlank() && !email.isBlank()) {
                    try {
                        List<UserRepresentation> kcUsers = getClient().realm(realm).users()
                                .searchByEmail(email, true);
                        if (!kcUsers.isEmpty()) {
                            userId = kcUsers.get(0).getId();
                            final Long finalEmpId = empId;
                            final String finalUserId = userId;
                            jdbcTemplate.update(
                                "UPDATE EMPLOYEES SET USER_ID = ? WHERE EMPLOYEE_ID = ?",
                                finalUserId, finalEmpId);
                            log.info("[Chat] getAllUsers: auto-linked USER_ID={} for employee_id={}", userId, empId);
                        }
                    } catch (Exception ex) {
                        log.warn("[Chat] Keycloak email lookup failed for {}: {}", email, ex.getMessage());
                    }
                }

                boolean online = empId != null && presenceService.estConnecte(empId.toString());
                return new UserDTO(
                        userId.isBlank() ? null : userId,
                        empId,
                        lastName,
                        firstName,
                        nomComplet,
                        email,
                        online
                );
            }).toList();

        } catch (Exception e) {
            log.error("[Chat] getAllUsers DB query failed, falling back to Keycloak: {}", e.getMessage());
            return getAllUsersFromKeycloak();
        }
    }

    /**
     * Fallback : construit la liste des contacts directement depuis Keycloak
     * lorsque la requête Oracle principale a échoué.
     * <p>
     * Pour chaque utilisateur Keycloak, tente de résoudre l'ID Oracle via :
     * <ol>
     *   <li>L'attribut {@code employee_id} dans Keycloak.</li>
     *   <li>La colonne {@code USER_ID} dans {@code EMPLOYEES}.</li>
     *   <li>L'email de l'utilisateur (avec auto-link si trouvé).</li>
     * </ol>
     *
     * @return la liste des {@link UserDTO} construite depuis Keycloak, ou une liste vide en cas d'erreur
     */
    private List<UserDTO> getAllUsersFromKeycloak() {
        try {
            return getClient().realm(realm).users().list()
                    .stream()
                    .map(u -> {
                        Long empId = extractEmployeeId(u);
                        String nomComplet = buildFullName(u);

                        if (empId == null) {
                            try {
                                List<Long> ids = jdbcTemplate.queryForList(
                                        "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE USER_ID = ?",
                                        Long.class, u.getId());
                                if (!ids.isEmpty()) empId = ids.get(0);
                            } catch (Exception ex) {
                                log.warn("[Chat] DB USER_ID lookup failed for {}: {}", u.getId(), ex.getMessage());
                            }
                        }

                        if (empId == null && u.getEmail() != null && !u.getEmail().isBlank()) {
                            try {
                                List<Long> ids = jdbcTemplate.queryForList(
                                        "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE UPPER(EMAIL) = UPPER(?)",
                                        Long.class, u.getEmail());
                                if (!ids.isEmpty()) {
                                    empId = ids.get(0);
                                    jdbcTemplate.update(
                                            "UPDATE EMPLOYEES SET USER_ID = ? WHERE EMPLOYEE_ID = ?",
                                            u.getId(), empId);
                                }
                            } catch (Exception ex) {
                                log.warn("[Chat] DB email lookup failed for {}: {}", u.getEmail(), ex.getMessage());
                            }
                        }

                        if (empId != null) nomCache.put(empId, nomComplet);

                        boolean online = empId != null && presenceService.estConnecte(empId.toString());
                        return new UserDTO(
                                u.getId(),
                                empId,
                                u.getLastName()  != null ? u.getLastName()  : "",
                                u.getFirstName() != null ? u.getFirstName() : "",
                                nomComplet,
                                u.getUsername(),
                                online
                        );
                    })
                    .toList();
        } catch (Exception e) {
            log.error("[Keycloak] getAllUsersFromKeycloak failed: {}", e.getMessage());
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

    /**
     * Retourne le client Keycloak Admin, en le créant si nécessaire (lazy singleton synchronisé).
     * Utilise les credentials de l'administrateur configurés via les propriétés {@code keycloak.*}.
     *
     * @return l'instance {@link Keycloak} prête à l'emploi (connectée au realm {@code master})
     */
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

    /**
     * Extrait l'ID Oracle de l'employé depuis les attributs personnalisés d'un utilisateur Keycloak.
     * Lit l'attribut {@code employee_id} et le convertit en {@code Long}.
     *
     * @param u la représentation Keycloak de l'utilisateur
     * @return l'ID Oracle de l'employé, ou {@code null} si l'attribut est absent ou invalide
     */
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

    /**
     * Construit le nom complet d'un utilisateur Keycloak depuis ses attributs {@code firstName} et {@code lastName}.
     * Utilise le {@code username} Keycloak si les deux noms sont vides.
     *
     * @param u la représentation Keycloak de l'utilisateur
     * @return le nom complet formaté (ex. "Marie Dupont"), ou le username si les noms sont absents
     */
    private String buildFullName(UserRepresentation u) {
        String prenom = u.getFirstName() != null ? u.getFirstName() : "";
        String nom = u.getLastName() != null ? u.getLastName() : "";
        String full = (prenom + " " + nom).trim();
        return full.isEmpty() ? u.getUsername() : full;
    }
    /**
     * Résout l'ID Oracle depuis un Keycloak subject (UUID).
     * Essaie d'abord la colonne USER_ID (EMPLOYEES), puis Keycloak Admin.
     */
    public Optional<Long> findEmployeeIdByKeycloakId(String keycloakId) {
        // 1. Lookup direct via USER_ID (colonne Keycloak sub dans EMPLOYEES)
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE USER_ID = ?", Long.class, keycloakId);
            if (!ids.isEmpty()) {
                log.info("[Chat] employee_id={} résolu via USER_ID pour sub={}", ids.get(0), keycloakId);
                return Optional.of(ids.get(0));
            }
        } catch (Exception e) {
            log.warn("[Chat] DB lookup USER_ID failed for sub={}: {}", keycloakId, e.getMessage());
        }

        // 2. Fallback Keycloak Admin — check employee_id attribute
        UserRepresentation kcUser = null;
        try {
            kcUser = getClient().realm(realm).users().get(keycloakId).toRepresentation();
            Long empId = extractEmployeeId(kcUser);
            if (empId != null) return Optional.of(empId);
        } catch (Exception e) {
            log.warn("[Keycloak] findEmployeeIdByKeycloakId({}) failed: {}", keycloakId, e.getMessage());
        }

        // 3. Auto-create / auto-link EMPLOYEES row from Keycloak user data
        try {
            if (kcUser == null) {
                kcUser = getClient().realm(realm).users().get(keycloakId).toRepresentation();
            }
            String email     = kcUser.getEmail();
            String firstName = kcUser.getFirstName() != null ? kcUser.getFirstName() : "Admin";
            String lastName  = kcUser.getLastName()  != null ? kcUser.getLastName()  : "";

            // Link by email if row already exists
            if (email != null && !email.isBlank()) {
                List<Long> byEmail = jdbcTemplate.queryForList(
                        "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE UPPER(EMAIL) = UPPER(?)", Long.class, email);
                if (!byEmail.isEmpty()) {
                    jdbcTemplate.update("UPDATE EMPLOYEES SET USER_ID = ? WHERE EMPLOYEE_ID = ?",
                            keycloakId, byEmail.get(0));
                    log.info("[Chat] Linked USER_ID for existing employee_id={} via email", byEmail.get(0));
                    return Optional.of(byEmail.get(0));
                }
            }

            // Create a minimal EMPLOYEES row using the first available dept/position
            Long deptId = jdbcTemplate.queryForObject("SELECT MIN(DEPT_ID) FROM DEPARTMENTS", Long.class);
            Long posId  = jdbcTemplate.queryForObject("SELECT MIN(POSITION_ID) FROM POSITIONS",  Long.class);
            if (deptId == null || posId == null) {
                log.warn("[Chat] Cannot auto-create employee — no departments/positions in DB");
                return Optional.empty();
            }
            String code = "ADM-" + System.currentTimeMillis();
            String effectiveEmail = (email != null && !email.isBlank()) ? email : keycloakId + "@admin.local";
            jdbcTemplate.update(
                    "INSERT INTO EMPLOYEES(EMPLOYEE_CODE, FIRST_NAME, LAST_NAME, EMAIL, USER_ID, STATUS, HIRE_DATE, DEPT_ID, POSITION_ID) " +
                    "VALUES(?, ?, ?, ?, ?, 'ACTIF', SYSDATE, ?, ?)",
                    code, firstName, lastName, effectiveEmail, keycloakId, deptId, posId);

            List<Long> newIds = jdbcTemplate.queryForList(
                    "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE USER_ID = ?", Long.class, keycloakId);
            if (!newIds.isEmpty()) {
                log.info("[Chat] Auto-created employee_id={} for keycloakId={}", newIds.get(0), keycloakId);
                return Optional.of(newIds.get(0));
            }
        } catch (Exception e) {
            log.warn("[Chat] Auto-create employee failed for keycloakId={}: {}", keycloakId, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Recherche l'ID Oracle d'un utilisateur par son email.
     * Tente d'abord Keycloak Admin, puis requête directe sur EMPLOYEES.
     */
    public Optional<Long> findEmployeeIdByEmail(String email) {
        // 1. Essai via Keycloak Admin
        try {
            List<UserRepresentation> users = getClient().realm(realm).users()
                    .searchByEmail(email, true);
            if (!users.isEmpty()) {
                UserRepresentation u = users.get(0);
                if (u.getAttributes() != null && u.getAttributes().containsKey("employee_id")) {
                    return Optional.of(Long.parseLong(u.getAttributes().get("employee_id").get(0)));
                }
            }
        } catch (Exception e) {
            log.warn("[Keycloak] Admin indisponible pour email={}, tentative DB : {}", email, e.getMessage());
        }

        // 2. Fallback : recherche directe dans EMPLOYEES
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE EMAIL = ?", Long.class, email);
            if (!ids.isEmpty()) {
                log.info("[Chat] employee_id={} résolu via DB pour email={}", ids.get(0), email);
                return Optional.of(ids.get(0));
            }
        } catch (Exception e) {
            log.error("[Chat] DB fallback échoué pour email={} : {}", email, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Returns the set of Oracle employee_id strings for all users who currently
     * have an active Keycloak session — using exactly 2 API calls:
     *   1. GET /clients?clientId=... → resolve internal UUID
     *   2. GET /clients/{uuid}/user-sessions → all active sessions in one shot
     *   3. SELECT ... WHERE USER_ID IN (...) → resolve to Oracle IDs in one DB query
     */
    public Set<String> getOnlineEmployeeIds() {
        try {
            // 1. Resolve client internal UUID from the configured clientId string
            List<ClientRepresentation> clients = getClient().realm(realm)
                    .clients().findByClientId(clientId);
            if (clients.isEmpty()) {
                log.warn("[Presence] No Keycloak client found for clientId='{}'", clientId);
                return Set.of();
            }
            String internalId = clients.get(0).getId();

            // 2. Fetch ALL active sessions for this client in one call
            List<UserSessionRepresentation> sessions = getClient().realm(realm)
                    .clients().get(internalId)
                    .getUserSessions(0, 5000);

            if (sessions.isEmpty()) return Set.of();

            // 3. Collect distinct Keycloak UUIDs from those sessions
            List<String> kcIds = sessions.stream()
                    .map(UserSessionRepresentation::getUserId)
                    .distinct()
                    .collect(Collectors.toList());

            // 4. Resolve to Oracle employee IDs in a single IN query
            String placeholders = kcIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Long> empIds = jdbcTemplate.queryForList(
                    "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE USER_ID IN (" + placeholders + ")",
                    Long.class,
                    kcIds.toArray());

            return empIds.stream().map(Object::toString).collect(Collectors.toSet());

        } catch (Exception e) {
            log.warn("[Presence] getOnlineEmployeeIds failed: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * Ferme proprement le client Keycloak Admin lors de l'arrêt du contexte Spring.
     * Libère les ressources HTTP (connexions, tokens) associées à la session admin.
     */
    @PreDestroy
    public void close() {
        if (keycloak != null && !keycloak.isClosed()) {
            keycloak.close();
        }
    }
}