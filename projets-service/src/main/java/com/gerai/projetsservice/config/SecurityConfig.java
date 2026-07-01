package com.gerai.projetsservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration de la sécurité HTTP du microservice projets-service.
 * <p>
 * Paramètres appliqués :
 * <ul>
 *   <li>CSRF désactivé (API REST stateless).</li>
 *   <li>CORS autorisé depuis {@code http://localhost:4200} (Angular dev).</li>
 *   <li>Endpoints {@code /api/public/**} ouverts sans authentification (candidatures publiques).</li>
 *   <li>Tous les autres endpoints nécessitent un JWT Keycloak valide.</li>
 *   <li>Les rôles Keycloak sont convertis en {@code ROLE_<NOM>} pour Spring Security.</li>
 * </ul>
 * </p>
 * <p>
 * {@code @EnableWebSecurity} : active la configuration Spring Security.<br>
 * {@code @EnableMethodSecurity} : active {@code @PreAuthorize} sur les méthodes.
 * </p>
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Définit la chaîne de filtres de sécurité HTTP.
     *
     * @param http le constructeur de sécurité Spring
     * @return la chaîne de filtres configurée
     * @throws Exception en cas d'erreur de configuration Spring Security
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
                );
        return http.build();
    }

    /**
     * Convertisseur JWT qui mappe les rôles Keycloak ({@code realm_access.roles})
     * en autorités Spring Security au format {@code ROLE_<NOM_ROLE>}.
     *
     * @return le convertisseur d'authentification JWT configuré
     */
    @Bean
    public JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null) return Collections.emptyList();
            @SuppressWarnings("unchecked")
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");
            if (roles == null) return Collections.emptyList();
            return roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                    .collect(Collectors.toList());
        });
        return converter;
    }

    /**
     * Source de configuration CORS autorisant le frontend Angular ({@code localhost:4200}).
     * <p>
     * Méthodes autorisées : GET, POST, PUT, PATCH, DELETE, OPTIONS.<br>
     * En-têtes : tous ({@code *}).<br>
     * Credentials : activés (cookies de session/JWT).
     * </p>
     *
     * @return la source de configuration CORS
     */
    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:4200"));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
