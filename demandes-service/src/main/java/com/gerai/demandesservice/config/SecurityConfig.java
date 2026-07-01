package com.gerai.demandesservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de la sécurité HTTP du demandes-service.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * <p>
 * {@code @EnableWebSecurity} : active la configuration de sécurité web Spring Security
 * et désactive la configuration automatique par défaut.
 * <p>
 * {@code @EnableMethodSecurity} : active la sécurité au niveau des méthodes,
 * ce qui rend opérationnelles les annotations {@code @PreAuthorize("hasRole('...')")}
 * placées sur les méthodes des contrôleurs.
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Indispensable pour que @PreAuthorize("hasRole('...')") fonctionne
public class SecurityConfig {

    /**
     * Configure la chaîne de filtres de sécurité HTTP.
     * <p>
     * {@code @Bean} : expose la {@link SecurityFilterChain} comme bean Spring.
     * <ul>
     *   <li>CSRF désactivé (API REST stateless avec JWT).</li>
     *   <li>CORS activé avec la configuration par défaut (respecte les {@code @CrossOrigin} des contrôleurs).</li>
     *   <li>Toutes les requêtes doivent être authentifiées.</li>
     *   <li>Serveur de ressources OAuth2 configuré pour valider les tokens JWT Keycloak.</li>
     * </ul>
     *
     * @param http le constructeur de configuration HTTP Spring Security
     * @return la chaîne de filtres configurée
     * @throws Exception si la configuration de sécurité échoue
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // On active le support CORS pour que les @CrossOrigin de tes Controllers soient respectés
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/demandes/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    /**
     * Crée le convertisseur d'authentification JWT qui extrait les rôles Keycloak.
     * <p>
     * Utilise {@link KeycloakRoleConverter} pour transformer les rôles du claim
     * {@code realm_access} en autorités Spring Security ({@code ROLE_CHEF},
     * {@code ROLE_EMPLOYE}, etc.).
     *
     * @return le convertisseur d'authentification JWT configuré
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }
}