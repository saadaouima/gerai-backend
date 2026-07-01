package com.gerai_backend.gerai.security;

import com.gerai_backend.gerai.config.KeycloakJwtRoleConverter;
import com.gerai_backend.gerai.config.SecurityConfig;
import com.gerai_backend.gerai.controllers.EmployeeController;
import com.gerai_backend.gerai.repositories.EmployeeRepository;
import com.gerai_backend.gerai.services.EmployeeService;
import com.gerai_backend.gerai.dto.CreateEmployeeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de sécurité négatifs et positifs du SecurityFilterChain de l'employe-service.
 *
 * Approche : @WebMvcTest charge uniquement la couche HTTP + SecurityConfig.
 * Le post-processeur jwt() injecte un JwtAuthenticationToken avec des autorités
 * prédéfinies, sans passer par le JwtDecoder ni KeycloakJwtRoleConverter.
 * Cela permet de tester les règles authorizeHttpRequests() de manière isolée.
 */
@WebMvcTest(
    value = EmployeeController.class,
    excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    }
)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class})
@TestPropertySource(properties = {
    // Client ID requis par KeycloakJwtRoleConverter (@Value)
    "spring.security.oauth2.client.registration.keycloak.client-id=grh-backend",
    // URI factice — JwtDecoder mocké, aucune connexion réseau effectuée
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/fake"
})
class EmployeeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // Remplace le JwtDecoder auto-configuré : évite toute connexion réseau vers Keycloak
    @MockBean private JwtDecoder     jwtDecoder;

    // Dépendances du EmployeeController
    @MockBean private EmployeeService    employeeService;
    @MockBean private EmployeeRepository employeeRepository;
    @MockBean private JdbcTemplate       jdbcTemplate;

    // ─── Test 1 : 401 — requête sans token ───────────────────────────────────

    @Test
    @DisplayName("1 — GET /employees/1 sans token → 401 Unauthorized")
    void whenNoToken_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/employees/1"))
            .andExpect(status().isUnauthorized());
    }

    // ─── Test 2 : 403 — EMPLOYE tente de créer un employé ───────────────────

    @Test
    @DisplayName("2 — POST /employees avec rôle EMPLOYE → 403 Forbidden")
    void whenEmployeCreatesEmployee_thenForbidden() throws Exception {
        mockMvc.perform(post("/employees")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYE")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"Test\",\"lastName\":\"User\"}"))
            .andExpect(status().isForbidden());
    }

    // ─── Test 3 : 403 — EMPLOYE tente de supprimer un employé ───────────────

    @Test
    @DisplayName("3 — DELETE /employees/1 avec rôle EMPLOYE → 403 Forbidden")
    void whenEmployeDeletesEmployee_thenForbidden() throws Exception {
        mockMvc.perform(delete("/employees/1")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYE"))))
            .andExpect(status().isForbidden());
    }

    // ─── Test 4 : 403 — EMPLOYE tente d'accéder à l'API admin Keycloak ──────

    @Test
    @DisplayName("4 — POST /admin/keycloak/users avec rôle EMPLOYE → 403 Forbidden")
    void whenEmployeAccessesAdminKeycloak_thenForbidden() throws Exception {
        mockMvc.perform(post("/admin/keycloak/users")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYE")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isForbidden());
    }

    // ─── Test 5 : 201 — ADMIN_RH a accès à POST /employees ──────────────────

    @Test
    @DisplayName("5 — POST /employees avec rôle ADMIN_RH → 201 Created (filtre de sécurité passé)")
    void whenAdminRhCreatesEmployee_thenCreated() throws Exception {
        // Le service est mocké pour retourner une réponse minimale valide
        when(employeeService.createEmployee(any()))
            .thenReturn(new CreateEmployeeResponse());

        mockMvc.perform(post("/employees")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN_RH")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"Nour\",\"lastName\":\"Admin\","
                + "\"email\":\"nour@test.tn\",\"hireDate\":\"2024-01-01\","
                + "\"deptId\":1,\"positionId\":1}"))
            .andExpect(status().isCreated());
    }
}
