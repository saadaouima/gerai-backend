package com.gerai_backend.gerai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Convertisseur de JWT Keycloak vers des {@link GrantedAuthority} Spring Security.
 *
 * <p>@Component : enregistré comme bean Spring, il est injecté dans {@link SecurityConfig}
 * pour configurer le convertisseur d'authentification JWT.</p>
 *
 * <p>Extrait les rôles depuis deux sources du token JWT Keycloak :</p>
 * <ul>
 *   <li>{@code resource_access.[clientId].roles} — rôles spécifiques au client Angular</li>
 *   <li>{@code realm_access.roles} — rôles realm Keycloak (partagés entre tous les clients)</li>
 * </ul>
 * <p>Chaque rôle est préfixé par {@code ROLE_} et converti en majuscules
 * pour être compatible avec {@code hasAnyRole()} de Spring Security.</p>
 *
 * @since 1.0
 */
@Component
public class KeycloakJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Logger log = LoggerFactory.getLogger(KeycloakJwtRoleConverter.class);

    // Default to synapse-frontend — must match the Angular PKCE client in Keycloak
    // so resource_access.synapse-frontend.roles is correctly extracted from the JWT
    /** Identifiant du client Angular dans Keycloak (ex. {@code synapse-frontend}). */
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:synapse-frontend}")
    private String clientId;

    /**
     * Convertit un token JWT Keycloak en une collection de {@link GrantedAuthority}
     * en fusionnant les rôles client et les rôles realm.
     *
     * @param jwt le token JWT issu de Keycloak
     * @return la collection de toutes les autorités accordées à l'utilisateur
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // Extraction des deux sources de rôles
        Collection<GrantedAuthority> clientRoles = extractClientRoles(jwt);
        Collection<GrantedAuthority> realmRoles  = extractRealmRoles(jwt);

        Collection<GrantedAuthority> allRoles = new ArrayList<>();
        allRoles.addAll(clientRoles);
        allRoles.addAll(realmRoles);

        log.debug("Roles extracted from JWT: {}", allRoles.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", ")));

        return allRoles;
    }

    /**
     * Extrait les rôles spécifiques au client depuis la claim {@code resource_access}
     * du token JWT Keycloak.
     *
     * @param jwt le token JWT Keycloak
     * @return la collection des autorités issues de {@code resource_access.[clientId].roles},
     *         ou une liste vide si la claim est absente
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null || !resourceAccess.containsKey(clientId)) {
            return Collections.emptyList();
        }

        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
        List<String> roles = (List<String>) clientAccess.get("roles");

        if (roles == null) return Collections.emptyList();

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())) // Modif ici
                .collect(Collectors.toList());
    }

    /**
     * Extrait les rôles realm depuis la claim {@code realm_access} du token JWT Keycloak.
     *
     * @param jwt le token JWT Keycloak
     * @return la collection des autorités issues de {@code realm_access.roles},
     *         ou une liste vide si la claim est absente
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return Collections.emptyList();

        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null) return Collections.emptyList();

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())) // Modif ici
                .collect(Collectors.toList());
    }
}