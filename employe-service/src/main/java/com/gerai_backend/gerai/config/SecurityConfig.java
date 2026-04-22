package com.gerai_backend.gerai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final KeycloakJwtRoleConverter keycloakJwtRoleConverter;

    public SecurityConfig(KeycloakJwtRoleConverter keycloakJwtRoleConverter) {
        this.keycloakJwtRoleConverter = keycloakJwtRoleConverter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(keycloakJwtRoleConverter);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth

                        /*
                         * GET /employees/**
                         *
                         * Roles autorisés :
                         *   - admin          : RH / administrateur complet
                         *   - employees:read : permission fine Keycloak
                         *   - CHEF           : chef d'équipe → lit les fiches pour enrichir
                         *                      les notifications Kafka (projet-service)
                         *   - RH             : alias métier du rôle admin dans certains realms
                         *
                         * Note Spring Security : hasAnyRole("CHEF") cherche "ROLE_CHEF"
                         * dans les GrantedAuthority. KeycloakJwtRoleConverter préfixe
                         * automatiquement "ROLE_" → cohérent.
                         */
                        .requestMatchers(HttpMethod.GET,    "/employees/**")
                        .hasAnyRole("admin", "RH","chef", "CHEF", "employees:read")

                        .requestMatchers(HttpMethod.POST,   "/employees/**")
                        .hasAnyRole("admin", "RH", "employees:write")

                        .requestMatchers(HttpMethod.PUT,    "/employees/**")
                        .hasAnyRole("admin", "RH", "employees:update")

                        .requestMatchers(HttpMethod.DELETE, "/employees/**")
                        .hasAnyRole("admin", "RH", "employees:delete")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                );

        return http.build();
    }
}