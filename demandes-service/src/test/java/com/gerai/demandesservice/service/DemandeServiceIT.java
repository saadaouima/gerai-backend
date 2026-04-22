package com.gerai.demandesservice.service;

import com.gerai.demandesservice.dto.DemandeRequest;
import com.gerai.demandesservice.dto.DemandeResponse;
import com.gerai.demandesservice.dto.NotificationMessage;
import com.gerai.demandesservice.dto.ValidationRequest;
import com.gerai.demandesservice.model.*;
import com.gerai.demandesservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Tests d'intégration de DemandeService.
 *
 * Prérequis dans application-test.properties :
 *   spring.datasource.url=jdbc:h2:mem:gerai-test;DB_CLOSE_DELAY=-1
 *   spring.datasource.driver-class-name=org.h2.Driver
 *   spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
 *   spring.jpa.hibernate.ddl-auto=create-drop
 *   spring.security.oauth2.resourceserver.jwt.issuer-uri=  (vide — désactivé)
 *
 * EmployeeRepository est mocké car il pointe vers GERAI_USER.EMPLOYEES
 * (non disponible en test H2). Tous les autres repos (LEAVE_REQUESTS,
 * TRAINING_REQUESTS…) sont des vraies tables H2.
 *
 * Workflow testé :
 *   creerDemande() → getMesDemandes() → getDemandesEnAttenteChef()
 *   → valider(VALIDEE_CHEF) → getDemandesEnAttenteRh()
 *   → valider(VALIDEE_RH) → statut final VALIDE_RH en base
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemandeServiceIT {

    @Autowired DemandeService            service;
    @Autowired LeaveRequestRepository    leaveRepo;
    @Autowired TrainingRequestRepository trainingRepo;
    @Autowired LoanRequestRepository     loanRepo;
    @Autowired DocumentRequestRepository documentRepo;
    @Autowired AuthorizationRequestRepository authRepo;

    /* Mocké car dépend de la base Oracle GERAI_USER.EMPLOYEES */
    @MockitoBean EmployeeRepository employeeRepo;
    @MockitoBean KafkaTemplate<String, NotificationMessage> kafkaTemplate;

    /* ── Constantes ─────────────────────────────────── */
    private static final Long   EMP_ID     = 10L;
    private static final Long   MANAGER_ID = 20L;
    private static final Long   RH_ID      = 30L;
    private static final String EMP_SUB    = "kc-uuid-emp-010";
    private static final String MGR_SUB    = "kc-uuid-chef-020";
    private static final String EMP_NOM    = "Nour Bousaidi";

    @BeforeEach
    void setupEmployeeRepo() {
        // Simule la résolution d'identité depuis le JWT pour les 3 rôles
        lenient().when(employeeRepo.findEmployeeIdByKeycloakSub(EMP_SUB)).thenReturn(EMP_ID);
        lenient().when(employeeRepo.findEmployeeIdByKeycloakSub(MGR_SUB)).thenReturn(MANAGER_ID);
        lenient().when(employeeRepo.findEmployeeIdByKeycloakSub("kc-uuid-rh-030")).thenReturn(RH_ID);

        lenient().when(employeeRepo.findFullNameByEmployeeId(EMP_ID)).thenReturn(EMP_NOM);
        lenient().when(employeeRepo.findFullNameByEmployeeId(MANAGER_ID)).thenReturn("Sami Trabelsi");

        lenient().when(employeeRepo.findManagerKeycloakSubByEmployeeId(EMP_ID)).thenReturn(MGR_SUB);
        lenient().when(employeeRepo.findManagerEmailByEmployeeId(EMP_ID)).thenReturn("chef@gerai.tn");

        // findById pour notifierEmploye()
        EmployeeRef empRef = mockEmployeeRef(EMP_ID, EMP_SUB, "nour@gerai.tn");
        lenient().when(employeeRepo.findById(EMP_ID)).thenReturn(Optional.of(empRef));
    }

    /* ══════════════════════════════════════════════════
       TEST 1 — Workflow complet CONGÉ (2 niveaux)
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("Workflow complet CONGÉ : création → chef valide → RH valide → VALIDE_RH en base")
    void workflow_conge_deuxNiveaux() {

        // ── 1. Employé crée la demande ────────────────
        DemandeRequest req = DemandeRequest.builder()
                .type(TypeDemande.CONGE)
                .leaveTypeId(1L)
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 5))
                .daysCount(BigDecimal.valueOf(5))
                .reason("Vacances d'été")
                .build();

        DemandeResponse created = service.creerDemande(req, authFor(EMP_SUB, "employe"));

        assertThat(created.getRequestId()).isNotNull();
        assertThat(created.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);
        assertThat(created.getStatusOracle()).isEqualTo("EN_ATTENTE");

        Long requestId = created.getRequestId();

        // ── 2. Demande bien dans getMesDemandes() ─────
        List<DemandeResponse> mesDemandes = service.getMesDemandes(authFor(EMP_SUB, "employe"));
        assertThat(mesDemandes).extracting("requestId").contains(requestId);

        // ── 3. Chef voit la demande en attente ────────
        // On stub findByManagerAndStatus pour retourner la demande créée
        LeaveRequest congeEnBase = leaveRepo.findById(requestId).orElseThrow();
        lenient().when(leaveRepo.findByManagerAndStatus(MANAGER_ID, "EN_ATTENTE"))
                .thenReturn(List.of(congeEnBase));
        lenient().when(trainingRepo.findByManagerAndStatus(MANAGER_ID, "EN_ATTENTE"))
                .thenReturn(List.of());
        lenient().when(authRepo.findByManagerAndStatus(MANAGER_ID, "EN_ATTENTE"))
                .thenReturn(List.of());

        List<DemandeResponse> enAttenteChef =
                service.getDemandesEnAttenteChef(authFor(MGR_SUB, "chef"));
        assertThat(enAttenteChef).extracting("requestId").contains(requestId);

        // ── 4. Chef valide (niveau 1) ─────────────────
        DemandeResponse apresChef = service.valider(
                requestId, TypeDemande.CONGE,
                new ValidationRequest(StatutDemande.VALIDEE_CHEF, "Planning OK"),
                authFor(MGR_SUB, "chef"));

        assertThat(apresChef.getStatut()).isEqualTo(StatutDemande.VALIDEE_CHEF);
        assertThat(apresChef.getStatusOracle()).isEqualTo("VALIDE_CHEF");

        // Vérification directe en base H2
        LeaveRequest apresChefDb = leaveRepo.findById(requestId).orElseThrow();
        assertThat(apresChefDb.getStatus()).isEqualTo("VALIDE_CHEF");
        assertThat(apresChefDb.getApprovedBy()).isEqualTo(MANAGER_ID);
        assertThat(apresChefDb.getApprovedAt()).isNotNull();

        // ── 5. RH voit la demande (VALIDE_CHEF) ───────
        List<DemandeResponse> enAttenteRh = service.getDemandesEnAttenteRh();
        assertThat(enAttenteRh).extracting("requestId").contains(requestId);

        // ── 6. RH valide (niveau final) ───────────────
        DemandeResponse apresRh = service.valider(
                requestId, TypeDemande.CONGE,
                new ValidationRequest(StatutDemande.VALIDEE_RH, "Accordé"),
                authFor("kc-uuid-rh-030", "rh"));

        assertThat(apresRh.getStatut()).isEqualTo(StatutDemande.VALIDEE_RH);
        assertThat(apresRh.getStatusOracle()).isEqualTo("VALIDE_RH");

        // ── 7. Vérification finale en base H2 ─────────
        LeaveRequest final_ = leaveRepo.findById(requestId).orElseThrow();
        assertThat(final_.getStatus()).isEqualTo("VALIDE_RH");
        assertThat(final_.getApprovedBy()).isEqualTo(RH_ID);
    }

    /* ══════════════════════════════════════════════════
       TEST 2 — Workflow PRÊT (1 seul niveau, directement RH)
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("Workflow PRÊT : création → RH approuve directement → APPROUVE en base")
    void workflow_pret_niveauUnique() {

        DemandeRequest req = DemandeRequest.builder()
                .type(TypeDemande.PRET)
                .amount(BigDecimal.valueOf(5000))
                .currency("TND")
                .durationMonths(24)
                .reason("Achat véhicule")
                .build();

        DemandeResponse created = service.creerDemande(req, authFor(EMP_SUB, "employe"));
        Long requestId = created.getRequestId();

        assertThat(created.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);

        // RH approuve directement
        DemandeResponse approved = service.valider(
                requestId, TypeDemande.PRET,
                new ValidationRequest(StatutDemande.VALIDEE_RH, "Accordé"),
                authFor("kc-uuid-rh-030", "rh"));

        assertThat(approved.getStatut()).isEqualTo(StatutDemande.VALIDEE_RH);
        assertThat(approved.getStatusOracle()).isEqualTo("APPROUVE");

        LoanRequest inDb = loanRepo.findById(requestId).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo("APPROUVE");
        assertThat(inDb.getApprovedBy()).isEqualTo(RH_ID);
        assertThat(inDb.getApprovedAt()).isNotNull();
    }

    /* ══════════════════════════════════════════════════
       TEST 3 — Workflow REFUS par Chef
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("Workflow REFUS : Chef refuse → REFUSE en base, pas de passage en RH")
    void workflow_refus_parChef() {

        DemandeRequest req = DemandeRequest.builder()
                .type(TypeDemande.CONGE)
                .leaveTypeId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .daysCount(BigDecimal.valueOf(3))
                .reason("Congé familial")
                .build();

        DemandeResponse created = service.creerDemande(req, authFor(EMP_SUB, "employe"));
        Long requestId = created.getRequestId();

        service.valider(
                requestId, TypeDemande.CONGE,
                new ValidationRequest(StatutDemande.REJETEE, "Charge de travail critique"),
                authFor(MGR_SUB, "chef"));

        LeaveRequest inDb = leaveRepo.findById(requestId).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo("REFUSE");
        assertThat(inDb.getRejectionReason()).isEqualTo("Charge de travail critique");

        // Vérifier que la demande N'apparaît PAS dans en-attente-rh
        List<DemandeResponse> enAttenteRh = service.getDemandesEnAttenteRh();
        assertThat(enAttenteRh).extracting("requestId").doesNotContain(requestId);
    }

    /* ══════════════════════════════════════════════════
       TEST 4 — getToutesDemandes() agrège toutes les tables
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("getToutesDemandes() retourne des demandes de tous les types")
    void getToutesDemandes_tousTypes() {

        // Créer une demande de chaque type
        service.creerDemande(DemandeRequest.builder()
                        .type(TypeDemande.CONGE).leaveTypeId(1L)
                        .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1))
                        .daysCount(BigDecimal.ONE).build(),
                authFor(EMP_SUB, "employe"));

        service.creerDemande(DemandeRequest.builder()
                        .type(TypeDemande.PRET).amount(BigDecimal.valueOf(1000))
                        .currency("TND").durationMonths(6).build(),
                authFor(EMP_SUB, "employe"));

        service.creerDemande(DemandeRequest.builder()
                        .type(TypeDemande.FORMATION).trainingTitle("JPA Avancé")
                        .plannedDate(LocalDate.now().plusMonths(1)).durationDays(2).build(),
                authFor(EMP_SUB, "employe"));

        List<DemandeResponse> toutes = service.getToutesDemandes();

        assertThat(toutes).hasSizeGreaterThanOrEqualTo(3);
        assertThat(toutes).extracting("type")
                .contains(TypeDemande.CONGE, TypeDemande.PRET, TypeDemande.FORMATION);

        // Vérifier le tri : plus récent en premier
        for (int i = 0; i < toutes.size() - 1; i++) {
            assertThat(toutes.get(i).getCreatedAt())
                    .isAfterOrEqualTo(toutes.get(i + 1).getCreatedAt());
        }
    }

    /* ── Helpers ──────────────────────────────────── */

    private Authentication authFor(String sub, String role) {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject(sub)
                .claim("email", role + "@gerai.tn")
                // Pas de claim employee_id → forcer le fallback sur sub
                .build();

        JwtAuthenticationToken auth = org.mockito.Mockito.mock(JwtAuthenticationToken.class);
        lenient().when(auth.getToken()).thenReturn(jwt);
        lenient().doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())))
                .when(auth).getAuthorities();
        return auth;
    }

    private EmployeeRef mockEmployeeRef(Long id, String userId, String email) {
        EmployeeRef ref = org.mockito.Mockito.mock(EmployeeRef.class);
        lenient().when(ref.getEmployeeId()).thenReturn(id);
        lenient().when(ref.getUserId()).thenReturn(userId);
        lenient().when(ref.getEmail()).thenReturn(email);
        lenient().when(ref.getFirstName()).thenReturn("Nour");
        lenient().when(ref.getLastName()).thenReturn("Bousaidi");
        return ref;
    }
}