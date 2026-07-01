package com.gerai_backend.gerai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

/**
 * Configuration principale de la sécurité HTTP du microservice {@code employe-service}.
 *
 * <p>@Configuration + @EnableWebSecurity : active la configuration de sécurité Spring Security
 * et remplace la configuration automatique par défaut.</p>
 *
 * <p>Stratégie appliquée :</p>
 * <ul>
 *   <li>Sessions stateless (JWT uniquement — pas de session HTTP côté serveur)</li>
 *   <li>CSRF désactivé (API REST consommée par Angular via Bearer token)</li>
 *   <li>CORS configuré pour autoriser {@code http://localhost:4200}</li>
 *   <li>Ressource OAuth2 sécurisée par validation JWT via Keycloak JWKS</li>
 *   <li>Rôles extraits du JWT par {@link KeycloakJwtRoleConverter}</li>
 * </ul>
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final KeycloakJwtRoleConverter keycloakJwtRoleConverter;

    /**
     * Injecte le convertisseur de rôles JWT Keycloak.
     *
     * @param keycloakJwtRoleConverter le convertisseur chargé d'extraire les rôles depuis le JWT
     */
    public SecurityConfig(KeycloakJwtRoleConverter keycloakJwtRoleConverter) {
        this.keycloakJwtRoleConverter = keycloakJwtRoleConverter;
    }

    /**
     * Définit la chaîne de filtres de sécurité HTTP : règles d'autorisation par rôle,
     * configuration OAuth2 Resource Server JWT et politique CORS/CSRF.
     *
     * @param http le builder de configuration de sécurité Spring Security
     * @return la {@link SecurityFilterChain} construite
     * @throws Exception si la configuration de sécurité échoue
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(keycloakJwtRoleConverter);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
                        .requestMatchers(HttpMethod.GET, "/employe/profil/photo/**")
                        .permitAll()

                        .requestMatchers(HttpMethod.GET,    "/employees/**", "/employes/**")
                        .hasAnyRole("admin", "RH", "chef", "CHEF", "ADMIN", "ADMIN_RH", "EMPLOYE", "employe", "employees:read")

                        .requestMatchers(HttpMethod.POST,   "/employees/**", "/employes/**")
                        .hasAnyRole("admin", "RH", "ADMIN", "ADMIN_RH", "employees:write")

                        .requestMatchers(HttpMethod.PUT,    "/employees/**", "/employes/**")
                        .hasAnyRole("admin", "RH", "ADMIN", "ADMIN_RH", "employees:update")

                        .requestMatchers(HttpMethod.DELETE, "/employees/**", "/employes/**")
                        .hasAnyRole("admin", "RH", "ADMIN", "ADMIN_RH", "employees:delete")

                        .requestMatchers("/admin/keycloak/**")
                        .hasAnyRole("admin", "ADMIN", "admin_rh", "ADMIN_RH")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                );

        return http.build();
    }

    /**
     * Définit la configuration CORS autorisée pour le microservice.
     *
     * <p>Autorise uniquement l'origine Angular ({@code http://localhost:4200}) avec
     * les méthodes HTTP standard et tous les en-têtes, avec support des credentials.</p>
     *
     * @return la source de configuration CORS appliquée à toutes les routes ({@code /**})
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
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