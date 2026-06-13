package com.gerai_backend.gerai.services;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final Keycloak keycloak;

    @Value("${keycloak.target-realm}")
    private String targetRealm;

    public record KeycloakProvisionResult(String userId, String username, boolean isNew) {}

    /**
     * Creates a Keycloak user (or reuses one if the email already exists).
     * Returns the Keycloak UUID and whether the account was newly created.
     */
    public KeycloakProvisionResult provisionKeycloakUser(String username, String email,
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
        if (response.getStatus() == 409) {
            // Conflict on email or username — find whichever exists and reuse
            String existingId = findUserIdByEmail(email);
            if (existingId == null) {
                existingId = findUserIdByUsername(username);
            }
            if (existingId == null) {
                throw new RuntimeException(
                        "Keycloak conflict on email/username for " + email
                                + " but user not found in realm — check Keycloak admin console."
                );
            }
            resetPassword(existingId, initialPassword);
            String existingUsername = getActualUsername(usersResource, existingId, username);
            return new KeycloakProvisionResult(existingId, existingUsername, false);
        }

        if (response.getStatus() != 201) {
            String errorBody = response.readEntity(String.class);
            throw new RuntimeException(
                    "Failed to create Keycloak user. Status: "
                            + response.getStatus() + " | Body: " + errorBody
            );
        }

        // 6. Extract the Keycloak userId from Location header
        String locationPath = response.getLocation().getPath();
        String keycloakUserId = locationPath.substring(locationPath.lastIndexOf('/') + 1);

        String actualUsername = getActualUsername(usersResource, keycloakUserId, username);
        return new KeycloakProvisionResult(keycloakUserId, actualUsername, true);
    }

    private String getActualUsername(UsersResource usersResource, String userId, String fallback) {
        try {
            UserRepresentation rep = usersResource.get(userId).toRepresentation();
            String name = rep.getUsername();
            return (name != null && !name.isBlank()) ? name : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public void resetPassword(String keycloakUserId, String newPassword) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(newPassword);
        cred.setTemporary(true);
        keycloak.realm(targetRealm).users().get(keycloakUserId).resetPassword(cred);
    }

    public String findUserIdByEmail(String email) {
        List<UserRepresentation> users = keycloak.realm(targetRealm)
                .users()
                .search(null, null, null, email, 0, 2);
        if (users == null) return null;
        return users.stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .map(UserRepresentation::getId)
                .findFirst()
                .orElse(null);
    }

    public String findUserIdByUsername(String username) {
        List<UserRepresentation> users = keycloak.realm(targetRealm)
                .users()
                .search(username, 0, 2);
        if (users == null) return null;
        return users.stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .map(UserRepresentation::getId)
                .findFirst()
                .orElse(null);
    }

    // ── Internal role names that should never be shown in the UI ─────────
    private static final Set<String> SYSTEM_ROLES = Set.of(
            "offline_access", "uma_authorization", "create-realm"
    );

    /** Returns the full access snapshot for a Keycloak user as a map ready for JSON serialization. */
    public Map<String, Object> getUserAccess(String keycloakUserId) {
        RealmResource realm = keycloak.realm(targetRealm);
        UserResource  ur    = realm.users().get(keycloakUserId);
        UserRepresentation rep = ur.toRepresentation();

        List<String> roles = ur.roles().realmLevel().listAll().stream()
                .map(RoleRepresentation::getName)
                .filter(n -> !SYSTEM_ROLES.contains(n) && !n.startsWith("default-roles"))
                .collect(Collectors.toList());

        List<String> groups = ur.groups().stream()
                .map(GroupRepresentation::getPath)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username",       rep.getUsername());
        result.put("accountEnabled", Boolean.TRUE.equals(rep.isEnabled()));
        result.put("emailVerified",  Boolean.TRUE.equals(rep.isEmailVerified()));
        result.put("roles",          roles);
        result.put("groups",         groups);
        return result;
    }

    /** Replaces all realm-level roles for a user with the given list. */
    public void updateRealmRoles(String keycloakUserId, List<String> newRoleNames) {
        RealmResource realm    = keycloak.realm(targetRealm);
        var           roleMgr  = realm.users().get(keycloakUserId).roles().realmLevel();

        List<RoleRepresentation> current = roleMgr.listAll();
        if (!current.isEmpty()) roleMgr.remove(current);

        List<RoleRepresentation> toAdd = newRoleNames.stream()
                .map(name -> {
                    try { return realm.roles().get(name).toRepresentation(); }
                    catch (Exception e) { log.warn("Keycloak role '{}' not found", name); return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!toAdd.isEmpty()) roleMgr.add(toAdd);
    }

    /** Replaces all group memberships for a user with the given paths (e.g. "/Informatique"). */
    public void updateGroups(String keycloakUserId, List<String> groupPaths) {
        RealmResource realm      = keycloak.realm(targetRealm);
        UserResource  userRes    = realm.users().get(keycloakUserId);

        userRes.groups().forEach(g -> {
            try { userRes.leaveGroup(g.getId()); } catch (Exception ignored) {}
        });

        for (String path : groupPaths) {
            realm.groups().groups().stream()
                    .filter(g -> path.equals(g.getPath()))
                    .findFirst()
                    .ifPresent(g -> {
                        try { userRes.joinGroup(g.getId()); }
                        catch (Exception e) { log.warn("Could not join group {}: {}", path, e.getMessage()); }
                    });
        }
    }

    /** Enables or disables a Keycloak account. */
    public void setAccountEnabled(String keycloakUserId, boolean enabled) {
        UserRepresentation patch = new UserRepresentation();
        patch.setEnabled(enabled);
        keycloak.realm(targetRealm).users().get(keycloakUserId).update(patch);
    }

    /** Generates a random temp password, resets the user's credentials, and returns the plain-text value. */
    public String generateAndResetPassword(String keycloakUserId) {
        String password = generatePassword();
        resetPassword(keycloakUserId, password);
        return password;
    }

    private static final String PWD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";

    private String generatePassword() {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(PWD_CHARS.charAt(rng.nextInt(PWD_CHARS.length())));
        return sb.toString();
    }

    /**
     * Creates any realm roles that don't already exist.
     * Called at dev-seeder startup so that assignRealmRoles never silently skips.
     */
    public void ensureRealmRolesExist(List<String> roleNames) {
        RealmResource realm = keycloak.realm(targetRealm);
        for (String name : roleNames) {
            try {
                realm.roles().get(name).toRepresentation();
                log.debug("[KC] Realm role '{}' already exists", name);
            } catch (Exception e) {
                RoleRepresentation role = new RoleRepresentation();
                role.setName(name);
                realm.roles().create(role);
                log.info("[KC] Created realm role '{}'", name);
            }
        }
    }

    /**
     * Ensures the built-in 'roles' client scope is in the default scopes of the given client.
     * Moves the scope from optional → default if needed (Keycloak rejects adding to default
     * while it is already in optional).
     */
    public void ensureRolesScopeOnClient(String clientIdStr) {
        RealmResource realm = keycloak.realm(targetRealm);

        List<ClientRepresentation> clients = realm.clients().findByClientId(clientIdStr);
        if (clients.isEmpty()) {
            log.warn("[KC] Client '{}' not found — cannot configure roles scope", clientIdStr);
            return;
        }
        String clientUUID = clients.get(0).getId();
        var clientRes = realm.clients().get(clientUUID);

        boolean alreadyDefault = clientRes.getDefaultClientScopes()
                .stream().anyMatch(s -> "roles".equals(s.getName()));
        if (alreadyDefault) {
            log.debug("[KC] 'roles' scope already in default scopes for '{}'", clientIdStr);
            return;
        }

        ClientScopeRepresentation rolesScope = realm.clientScopes().findAll()
                .stream().filter(s -> "roles".equals(s.getName())).findFirst().orElse(null);
        if (rolesScope == null) {
            log.warn("[KC] Built-in 'roles' client scope not found in realm '{}'", targetRealm);
            return;
        }

        // Must remove from optional first — Keycloak 404/409s if you try to add to default
        // while the scope is already in the optional list.
        try {
            boolean isOptional = clientRes.getOptionalClientScopes()
                    .stream().anyMatch(s -> rolesScope.getId().equals(s.getId()));
            if (isOptional) {
                clientRes.removeOptionalClientScope(rolesScope.getId());
                log.info("[KC] Removed 'roles' from optional scopes for client '{}'", clientIdStr);
            }
        } catch (Exception e) {
            log.debug("[KC] Could not inspect optional scopes for '{}': {}", clientIdStr, e.getMessage());
        }

        clientRes.addDefaultClientScope(rolesScope.getId());
        log.info("[KC] Added 'roles' scope to default scopes for client '{}'", clientIdStr);
    }

    /**
     * Adds an explicit 'oidc-usermodel-realm-role-mapper' protocol mapper to the client.
     * This is a belt-and-suspenders addition on top of the 'roles' client scope: even if scope
     * configuration is wrong, this mapper guarantees realm_access.roles appears in every token
     * issued by this client.
     */
    public void ensureRealmRolesMapper(String clientIdStr) {
        RealmResource realm = keycloak.realm(targetRealm);

        List<ClientRepresentation> clients = realm.clients().findByClientId(clientIdStr);
        if (clients.isEmpty()) {
            log.warn("[KC] Client '{}' not found — cannot add realm roles mapper", clientIdStr);
            return;
        }
        String clientUUID = clients.get(0).getId();
        var pmResource = realm.clients().get(clientUUID).getProtocolMappers();

        List<ProtocolMapperRepresentation> existing;
        try { existing = pmResource.getMappers(); } catch (Exception e) { existing = List.of(); }

        boolean hasMapper = existing != null && existing.stream()
                .anyMatch(m -> "oidc-usermodel-realm-role-mapper".equals(m.getProtocolMapper()));
        if (hasMapper) {
            log.debug("[KC] Realm roles mapper already present for client '{}'", clientIdStr);
            return;
        }

        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("realm-roles");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-usermodel-realm-role-mapper");
        mapper.setConfig(Map.of(
                "multivalued",        "true",
                "access.token.claim", "true",
                "id.token.claim",     "true",
                "claim.name",         "realm_access.roles",
                "jsonType.label",     "String"
        ));

        try (Response r = pmResource.createMapper(mapper)) {
            if (r.getStatus() == 201) {
                log.info("[KC] Created realm roles mapper for client '{}'", clientIdStr);
            } else if (r.getStatus() == 409) {
                log.debug("[KC] Realm roles mapper already existed for client '{}'", clientIdStr);
            } else {
                log.warn("[KC] Unexpected HTTP {} creating realm roles mapper for '{}'", r.getStatus(), clientIdStr);
            }
        } catch (Exception e) {
            log.warn("[KC] Could not create realm roles mapper for '{}': {}", clientIdStr, e.getMessage());
        }
    }

    public void deleteKeycloakUser(String keycloakUserId) {
        var userResource = keycloak.realm(targetRealm).users().get(keycloakUserId);

        // Terminate all active SSO sessions and invalidate refresh tokens first.
        // Access tokens (JWTs) remain valid until their TTL expires (~5-15 min) —
        // this is inherent to stateless JWTs and accepted in standard HR systems.
        try {
            userResource.logout();
            log.info("Keycloak sessions terminated for user {}", keycloakUserId);
        } catch (Exception ex) {
            log.warn("Could not terminate sessions for {} (may have none): {}", keycloakUserId, ex.getMessage());
        }

        userResource.remove();
    }

    /**
     * Like provisionKeycloakUser but without forced password change — for dev seed data only.
     * Returns the Keycloak UUID so the caller can store it in EMPLOYEES.USER_ID.
     */
    public String provisionSeedUser(String username, String email,
                                    String firstName, String lastName,
                                    String password, List<String> roles) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setCredentials(List.of(cred));

        RealmResource realm = keycloak.realm(targetRealm);
        Response response   = realm.users().create(user);

        String keycloakId;
        if (response.getStatus() == 201) {
            String path = response.getLocation().getPath();
            keycloakId  = path.substring(path.lastIndexOf('/') + 1);
        } else if (response.getStatus() == 409) {
            keycloakId = findUserIdByEmail(email);
            if (keycloakId == null) keycloakId = findUserIdByUsername(username);
            if (keycloakId == null)
                throw new RuntimeException("Keycloak conflict on " + email + " but user not found");
            // Reset to known seed password (non-temporary)
            CredentialRepresentation reset = new CredentialRepresentation();
            reset.setType(CredentialRepresentation.PASSWORD);
            reset.setValue(password);
            reset.setTemporary(false);
            realm.users().get(keycloakId).resetPassword(reset);
        } else {
            throw new RuntimeException("Keycloak user creation failed for " + email
                    + ": HTTP " + response.getStatus());
        }

        if (!roles.isEmpty()) assignRealmRoles(keycloakId, roles);
        return keycloakId;
    }

    public void assignRealmRoles(String userId, List<String> roleNames) {
        RealmResource realm = keycloak.realm(targetRealm);
        List<RoleRepresentation> roles = roleNames.stream()
                .map(name -> {
                    try {
                        return realm.roles().get(name).toRepresentation();
                    } catch (Exception e) {
                        log.warn("Keycloak realm role '{}' not found — skipping", name);
                        return null;
                    }
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
        if (!roles.isEmpty()) {
            realm.users().get(userId).roles().realmLevel().add(roles);
            log.info("Assigned roles {} to Keycloak user {}", roleNames, userId);
        }
    }
}
