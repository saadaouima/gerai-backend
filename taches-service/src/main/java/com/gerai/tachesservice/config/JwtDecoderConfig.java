package com.gerai.tachesservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration du décodeur JWT pour la validation des tokens Keycloak.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring,
 * remplaçant la configuration automatique de Spring Security pour le décodeur JWT.
 * <p>
 * Cette classe expose un {@link JwtDecoder} personnalisé basé sur
 * {@link NimbusJwtDecoder} avec des timeouts explicites afin d'éviter
 * un blocage indéfini lors de la récupération des clés publiques JWKS
 * depuis Keycloak (utile en environnement de développement ou réseau instable).
 *
 * @since 1.0
 */
@Configuration
public class JwtDecoderConfig {

    /**
     * Crée un décodeur JWT configuré avec des timeouts réseau explicites
     * pour la récupération des clés publiques JWKS depuis Keycloak.
     * <p>
     * Le décodeur valide la signature, l'expiration et l'émetteur de chaque
     * token JWT reçu par les endpoints sécurisés du service.
     *
     * @param jwkSetUri URI du JWKS Keycloak, injectée depuis
     *                  {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}
     * @return un {@link JwtDecoder} Nimbus configuré avec un timeout de 4 secondes
     *         en connexion et en lecture
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4_000);
        factory.setReadTimeout(4_000);

        RestTemplate rest = new RestTemplate(factory);
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .restOperations(rest)
                .build();
    }
}
