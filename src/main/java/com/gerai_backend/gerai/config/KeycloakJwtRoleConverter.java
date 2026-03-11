package com.gerai_backend.gerai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.Authentication;


// --- Roles/Authorities ---
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// --- Java utils ---
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KeycloakJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>>  {

    private static final Logger log = LoggerFactory.getLogger(KeycloakJwtRoleConverter.class);

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractClientRoles(jwt);
        log.info(">>> clientId = {}", clientId);
        log.info(">>> extracted roles = {}", authorities);
        return authorities;
    }

    private Collection<GrantedAuthority> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");

        log.info(">>> resource_access claim = {}", resourceAccess);

        if (resourceAccess == null || !resourceAccess.containsKey(clientId)) {
            log.warn(">>> No roles found for clientId: {}", clientId);
            return Collections.emptyList();
        }

        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
        List<String> roles = (List<String>) clientAccess.get("roles");

        if (roles == null) return Collections.emptyList();

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
