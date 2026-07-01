package com.gerai.notificationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration du décodeur JWT Keycloak pour le microservice notification-service.
 * <p>
 * {@code @Configuration} : indique à Spring que cette classe fournit des définitions
 * de beans à intégrer dans le contexte de l'application.
 * </p>
 * <p>
 * Déclare un {@link JwtDecoder} basé sur Nimbus qui récupère les clés publiques
 * Keycloak via l'URI JWK Set configurée dans {@code application.properties}.
 * Des timeouts de connexion et de lecture de 4 secondes sont appliqués pour
 * éviter tout blocage au démarrage si Keycloak est lent à répondre.
 * </p>
 *
 * @since 1.0
 */
@Configuration
public class JwtDecoderConfig {

    /**
     * Crée un {@link JwtDecoder} Nimbus configuré avec un {@link RestTemplate}
     * à timeouts courts pour récupérer les clés JWK depuis Keycloak.
     *
     * @param jwkSetUri URI de l'endpoint JWK Set Keycloak, injectée depuis
     *                  {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}
     * @return un {@link JwtDecoder} prêt à valider les tokens JWT émis par Keycloak
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
