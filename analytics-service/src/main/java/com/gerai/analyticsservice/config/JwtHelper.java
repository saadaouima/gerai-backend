package com.gerai.analyticsservice.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Utilitaire pour extraire les claims du JWT Keycloak.
 *
 * Claims standards Keycloak :
 *   sub       → UUID Keycloak de l'utilisateur
 *   email     → adresse email
 *   realm_access.roles → liste des rôles
 *
 * Claim custom (à configurer dans Keycloak via un mapper) :
 *   dept_id   → l'ID Oracle du département de l'employé
 *               Permet d'éviter une requête SQL pour résoudre le département d'un Chef.
 *
 * Configuration du claim custom dans Keycloak :
 *   Clients → gerai-backend → Client Scopes → Add Mapper
 *   Type : User Attribute
 *   User Attribute : dept_id
 *   Token Claim Name : dept_id
 *   Claim JSON Type : long
 */
@Component
public class JwtHelper {

    private static final String ROLE_RH    = "ROLE_RH";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_CHEF  = "ROLE_CHEF";

    /* ── Extraction des claims ─────────────────────────── */

    /**
     * Extrait le subject (UUID Keycloak) du JWT.
     * Utilisé pour résoudre le dept_id via findDeptIdBySubject().
     */
    public String getSubject(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        return jwt != null ? jwt.getSubject() : null;
    }

    /**
     * Extrait l'email du JWT.
     * Fallback si sub ne résout pas de dept_id.
     */
    public String getEmail(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    /**
     * Extrait le claim custom "dept_id" du JWT.
     * Retourne null si le mapper n'est pas configuré dans Keycloak.
     * Dans ce cas, StatsService effectuera une requête Oracle de fallback.
     */
    public Long getDeptId(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) return null;
        Object claim = jwt.getClaim("dept_id");
        if (claim == null) return null;
        try {
            return Long.parseLong(claim.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* ── Tests de rôle ─────────────────────────────────── */

    public boolean isChef(Authentication auth) {
        return hasRole(auth, ROLE_CHEF);
    }

    public boolean isRh(Authentication auth) {
        return hasRole(auth, ROLE_RH);
    }

    public boolean isAdmin(Authentication auth) {
        return hasRole(auth, ROLE_ADMIN);
    }

    /**
     * Retourne le rôle principal de l'utilisateur connecté.
     * Ordre de priorité : ADMIN > RH > CHEF > EMPLOYE
     */
    public String getPrimaryRole(Authentication auth) {
        if (auth == null) return "ANONYME";
        if (isAdmin(auth)) return "ADMIN";
        if (isRh(auth))    return "RH";
        if (isChef(auth))  return "CHEF";
        return "EMPLOYE";
    }

    /* ── Helpers privés ────────────────────────────────── */

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}