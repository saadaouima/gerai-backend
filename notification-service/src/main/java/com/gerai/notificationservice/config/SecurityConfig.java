package com.gerai.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration de la sécurité HTTP du microservice notification-service.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.<br>
 * {@code @EnableWebSecurity} : active la chaîne de filtres de sécurité Spring Security.<br>
 * {@code @EnableMethodSecurity} : autorise l'usage de {@code @PreAuthorize} /
 * {@code @PostAuthorize} sur les méthodes des contrôleurs.
 * </p>
 * <p>
 * Règles d'accès configurées :
 * <ul>
 *   <li>Endpoints WebSocket ({@code /ws/**}, {@code /ws-notifications/**}) : accès libre
 *       (l'authentification est réalisée au niveau STOMP dans {@code WebSocketConfig}).</li>
 *   <li>Endpoints internes ({@code /internal/**}) : accès libre (appelés inter-services).</li>
 *   <li>API REST ({@code /api/notifications/**}) : authentification JWT obligatoire.</li>
 * </ul>
 * CSRF désactivé car l'API est stateless (JWT).
 * </p>
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Définit la chaîne de filtres de sécurité principale : configuration CORS,
     * désactivation du CSRF, règles d'autorisation et validation des JWT Keycloak.
     *
     * @param http le constructeur de configuration de sécurité HTTP fourni par Spring Security
     * @return la {@link SecurityFilterChain} construite et enregistrée dans le contexte Spring
     * @throws Exception si la construction de la chaîne de filtres échoue
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ✅ CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ❌ CSRF inutile pour API REST + JWT
                .csrf(csrf -> csrf.disable())

                // ✅ Règles d'accès
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**", "/ws-notifications/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .anyRequest().permitAll()
                )

                // ✅ Keycloak JWT
                .oauth2ResourceServer(oauth ->
                        oauth.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Convertisseur des rôles Keycloak extraits du claim {@code realm_access.roles}
     * du JWT vers des {@link org.springframework.security.core.GrantedAuthority}
     * Spring Security (préfixe {@code ROLE_}).
     *
     * @return le {@link JwtAuthenticationConverter} configuré pour lire les rôles Keycloak
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");

            if (realmAccess == null || realmAccess.get("roles") == null) {
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            Collection<String> roles =
                    (Collection<String>) realmAccess.get("roles");

            return roles.stream()
                    .map(role ->
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
                    )
                    .collect(Collectors.toList());
        });

        return converter;
    }

    /**
     * Configure les règles CORS autorisant le frontend Angular ({@code localhost:4200})
     * à accéder à l'API REST du service.
     * <p>
     * Méthodes HTTP autorisées : GET, POST, PUT, PATCH, DELETE, OPTIONS.<br>
     * Les en-têtes arbitraires et les credentials (cookies, Authorization) sont acceptés.
     * </p>
     *
     * @return la source de configuration CORS enregistrée pour toutes les routes ({@code /**})
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}