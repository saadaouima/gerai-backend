package com.gerai.demandesservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration Spring du décodeur JWT pour le serveur de ressources OAuth2.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring,
 * chargée au démarrage du contexte applicatif.
 * <p>
 * Cette classe crée un {@link JwtDecoder} basé sur la clé publique Keycloak
 * récupérée via l'endpoint JWK Set. Un timeout court (4 s) est appliqué
 * afin d'éviter des blocages au démarrage si Keycloak est momentanément indisponible.
 *
 * @since 1.0
 */
@Configuration
public class JwtDecoderConfig {

    /**
     * Crée et expose le {@link JwtDecoder} utilisé par Spring Security
     * pour valider les tokens JWT émis par Keycloak.
     * <p>
     * {@code @Bean} : enregistre cette méthode comme fournisseur de bean Spring.
     * Un {@link org.springframework.web.client.RestTemplate} avec timeout de 4 s
     * est utilisé pour récupérer les clés publiques depuis l'URI JWK Set.
     *
     * @param jwkSetUri URI de l'endpoint JWK Set de Keycloak, injectée depuis
     *                  {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}
     * @return un {@link JwtDecoder} configuré avec le {@link org.springframework.web.client.RestTemplate} personnalisé
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
