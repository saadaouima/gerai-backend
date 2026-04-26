package com.gerai_backend.gerai.services;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final Keycloak keycloak;

    @Value("${keycloak.target-realm}")
    private String targetRealm;

    /**
     * Creates a Keycloak user and returns the generated Keycloak userId (UUID)
     */
    public String createKeycloakUser(String username, String email,
                                     String firstName, String lastName,
                                     String initialPassword) {

        // 1. Build credential (temporary = true forces password change on first login)
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(initialPassword);
        credential.setTemporary(true); // ← Forces password change on first login

        // 2. Build user representation
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setCredentials(List.of(credential));

        // 3. Optionally force username change via required actions
        user.setRequiredActions(List.of(
                "UPDATE_PASSWORD"   // force password change
        ));

        // 4. Call Keycloak Admin REST API
        RealmResource realmResource = keycloak.realm(targetRealm);
        UsersResource usersResource = realmResource.users();

        Response response = usersResource.create(user);

        // 5. Handle response
        if (response.getStatus() != 201) {
            String errorBody = response.readEntity(String.class);
            throw new RuntimeException(
                    "Failed to create Keycloak user. Status: "
                            + response.getStatus() + " | Body: " + errorBody
            );
        }

        // 6. Extract the Keycloak userId from Location header
        // Location: http://localhost:8080/admin/realms/hr-realm/users/{id}
        String locationPath = response.getLocation().getPath();
        String keycloakUserId = locationPath.substring(locationPath.lastIndexOf('/') + 1);

        return keycloakUserId; // UUID string
    }

    /**
     * Optional: Delete Keycloak user by ID (for rollback)
     */
    public void deleteKeycloakUser(String keycloakUserId) {
        keycloak.realm(targetRealm)
                .users()
                .get(keycloakUserId)
                .remove();
    }
}
