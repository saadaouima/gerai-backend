package com.gerai.projetsservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Configuration OpenFeign pour les appels inter-services.
 * <p>
 * Injecte automatiquement le token JWT de l'utilisateur authentifié dans
 * l'en-tête {@code Authorization} de chaque requête Feign sortante, permettant
 * ainsi la propagation du contexte de sécurité entre microservices.
 * </p>
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * </p>
 *
 * @since 1.0
 */
@Configuration
public class FeignConfig {

    /**
     * Crée un intercepteur Feign qui propage le JWT de l'utilisateur courant.
     * <p>
     * Extrait le token JWT depuis le {@code SecurityContextHolder} et l'ajoute
     * dans l'en-tête {@code Authorization: Bearer <token>} de chaque requête
     * sortante vers d'autres microservices (ex. employee-service).
     * </p>
     *
     * @return l'intercepteur de requête Feign configuré
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // On vérifie si l'utilisateur est authentifié avec un JWT
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                // On injecte le Token dans le header pour l'appel au service Employe
                requestTemplate.header("Authorization", "Bearer " + jwt.getTokenValue());
            }
        };
    }
}