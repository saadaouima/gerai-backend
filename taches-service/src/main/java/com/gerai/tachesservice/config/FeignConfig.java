package com.gerai.tachesservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Configuration Feign pour taches-service.
 *
 * Propage automatiquement le JWT Bearer de l'utilisateur connecté
 * vers projets-service lors des appels via ProjetClient.
 *
 * Copié depuis projets-service/FeignConfig — strictement identique.
 * (Chaque service garde sa propre copie : isolation microservices.)
 */
@Configuration
public class FeignConfig {

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