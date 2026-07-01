package com.gerai_backend.gerai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration du décodeur JWT utilisé par Spring Security pour valider
 * les tokens émis par Keycloak.
 *
 * <p>@Configuration : indique à Spring que cette classe déclare des beans
 * à enregistrer dans le contexte de l'application.</p>
 *
 * <p>Un {@link org.springframework.web.client.RestTemplate} avec timeout personnalisé
 * est injecté dans {@link NimbusJwtDecoder} afin d'éviter un blocage indéfini
 * lors de la récupération des clés publiques JWKS depuis Keycloak.</p>
 *
 * @since 1.0
 */
@Configuration
public class JwtDecoderConfig {

    /**
     * Crée un {@link JwtDecoder} basé sur Nimbus qui valide les tokens JWT
     * en récupérant les clés publiques depuis l'URI JWKS de Keycloak.
     *
     * <p>Les timeouts de connexion et de lecture sont fixés à 4 secondes
     * pour éviter de bloquer indéfiniment le démarrage de l'application.</p>
     *
     * @param jwkSetUri l'URI de l'endpoint JWKS de Keycloak,
     *                  injectée depuis {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}
     * @return un {@link JwtDecoder} configuré avec un {@code RestTemplate} à timeout limité
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
