package com.gerai.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

/**
 * Configuration de la sécurité HTTP du microservice chat-service.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * <br>
 * {@code @EnableWebSecurity} : active le support de Spring Security et remplace
 * la configuration de sécurité automatique par cette classe personnalisée.
 * <p>
 * Stratégie d'authentification : OAuth2 Resource Server avec validation JWT (Keycloak).
 * Les connexions WebSocket ({@code /ws/**}) sont ouvertes sans authentification HTTP
 * car elles sont sécurisées au niveau STOMP par {@link WebSocketAuthChannelInterceptor}.
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Exclut complètement les ressources statiques (fichiers uploadés) de la chaîne
     * de filtres Spring Security — aucun traitement JWT, aucune authentification,
     * aucune erreur 401, quels que soient les en-têtes de la requête.
     *
     * @return le personnalisateur de sécurité web configuré pour ignorer {@code /uploads/**}
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers("/uploads/**");
    }

    /**
     * Définit la chaîne de filtres de sécurité principale.
     * <ul>
     *   <li>CORS activé avec la configuration par défaut (voir {@link WebConfig}).</li>
     *   <li>CSRF désactivé (API stateless JWT).</li>
     *   <li>Sessions sans état ({@code STATELESS}).</li>
     *   <li>{@code /ws/**} et {@code /h2-console/**} accessibles sans authentification.</li>
     *   <li>Toutes les autres requêtes nécessitent un JWT valide (validé par Keycloak).</li>
     *   <li>La console H2 autorise les iframes de même origine.</li>
     * </ul>
     *
     * @param http le constructeur de configuration de sécurité HTTP
     * @return la chaîne de filtres de sécurité construite
     * @throws Exception en cas d'erreur de configuration Spring Security
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        // Nécessaire pour H2 console (iframe)
        http.headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * Convertisseur d'authentification JWT qui extrait les rôles Keycloak
     * depuis le claim {@code realm_access.roles} et les transforme en
     * {@link org.springframework.security.core.GrantedAuthority} Spring Security
     * avec le préfixe {@code ROLE_}.
     *
     * @return le convertisseur JWT configuré pour lire les rôles Keycloak realm
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // On récupère l'objet "realm_access"
            java.util.Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || realmAccess.isEmpty()) {
                return java.util.Collections.emptyList();
            }

            // On récupère la liste des rôles à l'intérieur
            java.util.Collection<String> roles = (java.util.Collection<String>) realmAccess.get("roles");
            if (roles == null) {
                return java.util.Collections.emptyList();
            }

            // On transforme chaque rôle en GrantedAuthority avec le préfixe ROLE_
            return roles.stream()
                    .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))
                    .collect(java.util.stream.Collectors.toList());
        });

        return converter;
    }
}