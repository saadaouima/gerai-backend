package com.gerai.projetsservice.config;

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
 * Définit un {@link JwtDecoder} basé sur Nimbus qui récupère les clés publiques
 * de Keycloak via l'URI JWKS configurée. Un {@link org.springframework.http.client.SimpleClientHttpRequestFactory}
 * avec timeouts courts (4 s) est utilisé pour éviter les blocages lors du démarrage
 * si Keycloak n'est pas encore disponible.
 * </p>
 * <p>
 * {@code @Configuration} : fournit ce bean au contexte Spring.
 * </p>
 *
 * @since 1.0
 */
@Configuration
public class JwtDecoderConfig {

    /**
     * Crée le décodeur JWT Nimbus configuré avec l'URI JWKS de Keycloak.
     * <p>
     * Applique un timeout de connexion et de lecture de 4 secondes pour
     * les requêtes de récupération des clés publiques.
     * </p>
     *
     * @param jwkSetUri URI de l'endpoint JWKS Keycloak (ex. {@code http://keycloak:8080/realms/synapse/protocol/openid-connect/certs})
     * @return le décodeur JWT prêt à l'emploi
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
