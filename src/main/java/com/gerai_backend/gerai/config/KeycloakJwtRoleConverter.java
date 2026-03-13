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

@Component
public class KeycloakJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Logger log = LoggerFactory.getLogger(KeycloakJwtRoleConverter.class);

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> clientRoles = extractClientRoles(jwt);
        Collection<GrantedAuthority> realmRoles  = extractRealmRoles(jwt);

        Collection<GrantedAuthority> allRoles = new ArrayList<>();
        allRoles.addAll(clientRoles);
        allRoles.addAll(realmRoles);

        log.info(">>> clientId     = {}", clientId);
        log.info(">>> client roles = {}", clientRoles);
        log.info(">>> realm roles  = {}", realmRoles);
        log.info(">>> all roles    = {}", allRoles);

        return allRoles;
    }

    // ── Extract client roles from resource_access.{clientId}.roles ──
    private Collection<GrantedAuthority> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");

        log.info(">>> resource_access claim = {}", resourceAccess);

        if (resourceAccess == null) {
            log.warn(">>> resource_access claim is null");
            return Collections.emptyList();
        }

        if (!resourceAccess.containsKey(clientId)) {
            log.warn(">>> No roles found for clientId: {}", clientId);
            log.warn(">>> Available clients in token: {}", resourceAccess.keySet());
            return Collections.emptyList();
        }

        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);

        if (clientAccess == null) {
            log.warn(">>> clientAccess is null for clientId: {}", clientId);
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) clientAccess.get("roles");

        if (roles == null || roles.isEmpty()) {
            log.warn(">>> No roles list found for clientId: {}", clientId);
            return Collections.emptyList();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }

    // ── Extract realm roles from realm_access.roles ──
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");

        if (realmAccess == null) {
            log.warn(">>> realm_access claim is null");
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) realmAccess.get("roles");

        if (roles == null || roles.isEmpty()) {
            log.warn(">>> No realm roles found");
            return Collections.emptyList();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}