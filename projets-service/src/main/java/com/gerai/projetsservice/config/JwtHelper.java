package com.gerai.projetsservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Utilitaire Spring pour extraire les informations métier depuis un JWT Keycloak.
 * <p>
 * Implémente {@link JwtHelperInterface} et fournit trois stratégies de résolution
 * de l'identifiant employé (employee_id) :
 * <ol>
 *   <li>Claim personnalisé {@code employee_id} configuré dans Keycloak.</li>
 *   <li>Recherche Oracle par {@code USER_ID = JWT.sub}.</li>
 *   <li>Recherche Oracle par {@code EMAIL = JWT.email} avec synchronisation automatique de {@code USER_ID}.</li>
 * </ol>
 * </p>
 * <p>
 * {@code @Component} : enregistre ce composant dans le contexte Spring.<br>
 * {@code @RequiredArgsConstructor} : injection par constructeur via Lombok.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHelper implements JwtHelperInterface {

    /** Template JDBC pour les requêtes Oracle de résolution d'identité. */
    private final JdbcTemplate jdbc;

    /**
     * Resolves the EMPLOYEE_ID for the authenticated user.
     * Strategy (in order):
     *  1. Custom claim  "employee_id" in the JWT  (requires Keycloak mapper)
     *  2. DB lookup by  USER_ID  = JWT sub
     *  3. DB lookup by  EMAIL    = JWT email claim
     */
    public Long getEmployeeId(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) throw new RuntimeException("JWT introuvable");

        // 1. Custom claim
        Object val = jwt.getClaim("employee_id");
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }

        // 2. Keycloak sub → EMPLOYEES.USER_ID
        String sub = jwt.getSubject();
        if (sub != null) {
            try {
                Long id = jdbc.queryForObject(
                    "SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES WHERE USER_ID = ? AND STATUS = 'ACTIF'",
                    Long.class, sub);
                if (id != null) { log.info("[JwtHelper] employee_id={} résolu par sub={}", id, sub); return id; }
            } catch (Exception e) { log.warn("[JwtHelper] sub lookup failed: {}", e.getMessage()); }
        }

        // 3. email claim → EMPLOYEES.EMAIL  (+ auto-sync USER_ID for future lookups)
        String email = jwt.getClaimAsString("email");
        if (email != null) {
            try {
                Long id = jdbc.queryForObject(
                    "SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES WHERE UPPER(EMAIL) = UPPER(?) AND STATUS = 'ACTIF'",
                    Long.class, email);
                if (id != null) {
                    log.info("[JwtHelper] employee_id={} résolu par email={}", id, email);
                    if (sub != null) {
                        try {
                            jdbc.update(
                                "UPDATE GERAI.EMPLOYEES SET USER_ID = ? WHERE EMPLOYEE_ID = ? AND (USER_ID IS NULL OR USER_ID != ?)",
                                sub, id, sub);
                            log.info("[JwtHelper] USER_ID synced for employee_id={}", id);
                        } catch (Exception ignored) {}
                    }
                    return id;
                }
            } catch (Exception e) { log.warn("[JwtHelper] email lookup failed for '{}': {}", email, e.getMessage()); }
        }

        throw new RuntimeException(
            "Employé introuvable pour sub=" + sub + " / email=" + email +
            " — vérifiez EMPLOYEES.USER_ID ou configurez le claim employee_id dans Keycloak");
    }

    /**
     * Extrait l'adresse email de l'utilisateur depuis le claim {@code email} du JWT.
     *
     * @param auth le contexte d'authentification courant
     * @return l'adresse email, ou {@code null} si le JWT est absent ou ne contient pas le claim
     */
    public String getEmail(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    /**
     * Retourne la liste des rôles Keycloak de l'utilisateur authentifié.
     * <p>
     * Les rôles sont extraits du claim {@code realm_access.roles} du JWT.
     * </p>
     *
     * @param auth le contexte d'authentification courant
     * @return liste immuable des rôles, vide si le JWT est absent ou si le claim est manquant
     */
    public List<String> getRoles(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) return List.of();
        Map<String, Object> ra = jwt.getClaim("realm_access");
        if (ra == null) return List.of();
        @SuppressWarnings("unchecked")
        Collection<String> roles = (Collection<String>) ra.get("roles");
        return roles != null ? List.copyOf(roles) : List.of();
    }

    /**
     * Vérifie si l'utilisateur authentifié possède le rôle {@code CHEF}.
     *
     * @param auth le contexte d'authentification courant
     * @return {@code true} si le rôle CHEF est présent
     */
    public boolean isChef(Authentication auth)      { return getRoles(auth).contains("CHEF");  }

    /**
     * Vérifie si l'utilisateur authentifié possède le rôle {@code RH}.
     *
     * @param auth le contexte d'authentification courant
     * @return {@code true} si le rôle RH est présent
     */
    public boolean isRh(Authentication auth)        { return getRoles(auth).contains("RH");    }

    /**
     * Vérifie si l'utilisateur authentifié possède le rôle {@code ADMIN}.
     *
     * @param auth le contexte d'authentification courant
     * @return {@code true} si le rôle ADMIN est présent
     */
    public boolean isAdmin(Authentication auth)     { return getRoles(auth).contains("ADMIN"); }

    /**
     * Vérifie si l'utilisateur est administrateur ou responsable RH.
     *
     * @param auth le contexte d'authentification courant
     * @return {@code true} si le rôle RH ou ADMIN est présent
     */
    public boolean isAdminOrRh(Authentication auth) { return isRh(auth) || isAdmin(auth);      }

    /**
     * Extrait le token JWT depuis un {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}.
     *
     * @param auth le contexte d'authentification
     * @return le JWT, ou {@code null} si l'authentification n'est pas de type JWT
     */
    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken t) return t.getToken();
        return null;
    }
}
