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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de DemandeService.
 *
 * Ce que chaque test vérifie :
 *   1. creerDemande_conge        → sauvegarde dans leaveRepo + Kafka vers le chef
 *   2. creerDemande_pret         → sauvegarde dans loanRepo + Kafka vers RH (pas de chef)
 *   3. valider_conge_par_chef    → statut Oracle "VALIDE_CHEF" + Kafka vers l'employé
 *   4. valider_conge_par_rh      → statut Oracle "VALIDE_RH" + Kafka vers l'employé
 *   5. valider_conge_refus       → statut Oracle "REFUSE" + Kafka vers l'employé
 *   6. valider_formation_chef    → statut Oracle "APPROUVE_CHEF" pour formation
 *   7. valider_pret_rh           → statut Oracle "APPROUVE" pour prêt
 *   8. valider_autorisation_chef → flux direct APPROUVE (pas d'étape intermédiaire)
 *   9. getMesDemandes            → agrège toutes les tables pour un employé
 *  10. resolveEmployeeId_fallback → fallback sur email si sub Oracle introuvable
 */
@ExtendWith(MockitoExtension.class)
class DemandeServiceTest {

    /* ── Repositories et services mockés ───────────────── */
    @Mock LeaveRequestRepository         leaveRepo;
    @Mock TrainingRequestRepository      trainingRepo;
    @Mock LoanRequestRepository          loanRepo;
    @Mock DocumentRequestRepository      documentRepo;
    @Mock AuthorizationRequestRepository authRepo;
    @Mock EmployeeRepository             employeeRepo;
    @Mock EmployeeInfoHelper             employeeInfoHelper;
    @Mock SalaryValidationService        salaryValidationService;
    @Mock LeaveQuotaService              leaveQuotaService;
    @Mock WorkingDayService              workingDayService;
    @Mock org.springframework.kafka.core.KafkaTemplate<String, com.gerai.demandesservice.dto.NotificationEvent> kafkaTemplate;
    @InjectMocks
    DemandeService service;

    /* ── Constantes réutilisées ──────────────────────── */
    private static final Long   EMP_ID      = 10L;
    private static final Long   MANAGER_ID  = 20L;
    private static final Long   RH_ID       = 30L;
    private static final String EMP_SUB     = "kc-uuid-emp-010";
    private static final String EMP_EMAIL   = "nour@gerai.tn";
    private static final String EMP_NOM     = "Nour Bousaidi";
    private static final String MANAGER_SUB = "kc-uuid-chef-020";
    private static final String MANAGER_EMAIL = "chef@gerai.tn";
    /* ── Helpers : Authentication JWT mockée ──────────── */

    /** Crée un Authentication JWT simulant un EMPLOYE */
    private Authentication authEmploye() {
        return buildAuth(EMP_SUB, EMP_EMAIL, "employe");
    }

    /** Crée un Authentication JWT simulant un CHEF */
    private Authentication authChef() {
        return buildAuth(MANAGER_SUB, MANAGER_EMAIL, "chef");
    }

    /** Crée un Authentication JWT simulant un RH */
    private Authentication authRh() {
        return buildAuth("kc-uuid-rh-030", "rh@gerai.tn", "rh");
    }

    private Authentication buildAuth(String sub, String email, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(sub)
                .claim("email", email)
                .claim("employee_id", role.equals("employe")  ? EMP_ID
                        : role.equals("chef")  ? MANAGER_ID : RH_ID)
                .build();
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        when(auth.getToken()).thenReturn(jwt);
        // Autorités Spring Security (ROLE_CHEF, ROLE_RH, ROLE_EMPLOYE)
        var authority = new org.springframework.security.core.authority
                .SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
        // lenient: getAuthorities() est appelé par valider() mais pas par creerDemande()
        lenient().doReturn(List.of(authority)).when(auth).getAuthorities();
        return auth;
    }

    /* ── Setup commun ────────────────────────────────── */

    @BeforeEach
    void setUp() {
        // resolveEmployeeId() délègue désormais à EmployeeInfoHelper (pas directement au repo).
        // On stubbe les 3 acteurs utilisés dans les tests pour que tryResolveEmployeeId()
        // retourne le bon ID au lieu de null (ce qui rendrait approvedBy = null dans les asserts).
        lenient().when(employeeInfoHelper.findEmployeeIdBySub(EMP_SUB))
                .thenReturn(EMP_ID);
        lenient().when(employeeInfoHelper.findEmployeeIdBySub(MANAGER_SUB))
                .thenReturn(MANAGER_ID);
        lenient().when(employeeInfoHelper.findEmployeeIdBySub("kc-uuid-rh-030"))
                .thenReturn(RH_ID);
        lenient().when(employeeInfoHelper.findEmployeeIdByEmailSafe(EMP_EMAIL))
                .thenReturn(EMP_ID);
        lenient().when(employeeInfoHelper.findEmployeeIdByEmailSafe(MANAGER_EMAIL))
                .thenReturn(MANAGER_ID);
        lenient().when(employeeInfoHelper.findEmployeeIdByEmailSafe("rh@gerai.tn"))
                .thenReturn(RH_ID);

        lenient().when(employeeRepo.findFullNameByEmployeeId(EMP_ID))
                .thenReturn(EMP_NOM);
        lenient().when(employeeRepo.findFullNameByEmployeeId(MANAGER_ID))
                .thenReturn("Sami Trabelsi");
        lenient().when(employeeRepo.findManagerKeycloakSubByEmployeeId(EMP_ID))
                .thenReturn(MANAGER_SUB);
        lenient().when(employeeRepo.findManagerEmailByEmployeeId(EMP_ID))
                .thenReturn(MANAGER_EMAIL);
    }

    /* ══════════════════════════════════════════════════
       TEST 1 — creerDemande CONGÉ → leaveRepo + Kafka Chef
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("creerDemande(CONGE) → save dans LEAVE_REQUESTS + notification Kafka au chef")
    void creerDemande_conge_saveLeaveRequestEtNotifieChef() {

        // ── Arrange ──────────────────────────────────
        DemandeRequest req = DemandeRequest.builder()
                .type(TypeDemande.CONGE)
                .leaveTypeId(1L)
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 5))
                .daysCount(BigDecimal.valueOf(5))
                .reason("Vacances annuelles")
                .build();

        LeaveRequest saved = LeaveRequest.builder()
                .requestId(101L)
                .employeeId(EMP_ID)
                .leaveTypeId(1L)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .daysCount(req.getDaysCount())
                .status("EN_ATTENTE")
                .createdAt(LocalDateTime.now())
                .build();

        when(leaveRepo.save(any(LeaveRequest.class))).thenReturn(saved);

        // ── Act ───────────────────────────────────────
        DemandeResponse response = service.creerDemande(req, authEmploye());

        // ── Assert : entité sauvegardée ───────────────
        assertThat(response.getRequestId()).isEqualTo(101L);
        assertThat(response.getType()).isEqualTo(TypeDemande.CONGE);
        assertThat(response.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);
        assertThat(response.getStatusOracle()).isEqualTo("EN_ATTENTE");

        verify(leaveRepo).save(argThat(lr ->
                EMP_ID.equals(lr.getEmployeeId())
                        && "EN_ATTENTE".equals(lr.getStatus())
                        && BigDecimal.valueOf(5).equals(lr.getDaysCount())
        ));

        // ── Assert : autres repos non touchés ─────────
        verifyNoInteractions(trainingRepo, loanRepo, documentRepo, authRepo);
    }

    /* ══════════════════════════════════════════════════
       TEST 2 — creerDemande PRÊT → loanRepo + Kafka RH (pas chef)
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("creerDemande(PRET) → save dans LOAN_REQUESTS + notification Kafka au RH (pas de chef)")
    void creerDemande_pret_saveLoanRequestEtNotifieRh() {

        DemandeRequest req = DemandeRequest.builder()
                .type(TypeDemande.PRET)
                .amount(BigDecimal.valueOf(5000))
                .currency("TND")
                .durationMonths(24)
                .reason("Achat véhicule")
                .build();

        LoanRequest saved = LoanRequest.builder()
                .requestId(201L)
                .employeeId(EMP_ID)
                .amount(req.getAmount())
                .currency("TND")
                .durationMonths(24)
                .status("EN_ATTENTE")
                .createdAt(LocalDateTime.now())
                .build();

        when(loanRepo.save(any(LoanRequest.class))).thenReturn(saved);

        DemandeResponse response = service.creerDemande(req, authEmploye());

        assertThat(response.getType()).isEqualTo(TypeDemande.PRET);
        assertThat(response.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE);

        verify(loanRepo).save(any(LoanRequest.class));

        // CONGÉ, FORMATION, DOCUMENT, AUTORISATION non touchés
        verifyNoInteractions(leaveRepo, trainingRepo, documentRepo, authRepo);
    }

    /* ══════════════════════════════════════════════════
       TEST 3 — valider CONGÉ par le CHEF → VALIDE_CHEF Oracle
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("valider(CONGE) par CHEF → statut Oracle VALIDE_CHEF + Kafka employé VALIDEE_CHEF")
    void valider_conge_parChef_setValideChef() {

        LeaveRequest conge = LeaveRequest.builder()
                .requestId(101L)
                .employeeId(EMP_ID)
                .leaveTypeId(1L)
                .daysCount(BigDecimal.valueOf(5))
                .status("EN_ATTENTE")
                .createdAt(LocalDateTime.now())
                .build();

        when(leaveRepo.findById(101L)).thenReturn(Optional.of(conge));
        when(leaveRepo.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        // Simuler un employé dans le repo pour notifierEmploye()
        EmployeeRef empRef = buildEmployeeRef(EMP_ID, EMP_SUB, EMP_EMAIL);
        when(employeeRepo.findById(EMP_ID)).thenReturn(Optional.of(empRef));

        ValidationRequest validation = new ValidationRequest(StatutDemande.VALIDEE_CHEF, "OK pour moi");

        DemandeResponse response = service.valider(101L, TypeDemande.CONGE, validation, authChef());

        // ── Assert statut Oracle ──────────────────────
        assertThat(response.getStatut()).isEqualTo(StatutDemande.VALIDEE_CHEF);
        assertThat(response.getStatusOracle()).isEqualTo("VALIDE_CHEF");

        // ── Assert entité sauvegardée ─────────────────
        verify(leaveRepo).save(argThat(lr ->
                "VALIDE_CHEF".equals(lr.getStatus())
                        && lr.getApprovedBy().equals(MANAGER_ID)
                        && lr.getApprovedAt() != null
                        && "OK pour moi".equals(lr.getRejectionReason())
        ));

    }

    /* ══════════════════════════════════════════════════
       TEST 4 — valider CONGÉ par le RH → VALIDE_RH Oracle
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("valider(CONGE) par RH → statut Oracle VALIDE_RH + Kafka employé VALIDEE_RH")
    void valider_conge_parRh_setValideRh() {

        LeaveRequest conge = LeaveRequest.builder()
                .requestId(102L)
                .employeeId(EMP_ID)
                .leaveTypeId(1L)
                .daysCount(BigDecimal.valueOf(3))
                .status("VALIDE_CHEF")
                .createdAt(LocalDateTime.now())
                .build();

        when(leaveRepo.findById(102L)).thenReturn(Optional.of(conge));
        when(leaveRepo.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeRef empRef = buildEmployeeRef(EMP_ID, EMP_SUB, EMP_EMAIL);
        when(employeeRepo.findById(EMP_ID)).thenReturn(Optional.of(empRef));

        // RH approuve → service force VALIDEE_RH car isRhOrAdmin = true
        ValidationRequest validation = new ValidationRequest(StatutDemande.VALIDEE_RH, "Validé");

        DemandeResponse response = service.valider(102L, TypeDemande.CONGE, validation, authRh());

        assertThat(response.getStatut()).isEqualTo(StatutDemande.VALIDEE_RH);
        assertThat(response.getStatusOracle()).isEqualTo("VALIDE_RH");

        // RH valide → champ approvedByRh (pas approvedBy qui reste pour le chef)
        verify(leaveRepo).save(argThat(lr ->
                "VALIDE_RH".equals(lr.getStatus())
                        && RH_ID.equals(lr.getApprovedByRh())
        ));

    }

    /* ══════════════════════════════════════════════════
       TEST 5 — valider CONGÉ REFUS → REFUSE Oracle
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("valider(CONGE) REJETEE → statut Oracle REFUSE + Kafka employé REJETEE")
    void valider_conge_refus_setRefuse() {

        LeaveRequest conge = LeaveRequest.builder()
                .requestId(103L)
                .employeeId(EMP_ID)
                .leaveTypeId(1L)
                .daysCount(BigDecimal.valueOf(10))
                .status("EN_ATTENTE")
                .createdAt(LocalDateTime.now())
                .build();

        when(leaveRepo.findById(103L)).thenReturn(Optional.of(conge));
        when(leaveRepo.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeRef empRef = buildEmployeeRef(EMP_ID, EMP_SUB, EMP_EMAIL);
        when(employeeRepo.findById(EMP_ID)).thenReturn(Optional.of(empRef));

        ValidationRequest validation = new ValidationRequest(StatutDemande.REJETEE, "Charge trop importante");

        DemandeResponse response = service.valider(103L, TypeDemande.CONGE, validation, authChef());

        assertThat(response.getStatut()).isEqualTo(StatutDemande.REJETEE);
        assertThat(response.getStatusOracle()).isEqualTo("REFUSE");

        verify(leaveRepo).save(argThat(lr ->
                "REFUSE".equals(lr.getStatus())
                        && "Charge trop importante".equals(lr.getRejectionReason())
        ));

    }

    /* ══════════════════════════════════════════════════
       TEST 6 — valider FORMATION par CHEF → APPROUVE_CHEF Oracle
       (statut propre à TRAINING_REQUESTS, différent de CONGE)
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("valider(FORMATION) par CHEF → statut Oracle APPROUVE_CHEF")
    void valider_formation_parChef_setApprouveChef() {

        TrainingRequest formation = TrainingRequest.builder()
                .requestId(301L)
                .employeeId(EMP_ID)
                .trainingTitle("Spring Boot Avancé")
                .status("EN_ATTENTE")
                .createdAt(LocalDateTime.now())
                .build();

        when(trainingRepo.findById(301L)).thenReturn(Optional.of(formation));
        when(trainingRepo.save(any(TrainingRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeRef empRef = buildEmployeeRef(EMP_ID, EMP_SUB, EMP_EMAIL);
        when(employeeRepo.findById(EMP_ID)).thenReturn(Optional.of(empRef));

        ValidationRequest validation = new ValidationRequest(StatutDemande.VALIDEE_CHEF, "Besoin confirmé");

        DemandeResponse response = service.valider(301L, TypeDemande.FORMATION, validation, authChef());

        assertThat(response.getStatut()).isEqualTo(StatutDemande.VALIDEE_CHEF);
        // !! Pour FORMATION, VALIDEE_CHEF → "APPROUVE_CHEF" (pas "VALIDE_CHEF")
        assertThat(response.getStatusOracle()).isEqualTo("APPROUVE_CHEF");

        verify(trainingRepo).save(argThat(tr ->
                "APPROUVE_CHEF".equals(tr.getStatus())
                        && tr.getApprovedBy().equals(MANAGER_ID)
        ));
    }

    /* ══════════════════════════════════════════════════
       TEST 7 — valider PRÊT par RH → APPROUVE Oracle
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("valider(PRET) par RH → statut Oracle APPROUVE")
    void valider_pret_parRh_setApprouve() {

        // needsCommission = false → premier passage RH → VALIDEE_DG (pas EN_ETUDE_DG)
        LoanRequest pret = LoanRequest.builder()
                .requestId(401L)
                .employeeId(EMP_ID)
                .amount(BigDecimal.valueOf(3000))
                .currency("TND")
                .durationMonths(12)
                .status("EN_ATTENTE")
                .needsCommission(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(loanRepo.findById(401L)).thenReturn(Optional.of(pret));
        when(loanRepo.save(any(LoanRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeRef empRef = buildEmployeeRef(EMP_ID, EMP_SUB, EMP_EMAIL);
        when(employeeRepo.findById(EMP_ID)).thenReturn(Optional.of(empRef));

        ValidationRequest validation = new ValidationRequest(StatutDemande.VALIDEE_RH, "Accordé");

        DemandeResponse response = service.valider(401L, TypeDemande.PRET, validation, authRh());

        // Le nouveau workflow PRET va EN_ATTENTE → VALIDEE_DG (premier avis RH)
        // puis VALIDEE_DG → APPROUVE (validation finale RH après décision favorable)
        assertThat(response.getStatusOracle()).isEqualTo("VALIDEE_DG");

        verify(loanRepo).save(argThat(lr ->
                "VALIDEE_DG".equals(lr.getStatus())
                        && RH_ID.equals(lr.getApprovedByRh())
        ));
    }

    /* ══════════════════════════════════════════════════
       TEST 8 — valider AUTORISATION par CHEF → flux direct APPROUVE
       (pas d'étape intermédiaire pour les autorisations)
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("valider(AUTORISATION) par CHEF → flux direct APPROUVE (pas VALIDE_CHEF)")
    void valider_autorisation_parChef_fluxDirect_setApprouve() {

        AuthorizationRequest auth = AuthorizationRequest.builder()
                .requestId(501L)
                .employeeId(EMP_ID)
                .startDatetime(LocalDateTime.now().plusDays(1))
                .endDatetime(LocalDateTime.now().plusDays(1).plusHours(4))
                .reason("Visite médicale")
                .status("EN_ATTENTE")
                .createdAt(LocalDateTime.now())
                .build();

        when(authRepo.findById(501L)).thenReturn(Optional.of(auth));
        when(authRepo.save(any(AuthorizationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeRef empRef = buildEmployeeRef(EMP_ID, EMP_SUB, EMP_EMAIL);
        when(employeeRepo.findById(EMP_ID)).thenReturn(Optional.of(empRef));

        // Chef approuve une autorisation → doit aller directement en VALIDEE_RH
        // car la méthode valider() force VALIDEE_RH pour AUTORISATION quand c'est un Chef
        ValidationRequest validation = new ValidationRequest(StatutDemande.VALIDEE_CHEF, "OK");

        DemandeResponse response = service.valider(
                501L, TypeDemande.AUTORISATION, validation, authChef());

        // Pour AUTORISATION, le Chef → VALIDEE_RH directement (flux 1 seul niveau)
        assertThat(response.getStatut()).isEqualTo(StatutDemande.VALIDEE_RH);
        assertThat(response.getStatusOracle()).isEqualTo("APPROUVE");

        verify(authRepo).save(argThat(a ->
                "APPROUVE".equals(a.getStatus())
                        && a.getApprovedBy().equals(MANAGER_ID)
        ));
    }

    /* ══════════════════════════════════════════════════
       TEST 9 — getMesDemandes → agrège toutes les tables
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("getMesDemandes → retourne les demandes des 5 tables pour l'employé connecté")
    void getMesDemandes_agregeToutesLesTables() {

        LeaveRequest conge = LeaveRequest.builder()
                .requestId(1L).employeeId(EMP_ID).leaveTypeId(1L)
                .daysCount(BigDecimal.ONE).status("EN_ATTENTE")
                .createdAt(LocalDateTime.now().minusDays(3)).build();

        TrainingRequest formation = TrainingRequest.builder()
                .requestId(2L).employeeId(EMP_ID).trainingTitle("Test")
                .status("EN_ATTENTE")
                .createdAt(LocalDateTime.now().minusDays(2)).build();

        LoanRequest pret = LoanRequest.builder()
                .requestId(3L).employeeId(EMP_ID).amount(BigDecimal.TEN)
                .durationMonths(12).status("EN_ATTENTE")
                .createdAt(LocalDateTime.now().minusDays(1)).build();

        when(leaveRepo.findByEmployeeIdOrderByCreatedAtDesc(EMP_ID)).thenReturn(List.of(conge));
        when(trainingRepo.findByEmployeeIdOrderByCreatedAtDesc(EMP_ID)).thenReturn(List.of(formation));
        when(loanRepo.findByEmployeeIdOrderByCreatedAtDesc(EMP_ID)).thenReturn(List.of(pret));
        when(documentRepo.findByEmployeeIdOrderByCreatedAtDesc(EMP_ID)).thenReturn(List.of());
        when(authRepo.findByEmployeeIdOrderByCreatedAtDesc(EMP_ID)).thenReturn(List.of());

        List<DemandeResponse> result = service.getMesDemandes(authEmploye());

        assertThat(result).hasSize(3);
        assertThat(result).extracting("type")
                .containsExactlyInAnyOrder(TypeDemande.CONGE, TypeDemande.FORMATION, TypeDemande.PRET);
        // Doit être trié du plus récent au plus ancien
        assertThat(result.get(0).getType()).isEqualTo(TypeDemande.PRET);
        assertThat(result.get(2).getType()).isEqualTo(TypeDemande.CONGE);
    }

    /* ══════════════════════════════════════════════════
       TEST 10 — resolveEmployeeId fallback via email
       (quand employee_id n'est pas dans le JWT)
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("resolveEmployeeId → fallback email quand claim employee_id absent du JWT")
    void resolveEmployeeId_fallback_viaEmail() {

        // JWT sans claim employee_id (ancien token)
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("old-kc-uuid")
                .claim("email", EMP_EMAIL)
                // PAS de claim employee_id
                .build();

        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        when(auth.getToken()).thenReturn(jwt);
        var authority = new org.springframework.security.core.authority
                .SimpleGrantedAuthority("ROLE_EMPLOYE");
        // lenient: getAuthorities() est appelé par valider() mais pas par creerDemande()
        lenient().doReturn(List.of(authority)).when(auth).getAuthorities();

        // Niveau 1 : "old-kc-uuid" → null par défaut (Mockito, non stubé dans @BeforeEach)
        // Niveau 2 : EMP_EMAIL → EMP_ID stubé en lenient dans @BeforeEach
        // findFullNameByEmployeeId(EMP_ID) → EMP_NOM également stubé en lenient dans @BeforeEach

        LeaveRequest saved = LeaveRequest.builder()
                .requestId(999L).employeeId(EMP_ID).leaveTypeId(1L)
                .daysCount(BigDecimal.ONE).status("EN_ATTENTE")
                .createdAt(LocalDateTime.now()).build();
        when(leaveRepo.save(any())).thenReturn(saved);

        DemandeRequest req = DemandeRequest.builder()
                .type(TypeDemande.CONGE)
                .leaveTypeId(1L)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(1))
                .daysCount(BigDecimal.ONE)
                .build();

        DemandeResponse response = service.creerDemande(req, auth);

        // Le fallback a bien résolu l'identité de l'employé via email
        assertThat(response.getEmployeeId()).isEqualTo(EMP_ID);
    }

    /* ══════════════════════════════════════════════════
       TEST 11 — demande introuvable → IllegalArgumentException
       ══════════════════════════════════════════════════ */

    @Test
    @DisplayName("valider() sur une demande inexistante → IllegalArgumentException")
    void valider_demandeIntrouvable_throwsException() {

        when(leaveRepo.findById(9999L)).thenReturn(Optional.empty());

        ValidationRequest validation = new ValidationRequest(StatutDemande.VALIDEE_CHEF, null);

        assertThatThrownBy(() ->
                service.valider(9999L, TypeDemande.CONGE, validation, authChef())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999");

        // Aucune sauvegarde ne doit être émise
        verify(leaveRepo, never()).save(any());
    }

    /* ── Helper : construit un EmployeeRef pour les tests ── */
    private EmployeeRef buildEmployeeRef(Long id, String userId, String email) {
        // EmployeeRef est @Immutable — pas de setter, on construit via réflexion
        // ou on utilise un spy. Ici on utilise un mock pour rester simple.
        EmployeeRef ref = mock(EmployeeRef.class);
        lenient().when(ref.getEmployeeId()).thenReturn(id);
        lenient().when(ref.getUserId()).thenReturn(userId);
        lenient().when(ref.getEmail()).thenReturn(email);
        lenient().when(ref.getFirstName()).thenReturn("Nour");
        lenient().when(ref.getLastName()).thenReturn("Bousaidi");
        return ref;
    }
}