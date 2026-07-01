package com.gerai.tachesservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Configuration Feign pour la propagation du JWT entre microservices.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * <p>
 * Ce bean est référencé dans l'annotation {@code @FeignClient} de {@link com.gerai.tachesservice.client.ProjetClient}
 * via le paramètre {@code configuration = FeignConfig.class}. Il est donc appliqué
 * uniquement aux appels Feign de ce client spécifique.
 * <p>
 * Chaque microservice possède sa propre copie de cette configuration, conformément
 * au principe d'isolation des microservices (pas de dépendance partagée).
 *
 * @since 1.0
 */
@Configuration
public class FeignConfig {

    /**
     * Crée un intercepteur Feign qui injecte le token JWT de l'utilisateur connecté
     * dans l'en-tête {@code Authorization} de chaque requête sortante vers projets-service.
     * <p>
     * Le token est extrait du {@code SecurityContextHolder}, ce qui garantit que
     * les droits d'accès de l'utilisateur original sont respectés par projets-service
     * (filtrage des projets selon le rôle CHEF, ADMIN, etc.).
     *
     * @return l'intercepteur de requêtes Feign qui ajoute le Bearer token
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                requestTemplate.header("Authorization", "Bearer " + jwt.getTokenValue());
            }
        };
    }
}