package com.gerai.demandesservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Convertisseur d'autorités Keycloak → Spring Security.
 * <p>
 * Extrait les rôles du claim {@code realm_access.roles} du JWT Keycloak
 * et les transforme en objets {@link org.springframework.security.core.authority.SimpleGrantedAuthority}
 * préfixés par {@code ROLE_}, ce qui permet l'utilisation de
 * {@code @PreAuthorize("hasRole('CHEF')")} dans les contrôleurs.
 * <p>
 * Exemple : le rôle Keycloak {@code EMPLOYE} devient l'autorité Spring {@code ROLE_EMPLOYE}.
 *
 * @since 1.0
 */
class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * Convertit un token JWT Keycloak en collection d'autorités Spring Security.
     * <p>
     * Si le claim {@code realm_access} est absent ou vide, retourne une liste vide.
     *
     * @param jwt le token JWT émis par Keycloak
     * @return la collection des autorités Spring Security correspondant aux rôles Keycloak
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");

        if (realmAccess == null || realmAccess.isEmpty()) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");

        return roles.stream()
                .map(roleName -> "ROLE_" + roleName.toUpperCase()) // Ajoute ROLE_ pour hasRole('CHEF')
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
