package com.gerai.tachesservice.config;

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
 * Configuration de la sécurité HTTP pour taches-service.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * {@code @EnableWebSecurity} : active la chaîne de filtres de sécurité Spring Security
 * et désactive la configuration automatique par défaut.
 * {@code @EnableMethodSecurity} : active les annotations de sécurité au niveau des méthodes
 * ({@code @PreAuthorize}, {@code @PostAuthorize}) utilisées dans les contrôleurs.
 * <p>
 * Ce service est un Resource Server OAuth2 : il valide les tokens JWT émis par Keycloak
 * et extrait les rôles depuis le claim {@code realm_access.roles}.
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Définit la chaîne de filtres de sécurité HTTP du service.
     * <p>
     * Règles appliquées :
     * <ul>
     *   <li>CSRF désactivé (API REST stateless, pas de session)</li>
     *   <li>CORS configuré pour autoriser l'application Angular sur {@code localhost:4200}</li>
     *   <li>Toutes les requêtes vers {@code /api/affectation/**} et {@code /api/taches/**}
     *       nécessitent une authentification JWT valide</li>
     *   <li>Validation des tokens JWT via le convertisseur {@link #jwtConverter()}</li>
     * </ul>
     *
     * @param http le constructeur de configuration de sécurité HTTP
     * @return la chaîne de filtres de sécurité configurée
     * @throws Exception en cas d'erreur de configuration Spring Security
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/affectation/**").authenticated()
                        .requestMatchers("/api/taches/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
                );
        return http.build();
    }

    /**
     * Convertisseur JWT qui extrait les rôles Keycloak depuis le claim {@code realm_access.roles}
     * et les transforme en autorités Spring Security préfixées par {@code ROLE_}.
     * <p>
     * Exemple : le rôle Keycloak {@code "CHEF"} devient {@code "ROLE_CHEF"},
     * ce qui permet son utilisation dans les expressions {@code @PreAuthorize("hasRole('CHEF')")}.
     *
     * @return le convertisseur JWT configuré pour lire les rôles Keycloak
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
     * Définit la politique CORS autorisant l'application Angular à communiquer
     * avec ce service depuis {@code http://localhost:4200}.
     * <p>
     * Méthodes autorisées : GET, POST, PUT, PATCH, DELETE, OPTIONS.
     * Tous les en-têtes sont acceptés. Les credentials (cookies, JWT) sont autorisés.
     *
     * @return la source de configuration CORS appliquée à tous les chemins ({@code /**})
     */
    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:4200"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}