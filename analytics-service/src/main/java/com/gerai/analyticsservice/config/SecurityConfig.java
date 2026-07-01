package com.gerai.analyticsservice.config;

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
 * Configuration de la sécurité Spring Security pour l'analytics-service.
 *
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * {@code @EnableWebSecurity} : active la configuration de sécurité web Spring Security.
 * {@code @EnableMethodSecurity} : active les annotations de sécurité au niveau des méthodes
 * ({@code @PreAuthorize}, {@code @PostAuthorize}, etc.).
 *
 * Politique d'accès :
 * <ul>
 *   <li>/internal/** : accès public (communication inter-services sans JWT)</li>
 *   <li>/api/analytics/**, /api/reports/**, /api/admin/** : authentification requise</li>
 * </ul>
 * Les rôles Keycloak ({@code realm_access.roles}) sont convertis en {@code ROLE_XXX}
 * via le convertisseur JWT personnalisé.
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Définit la chaîne de filtres de sécurité HTTP.
     * Configure : désactivation CSRF, politique CORS, règles d'autorisation
     * et le serveur de ressources OAuth2 basé sur JWT Keycloak.
     *
     * @param http le constructeur de configuration HTTP de Spring Security
     * @return la chaîne de filtres de sécurité configurée
     * @throws Exception en cas d'erreur de configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/analytics/**").authenticated()
                        .requestMatchers("/api/reports/**").authenticated()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
                );
        return http.build();
    }

    /**
     * Convertit realm_access.roles → ROLE_RH, ROLE_CHEF, ROLE_ADMIN, ROLE_EMPLOYE
     * pour que @PreAuthorize("hasRole('RH')") fonctionne.
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
     * Configure la source CORS pour autoriser les requêtes depuis le frontend Angular
     * (http://localhost:4200). Autorise toutes les méthodes HTTP standards et tous les en-têtes,
     * avec support des credentials.
     *
     * @return la source de configuration CORS enregistrée sur tous les chemins ({@code /**})
     */
    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:4200"));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}