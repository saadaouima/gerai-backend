package com.gerai_backend.gerai.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration du client d'administration Keycloak utilisé par le microservice
 * pour gérer les utilisateurs, rôles et groupes via l'API Admin REST de Keycloak.
 *
 * <p>@Configuration : déclare le bean {@link Keycloak} (client admin Keycloak)
 * qui sera injecté dans {@link com.gerai_backend.gerai.services.KeycloakUserService}.</p>
 *
 * <p>Les propriétés sont lues depuis {@code application.yml} (préfixe {@code keycloak.*}).</p>
 *
 * @since 1.0
 */
@Configuration
public class KeycloakAdminConfig {

    /** URL du serveur Keycloak (ex. {@code http://localhost:8080}). */
    @Value("${keycloak.server-url}")
    private String serverUrl;

    /** Realm d'administration Keycloak (généralement {@code master}). */
    @Value("${keycloak.admin-realm}")
    private String adminRealm;

    /** Identifiant du client OAuth2 d'administration (ex. {@code admin-cli}). */
    @Value("${keycloak.admin-client-id}")
    private String adminClientId;

    /** Nom d'utilisateur du compte administrateur Keycloak. */
    @Value("${keycloak.admin-username}")
    private String adminUsername;

    /** Mot de passe du compte administrateur Keycloak. */
    @Value("${keycloak.admin-password}")
    private String adminPassword;

    /**
     * Crée et configure le client d'administration Keycloak avec les credentials
     * de l'administrateur, en utilisant le flux {@code password} (Resource Owner Password).
     *
     * @return un client {@link Keycloak} authentifié, prêt à appeler l'API Admin REST
     */
    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(adminRealm)
                .clientId(adminClientId)
                .username(adminUsername)
                .password(adminPassword)
                .grantType(OAuth2Constants.PASSWORD)
                .build();
    }
}
