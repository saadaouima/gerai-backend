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
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // ← stateless REST API
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,    "/employees/**").hasAnyRole("admin", "employees:read")
                        .requestMatchers(HttpMethod.POST,   "/employees/**").hasAnyRole("admin", "employees:write")
                        .requestMatchers(HttpMethod.PUT,    "/employees/**").hasAnyRole("admin", "employees:update")
                        .requestMatchers(HttpMethod.DELETE, "/employees/**").hasAnyRole("admin", "employees:delete")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                );

        return http.build();
    }
}