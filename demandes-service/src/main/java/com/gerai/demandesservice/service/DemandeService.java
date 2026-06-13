package com.gerai.demandesservice.service;

import com.gerai.demandesservice.dto.*;
import com.gerai.demandesservice.dto.DemandeEvent;
import com.gerai.demandesservice.dto.NotificationEvent;
import com.gerai.demandesservice.model.*;
import com.gerai.demandesservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service principal du demandes-service GerAI.
 *
 * Adapte l'ancien modèle (table DEMANDES unique) vers les 5 tables Oracle réelles :
 *   LEAVE_REQUESTS, TRAINING_REQUESTS, LOAN_REQUESTS,
 *   DOCUMENT_REQUESTS, AUTHORIZATION_REQUESTS
 *
 * Stratégie de résolution d'identité :
 *   1. Extrait le claim "sub" du JWT Keycloak
 *   2. Résout l'employee_id Oracle via EMPLOYEES.user_id = sub
 *   3. Fallback sur l'email si le sub ne résout pas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemandeService {

    private final LeaveRequestRepository                    leaveRepo;
    private final TrainingRequestRepository                  trainingRepo;
    private final LoanRequestRepository                      loanRepo;
    private final DocumentRequestRepository                  documentRepo;
    private final AuthorizationRequestRepository             authRepo;
    private final EmployeeRepository                         employeeRepo;
    private final EmployeeInfoHelper                         employeeInfoHelper;
    private final SalaryValidationService                    salaryValidationService;
    private final LeaveQuotaService                          leaveQuotaService;
    private final WorkingDayService                          workingDayService;
    private final KafkaTemplate<String, NotificationEvent>   kafkaTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.kafka.topic.notifications}")
    private String notificationTopic;

    @Value("${app.services.analytics-url:http://localhost:8083}")
    private String analyticsServiceUrl;

    /* ═══════════════════════════════════════════════════════
       CRÉATION — dispatch selon TypeDemande
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public DemandeResponse creerDemande(DemandeRequest request, Authentication auth) {
        Long employeeId = resolveEmployeeId(auth);
        String employeNom = resolveNom(employeeId);

        log.info("[Demande] Création | type={} | emp={} ({})",
                request.getType(), employeeId, employeNom);

        return switch (request.getType()) {
            case CONGE       -> creerConge(request, employeeId, employeNom, auth);
            case FORMATION   -> creerFormation(request, employeeId, employeNom, auth);
            case PRET        -> creerPret(request, employeeId, employeNom, auth);
            case DOCUMENT    -> creerDocument(request, employeeId, employeNom, auth);
            case AUTORISATION-> creerAutorisation(request, employeeId, employeNom, auth);
        };
    }

    private DemandeResponse creerConge(DemandeRequest req, Long empId,
                                       String empNom, Authentication auth) {
        Long leaveTypeId = req.getLeaveTypeId() != null ? req.getLeaveTypeId() : 1L;

        // Accurate working-day count if not provided
        BigDecimal daysCount = req.getDaysCount();
        if (daysCount == null && req.getStartDate() != null && req.getEndDate() != null) {
            daysCount = BigDecimal.valueOf(
                workingDayService.countWorkingDays(req.getStartDate(), req.getEndDate()));
        }

        // Once-in-career check (HAJJ)
        leaveQuotaService.checkOnceInCareer(empId, leaveTypeId);

        // Annual quota check
        if (req.getStartDate() != null) {
            leaveQuotaService.checkAnnualQuota(empId, leaveTypeId, daysCount, req.getStartDate().getYear());
        }

        boolean halfSalary  = leaveQuotaService.isHalfSalary(leaveTypeId);
        boolean needsMedical = leaveQuotaService.requiresMedicalCommittee(leaveTypeId);
        String initialStatus = needsMedical ? "EN_ETUDE_MEDICALE" : "EN_ATTENTE";

        LeaveRequest entity = LeaveRequest.builder()
                .employeeId(empId)
                .leaveTypeId(leaveTypeId)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .daysCount(daysCount)
                .reason(req.getReason())
                .attachmentUrl(req.getAttachmentUrl())
                .halfSalary(halfSalary)
                .status(initialStatus)
                .build();
        entity = leaveRepo.save(entity);

        // Record once-in-career usage after successful save
        if (leaveTypeId == 9L) {
            leaveQuotaService.recordCareerUsage(empId, leaveTypeId, entity.getRequestId());
        }

        if (needsMedical) {
            log.info("[Conge] Longue maladie soumise — en attente comité médical | emp={}", empId);
        } else {
            notifierChef(empId, empNom, entity.getRequestId(), TypeDemande.CONGE, auth);
        }
        return toResponse(entity, empNom);
    }

    @Transactional
    public DemandeResponse decisionMedicale(Long id, MedicalDecisionRequest decision, Authentication auth) {
        LeaveRequest entity = leaveRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + id));

        if (!"EN_ETUDE_MEDICALE".equals(entity.getStatus())) {
            throw new IllegalStateException("Cette demande n'est pas en attente de décision médicale.");
        }

        Long valideurId = tryResolveEmployeeId(auth);
        entity.setMedApproved(decision.isApprouve());
        entity.setMedApprovedAt(LocalDateTime.now());
        entity.setMedApprovedBy(valideurId);
        entity.setMedComment(decision.getCommentaire());

        if (decision.isApprouve()) {
            entity.setStatus("EN_ATTENTE"); // forward to normal chef workflow
        } else {
            entity.setStatus("REFUSE");
            entity.setRejectionReason(decision.getCommentaire());
        }

        entity = leaveRepo.save(entity);
        return toResponse(entity, null);
    }

    private DemandeResponse creerFormation(DemandeRequest req, Long empId,
                                           String empNom, Authentication auth) {
        TrainingRequest entity = TrainingRequest.builder()
                .employeeId(empId)
                .trainingTitle(req.getTrainingTitle())
                .provider(req.getProvider())
                .estimatedCost(req.getEstimatedCost())
                .plannedDate(req.getPlannedDate())
                .durationDays(req.getDurationDays())
                .lieu(req.getLieu())
                .modeFormation(req.getModeFormation())
                .reason(req.getReason())
                .status("EN_ATTENTE")
                .build();
        entity = trainingRepo.save(entity);

        notifierChef(empId, empNom, entity.getRequestId(), TypeDemande.FORMATION, auth);
        return toResponse(entity, empNom);
    }

    private DemandeResponse creerPret(DemandeRequest req, Long empId,
                                      String empNom, Authentication auth) {
        // Validation : montant ≤ 3× salaire mensuel brut
        // Uses a REQUIRES_NEW transaction so an ORA-00942 (CONTRACTS missing) cannot poison the main TX.
        if (req.getAmount() != null) {
            java.math.BigDecimal salaire = salaryValidationService.getSalary(empId);
            if (salaire != null && salaire.compareTo(java.math.BigDecimal.ZERO) > 0) {
                java.math.BigDecimal plafond = salaire.multiply(java.math.BigDecimal.valueOf(3));
                if (req.getAmount().compareTo(plafond) > 0) {
                    throw new IllegalArgumentException(
                        "Le montant demandé (" + req.getAmount() + " TND) dépasse le plafond autorisé de 3× votre salaire (" + plafond + " TND).");
                }
            }
        }

        // ── Étape 1 : Avis de la hiérarchie (selon paramétrage) ──────────────
        // Si l'employé a un responsable configuré, la demande passe d'abord par lui.
        // Sinon, elle est transmise directement à l'étape suivante.
        Long managerId = employeeInfoHelper.findManagerId(empId);
        if (managerId == null) managerId = employeeInfoHelper.findManagerIdViaProject(empId);
        if (managerId == null) managerId = employeeInfoHelper.findChefInSameDept(empId);
        boolean hasHierarchy = (managerId != null);

        // ── Étape 2 : Commission de prêt (si prêt nécessitant une commission) ─
        // Par défaut true ; peut être overridé via le champ needsCommission de la requête.
        boolean needsCommission = req.getNeedsCommission() == null || req.getNeedsCommission();

        // ── Routing initial ──────────────────────────────────────────────────
        // Avec hiérarchie       → EN_ATTENTE (chef donne son avis d'abord)
        // Sans hiérarchie + commission → EN_ETUDE_DG (direct commission)
        // Sans hiérarchie + sans commission → VALIDEE_DG  (direct Direction RH)
        String initialStatus;
        if (hasHierarchy) {
            initialStatus = "EN_ATTENTE";
        } else if (needsCommission) {
            initialStatus = "EN_ETUDE_DG";
        } else {
            initialStatus = "VALIDEE_DG";
        }

        LoanRequest entity = LoanRequest.builder()
                .employeeId(empId)
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "TND")
                .durationMonths(req.getDurationMonths())
                .reason(req.getReason())
                .needsCommission(needsCommission)
                .status(initialStatus)
                .build();
        entity = loanRepo.save(entity);

        // Notifier selon l'étape atteinte
        if (hasHierarchy) {
            notifierChef(empId, empNom, entity.getRequestId(), TypeDemande.PRET, auth);
        } else {
            // Pas de hiérarchie → notifier directement RH/commission
            notifierRh(empId, empNom, entity.getRequestId(), TypeDemande.PRET, auth);
        }
        return toResponse(entity, empNom);
    }

    private DemandeResponse creerDocument(DemandeRequest req, Long empId,
                                          String empNom, Authentication auth) {
        DocumentRequest entity = DocumentRequest.builder()
                .employeeId(empId)
                .docTypeId(req.getDocTypeId() != null ? req.getDocTypeId() : 1L)
                .reason(req.getReason())
                .copiesCount(req.getCopiesCount() != null ? req.getCopiesCount() : 1)
                .language(req.getLanguage() != null ? req.getLanguage() : "FR")
                .status("EN_ATTENTE")
                .build();
        entity = documentRepo.save(entity);

        notifierRh(empId, empNom, entity.getRequestId(), TypeDemande.DOCUMENT, auth);
        return toResponse(entity, empNom);
    }

    private DemandeResponse creerAutorisation(DemandeRequest req, Long empId,
                                              String empNom, Authentication auth) {
        BigDecimal durHours = req.getDurationHours();
        if (durHours == null && req.getStartDatetime() != null && req.getEndDatetime() != null) {
            long h = java.time.temporal.ChronoUnit.HOURS.between(req.getStartDatetime(), req.getEndDatetime());
            durHours = BigDecimal.valueOf(h > 0 ? h : 1);
        }
        AuthorizationRequest entity = AuthorizationRequest.builder()
                .employeeId(empId)
                .startDatetime(req.getStartDatetime())
                .endDatetime(req.getEndDatetime())
                .durationHours(durHours)
                .reason(req.getReason())
                .status("EN_ATTENTE")
                .build();
        entity = authRepo.save(entity);

        notifierChef(empId, empNom, entity.getRequestId(), TypeDemande.AUTORISATION, auth);
        return toResponse(entity, empNom);
    }

    /* ═══════════════════════════════════════════════════════
       LECTURE — par employé connecté
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<DemandeResponse> getMesDemandes(Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        String nom = employeeRepo.findFullNameByEmployeeId(empId);
        List<DemandeResponse> all = new ArrayList<>();

        leaveRepo.findByEmployeeIdOrderByCreatedAtDesc(empId)
                .forEach(e -> all.add(toResponse(e, nom)));
        trainingRepo.findByEmployeeIdOrderByCreatedAtDesc(empId)
                .forEach(e -> all.add(toResponse(e, nom)));
        loanRepo.findByEmployeeIdOrderByCreatedAtDesc(empId)
                .forEach(e -> all.add(toResponse(e, nom)));
        documentRepo.findByEmployeeIdOrderByCreatedAtDesc(empId)
                .forEach(e -> all.add(toResponse(e, nom)));
        authRepo.findByEmployeeIdOrderByCreatedAtDesc(empId)
                .forEach(e -> all.add(toResponse(e, nom)));

        all.sort(Comparator.comparing(DemandeResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return all;
    }

    /* ═══════════════════════════════════════════════════════
       LECTURE — espace Chef (demandes de son équipe)
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<DemandeResponse> getDemandesEquipe(Authentication auth) {
        Long managerEmpId = resolveEmployeeId(auth);
        List<DemandeResponse> all = new ArrayList<>();

        leaveRepo.findByManager(managerEmpId)
                .forEach(e -> all.add(toResponse(e, null)));
        trainingRepo.findByManager(managerEmpId)
                .forEach(e -> all.add(toResponse(e, null)));
        authRepo.findByManager(managerEmpId, "EN_ATTENTE")
                .forEach(e -> all.add(toResponse(e, null)));

        // Fallback: MANAGER_ID non renseigné → chercher par département
        if (all.isEmpty()) {
            log.warn("[getDemandesEquipe] MANAGER_ID non renseigné pour manager={}. " +
                     "Fallback: recherche par département.", managerEmpId);
            addDeptFallback(managerEmpId, null, all);
        }

        all.sort(Comparator.comparing(DemandeResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return all;
    }

    @Transactional(readOnly = true)
    public List<DemandeResponse> getDemandesEnAttenteChef(Authentication auth) {
        Long managerEmpId = resolveEmployeeId(auth);
        List<DemandeResponse> all = new ArrayList<>();

        leaveRepo.findByManagerAndStatus(managerEmpId, "EN_ATTENTE")
                .forEach(e -> all.add(toResponse(e, null)));
        trainingRepo.findByManagerAndStatus(managerEmpId, "EN_ATTENTE")
                .forEach(e -> all.add(toResponse(e, null)));
        authRepo.findByManagerAndStatus(managerEmpId, "EN_ATTENTE")
                .forEach(e -> all.add(toResponse(e, null)));

        if (all.isEmpty()) {
            addDeptFallback(managerEmpId, "EN_ATTENTE", all);
        }
        return all;
    }

    private void addDeptFallback(Long managerEmpId, String statusFilter, List<DemandeResponse> all) {
        Long deptId = employeeRepo.findDeptIdByEmployeeId(managerEmpId);
        if (deptId == null) return;
        List<Long> deptEmployees = employeeRepo.findEmployeeIdsByDeptId(deptId)
                .stream().filter(id -> !id.equals(managerEmpId)).toList();
        if (deptEmployees.isEmpty()) return;

        leaveRepo.findByEmployeeIdIn(deptEmployees).stream()
                .filter(e -> statusFilter == null || statusFilter.equals(e.getStatus()))
                .forEach(e -> all.add(toResponse(e, null)));
        trainingRepo.findByEmployeeIdIn(deptEmployees).stream()
                .filter(e -> statusFilter == null || statusFilter.equals(e.getStatus()))
                .forEach(e -> all.add(toResponse(e, null)));
        authRepo.findByEmployeeIdIn(deptEmployees).stream()
                .filter(e -> statusFilter == null || statusFilter.equals(e.getStatus()))
                .forEach(e -> all.add(toResponse(e, null)));
    }

    /* ═══════════════════════════════════════════════════════
       LECTURE — espace RH (toutes demandes)
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<DemandeResponse> getToutesDemandes() {
        List<DemandeResponse> all = new ArrayList<>();

        leaveRepo.findAll().forEach(e -> all.add(toResponse(e, null)));
        trainingRepo.findAll().forEach(e -> all.add(toResponse(e, null)));
        loanRepo.findAll().forEach(e -> all.add(toResponse(e, null)));
        documentRepo.findAll().forEach(e -> all.add(toResponse(e, null)));
        authRepo.findAll().forEach(e -> all.add(toResponse(e, null)));

        all.sort(Comparator.comparing(DemandeResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return all;
    }

    /** Crédits en attente de validation finale Direction RH (statut VALIDEE_DG = avis favorable de la commission). */
    @Transactional(readOnly = true)
    public List<DemandeResponse> getCreditsEnAttenteDg() {
        return loanRepo.findByStatusOrderByCreatedAtDesc("VALIDEE_DG")
                .stream().map(e -> toResponse(e, null)).toList();
    }

    /** Historique DG : tous les crédits décidés (VALIDEE_DG + REJETEE). */
    @Transactional(readOnly = true)
    public List<DemandeResponse> getAllCredits() {
        return loanRepo.findByStatusInOrderByCreatedAtDesc(List.of("VALIDEE_DG", "REJETEE", "EN_ETUDE_DG", "VALIDEE_RH"))
                .stream().map(e -> toResponse(e, null)).toList();
    }

    @Transactional(readOnly = true)
    public List<DemandeResponse> getDemandesEnAttenteRh() {
        List<DemandeResponse> all = new ArrayList<>();
        // RH voit les demandes dont le statut est la 2ème étape (validée chef, ou directe)
        leaveRepo.findByStatusOrderByCreatedAtDesc("VALIDE_CHEF")
                .forEach(e -> all.add(toResponse(e, null)));
        trainingRepo.findByStatusOrderByCreatedAtDesc("APPROUVE_CHEF")
                .forEach(e -> all.add(toResponse(e, null)));
        loanRepo.findByStatusOrderByCreatedAtDesc("EN_ATTENTE")
                .forEach(e -> all.add(toResponse(e, null)));
        documentRepo.findByStatusOrderByCreatedAtDesc("EN_ATTENTE")
                .forEach(e -> all.add(toResponse(e, null)));
        return all;
    }

    /* ═══════════════════════════════════════════════════════
       VALIDATION / REJET
       dispatch sur le bon repository selon le type
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public DemandeResponse valider(Long requestId, TypeDemande type,
                                   ValidationRequest validation, Authentication auth) {
        Long valideurEmpId = tryResolveEmployeeId(auth);

        // 1. Détecter si l'utilisateur est RH ou ADMIN
        boolean isRhOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RH")
                            || a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_ADMIN_RH"));

        // 2. Calculer le statut API cible selon le workflow métier
        StatutDemande statutTarget;
        if (validation.getNouveauStatut() == StatutDemande.REJETEE) {
            statutTarget = StatutDemande.REJETEE;
        } else if (isRhOrAdmin) {
            statutTarget = StatutDemande.VALIDEE_RH;
        } else {
            // C'est un CHEF :
            // Si c'est une AUTORISATION, flux direct -> VALIDEE_RH
            // Sinon (CONGE, FORMATION, etc.) -> VALIDEE_CHEF (étape intermédiaire)
            statutTarget = (type == TypeDemande.AUTORISATION) ? StatutDemande.VALIDEE_RH : StatutDemande.VALIDEE_CHEF;
        }

        // 3. Obtenir la String Oracle correspondante via l'Enum
        String oracleStatus = statutTarget.toOracleStatus(type);

        log.info("[Demande] Validation | id={} | type={} | role_RH={} | target={}",
                requestId, type, isRhOrAdmin, statutTarget);

        // 4. Dispatch vers les méthodes privées
        return switch (type) {
            case CONGE        -> validerConge(requestId, oracleStatus, validation, statutTarget, valideurEmpId, isRhOrAdmin, auth);
            case FORMATION    -> validerFormation(requestId, oracleStatus, validation, statutTarget, valideurEmpId, isRhOrAdmin, auth);
            case PRET         -> validerPret(requestId, oracleStatus, validation, statutTarget, valideurEmpId, isRhOrAdmin, auth);
            case DOCUMENT     -> validerDocument(requestId, oracleStatus, validation, statutTarget, valideurEmpId, auth);
            case AUTORISATION -> validerAutorisation(requestId, oracleStatus, validation, statutTarget, valideurEmpId, auth);
        };
    }

    /* ═══════════════════════════════════════════════════════
   MÉTHODES DE VALIDATION PRIVÉES (CORRIGÉES)
   ═══════════════════════════════════════════════════════ */

    private DemandeResponse validerConge(Long id, String oracleStatus, ValidationRequest val,
                                         StatutDemande statutTarget, Long valideurId, boolean isRh, Authentication auth) {
        LeaveRequest entity = leaveRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Congé introuvable : " + id));

        entity.setStatus(oracleStatus);
        if (isRh) {
            entity.setApprovedByRh(valideurId);
            entity.setApprovedAtRh(LocalDateTime.now());
            entity.setApprovedByRhName(extractFullNameFromToken(auth));
        } else {
            entity.setApprovedBy(valideurId);
            entity.setApprovedAt(LocalDateTime.now());
        }
        if (val.getCommentaire() != null) entity.setRejectionReason(val.getCommentaire());

        entity = leaveRepo.save(entity);

        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.CONGE, statutTarget, oracleStatus,
                entity.getDaysCount() != null ? entity.getDaysCount().intValue() : null, auth);

        // Chef vient de valider → notifier RH pour la validation finale
        if (!isRh && statutTarget == StatutDemande.VALIDEE_CHEF) {
            notifierRhApresChef(entity.getEmployeeId(), resolveNom(entity.getEmployeeId()),
                    entity.getRequestId(), TypeDemande.CONGE);
        }

        return toResponse(entity, null);
    }

    private DemandeResponse validerFormation(Long id, String oracleStatus, ValidationRequest val,
                                             StatutDemande statutTarget, Long valideurId, boolean isRh, Authentication auth) {
        TrainingRequest entity = trainingRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Formation introuvable : " + id));

        entity.setStatus(oracleStatus);
        if (isRh) {
            entity.setApprovedByRh(valideurId);
            entity.setApprovedAtRh(LocalDateTime.now());
            entity.setApprovedByRhName(extractFullNameFromToken(auth));
        } else {
            entity.setApprovedBy(valideurId);
        }

        entity = trainingRepo.save(entity);

        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.FORMATION, statutTarget, oracleStatus, null, auth);

        // Chef vient de valider → notifier RH pour la validation finale
        if (!isRh && statutTarget == StatutDemande.VALIDEE_CHEF) {
            notifierRhApresChef(entity.getEmployeeId(), resolveNom(entity.getEmployeeId()),
                    entity.getRequestId(), TypeDemande.FORMATION);
        }

        return toResponse(entity, null);
    }

    private DemandeResponse validerPret(Long id, String ignoredOracleStatus, ValidationRequest val,
                                        StatutDemande statutTarget, Long valideurId, boolean isRh, Authentication auth) {
        LoanRequest entity = loanRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Crédit introuvable : " + id));

        if (statutTarget == StatutDemande.REJETEE) {
            // Refus (RH)
            entity.setStatus("REFUSE");
            entity.setApprovedBy(valideurId);
            entity.setApprovedAt(LocalDateTime.now());
            if (val.getCommentaire() != null) entity.setRejectionReason(val.getCommentaire());
        } else if (isRh && "VALIDEE_DG".equals(entity.getStatus())) {
            // Validation finale RH après décision favorable du comité
            entity.setStatus("APPROUVE");
            entity.setFinalApprovedBy(valideurId);
            entity.setFinalApprovedAt(LocalDateTime.now());
            entity.setFinalApprovedByName(extractFullNameFromToken(auth));
        } else if (isRh && "EN_ATTENTE".equals(entity.getStatus())) {
            // RH valide l'éligibilité après avis hiérarchie
            // → commission si nécessaire, sinon direct Direction RH
            String nextStatus = entity.isNeedsCommission() ? "EN_ETUDE_DG" : "VALIDEE_DG";
            entity.setStatus(nextStatus);
            entity.setApprovedByRh(valideurId);
            entity.setApprovedAtRh(LocalDateTime.now());
            entity.setApprovedByRhName(extractFullNameFromToken(auth));
        } else if (!isRh && "EN_ATTENTE".equals(entity.getStatus())) {
            // Avis du chef hiérarchique : transmet à l'étape suivante
            // → commission si nécessaire, sinon direct Direction RH
            String nextStatus = entity.isNeedsCommission() ? "EN_ETUDE_DG" : "VALIDEE_DG";
            entity.setStatus(nextStatus);
            entity.setApprovedBy(valideurId);
            entity.setApprovedAt(LocalDateTime.now());
        } else if (!isRh) {
            log.warn("[Crédit] Le chef ne peut pas agir sur un crédit au statut {} (id={})", entity.getStatus(), id);
            return toResponse(entity, null);
        }

        entity = loanRepo.save(entity);

        StatutDemande apiStatut;
        if (statutTarget == StatutDemande.REJETEE) {
            apiStatut = StatutDemande.REJETEE;
        } else if ("APPROUVE".equals(entity.getStatus())) {
            apiStatut = StatutDemande.VALIDEE_RH;
        } else {
            apiStatut = StatutDemande.EN_ETUDE_DG;
        }
        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.PRET, apiStatut, entity.getStatus(), null, auth);

        return toResponse(entity, null);
    }

    /** Décision finale de la Direction RH sur un crédit ayant reçu l'avis de la commission (VALIDEE_DG). */
    @Transactional
    public DemandeResponse decisionDg(Long id, com.gerai.demandesservice.dto.DgDecisionRequest decision,
                                      Authentication auth) {
        LoanRequest entity = loanRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Crédit introuvable : " + id));

        String currentStatus = entity.getStatus();
        if (!"VALIDEE_DG".equals(currentStatus) && !"EN_ETUDE_DG".equals(currentStatus)) {
            throw new IllegalStateException(
                    "Ce crédit ne peut pas faire l'objet d'une décision Direction RH (statut actuel : " + currentStatus + ")");
        }

        Long dgId = tryResolveEmployeeId(auth);

        if (decision.isApprouve()) {
            // Validation finale Direction RH → prêt approuvé, en attente de déblocage
            entity.setStatus("APPROUVE");
            entity.setFinalApprovedBy(dgId);
            entity.setFinalApprovedAt(LocalDateTime.now());
            entity.setFinalApprovedByName(extractFullNameFromToken(auth));
            if (decision.getMontantApprouve() != null) {
                entity.setMontantApprouve(decision.getMontantApprouve());
            }
            if (decision.getNbTranches() != null) {
                entity.setNbTranches(decision.getNbTranches());
                if (entity.getMontantApprouve() != null && decision.getNbTranches() > 0) {
                    entity.setMontantTranche(entity.getMontantApprouve().divide(
                            java.math.BigDecimal.valueOf(decision.getNbTranches()),
                            2, java.math.RoundingMode.HALF_UP));
                }
            }
        } else {
            entity.setStatus("REFUSE");
            entity.setRejectionReason(decision.getCommentaire());
        }

        entity.setDgApprovedBy(dgId);
        entity.setDgDecisionAt(LocalDateTime.now());
        entity.setDgComment(decision.getCommentaire());
        entity.setDgApprovedByName(extractFullNameFromToken(auth));
        entity = loanRepo.save(entity);

        StatutDemande apiStatut = decision.isApprouve() ? StatutDemande.VALIDEE_RH : StatutDemande.REJETEE;
        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.PRET, apiStatut, entity.getStatus(), null, auth);

        return toResponse(entity, null);
    }

    private DemandeResponse validerDocument(Long id, String oracleStatus, ValidationRequest val,
                                            StatutDemande statutTarget, Long valideurId, Authentication auth) {
        DocumentRequest entity = documentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable : " + id));

        entity.setStatus(oracleStatus);
        entity.setProcessedBy(valideurId);
        entity.setProcessedAt(LocalDateTime.now());
        // Note: Si ta table DOCUMENT possède REJECTION_REASON, ajoute-le ici

        entity = documentRepo.save(entity);

        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.DOCUMENT, statutTarget, oracleStatus, null, auth);

        return toResponse(entity, null);
    }

    private DemandeResponse validerAutorisation(Long id, String oracleStatus, ValidationRequest val,
                                                StatutDemande statutTarget, Long valideurId, Authentication auth) {
        AuthorizationRequest entity = authRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Autorisation introuvable : " + id));

        entity.setStatus(oracleStatus);
        entity.setApprovedBy(valideurId);
        entity.setApprovedAt(LocalDateTime.now());

        entity = authRepo.save(entity);

        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.AUTORISATION, statutTarget, oracleStatus, null, auth);

        return toResponse(entity, null);
    }

    /* ═══════════════════════════════════════════════════════
       HTTP — Notifications
       ═══════════════════════════════════════════════════════ */

    private void notifierChef(Long empId, String empNom, Long requestId,
                              TypeDemande type, Authentication auth) {
        try {
            Long managerId = employeeInfoHelper.findManagerId(empId);

            if (managerId == null) {
                // Fallback 1 : chef du projet auquel l'employé appartient (cohérent avec TrainingRequestRepository)
                managerId = employeeInfoHelper.findManagerIdViaProject(empId);
            }

            if (managerId == null) {
                // Fallback 2 : quelqu'un avec "CHEF" ou "MANAGER" dans le titre du même département
                managerId = employeeInfoHelper.findChefInSameDept(empId);
            }

            if (managerId == null) {
                log.warn("[Notif] Aucun chef trouvé pour emp={}. " +
                         "Configurez EMPLOYEES.MANAGER_ID ou ajoutez l'employé à un projet.", empId);
                return;
            }

            String managerEmail = employeeInfoHelper.findEmail(managerId);

            NotificationEvent event = NotificationEvent.builder()
                    .employeeId(managerId)
                    .email(managerEmail)
                    .type("INFO")
                    .title("Nouvelle demande de " + empNom)
                    .content(empNom + " a soumis une demande de type " + type.name())
                    .referenceId(String.valueOf(requestId))
                    .referenceType(type.name())
                    .actionUrl("/chef/demandes")
                    .sourceService("DEMANDES-SERVICE")
                    .build();

            sendNotification(event);
        } catch (Exception e) {
            log.warn("[Notif] notifierChef failed for emp={}: {}", empId, e.getMessage());
        }
    }

    private void notifierRh(Long empId, String empNom, Long requestId,
                            TypeDemande type, Authentication auth) {
        List<Long> adminIds;
        try {
            adminIds = employeeInfoHelper.findAdminIds();
        } catch (Exception e) {
            log.warn("[Notif] notifierRh : impossible de récupérer les admins ({}), notification ignorée", e.getMessage());
            return;
        }
        if (adminIds.isEmpty()) {
            log.warn("[Notif] notifierRh : aucun administrateur trouvé en DB (ref={})", requestId);
            return;
        }
        String label = typeLabel(type);
        for (Long adminId : adminIds) {
            String adminEmail = employeeRepo.findEmailByEmployeeId(adminId);
            NotificationEvent event = NotificationEvent.builder()
                    .employeeId(adminId)
                    .email(adminEmail)
                    .type("INFO")
                    .title("Nouvelle demande de " + label + " de " + empNom)
                    .content(empNom + " a soumis une demande de " + label
                             + " en attente de votre traitement. (Réf: " + requestId + ")")
                    .referenceId(String.valueOf(requestId))
                    .referenceType(type.name())
                    .actionUrl("/admin/demandes")
                    .sourceService("DEMANDES-SERVICE")
                    .build();
            sendNotification(event);
        }
        log.info("[Notif] notifierRh : {} administrateur(s) notifié(s) pour ref={}", adminIds.size(), requestId);
    }

    /** Notifie les admins RH après validation chef — demande en attente de validation finale. */
    private void notifierRhApresChef(Long empId, String empNom, Long requestId, TypeDemande type) {
        List<Long> adminIds;
        try {
            adminIds = employeeInfoHelper.findAdminIds();
        } catch (Exception e) {
            log.warn("[Notif] notifierRhApresChef : impossible de récupérer les admins ({})", e.getMessage());
            return;
        }
        if (adminIds.isEmpty()) {
            log.warn("[Notif] notifierRhApresChef : aucun administrateur trouvé (ref={})", requestId);
            return;
        }
        String label = typeLabel(type);
        for (Long adminId : adminIds) {
            String adminEmail = employeeRepo.findEmailByEmployeeId(adminId);
            NotificationEvent event = NotificationEvent.builder()
                    .employeeId(adminId)
                    .email(adminEmail)
                    .type("INFO")
                    .title("Demande de " + label + " à valider — " + empNom)
                    .content("La demande de " + label + " de " + empNom
                             + " a été approuvée par le chef et attend votre validation finale. (Réf: " + requestId + ")")
                    .referenceId(String.valueOf(requestId))
                    .referenceType(type.name())
                    .actionUrl("/admin/demandes")
                    .sourceService("DEMANDES-SERVICE")
                    .build();
            sendNotification(event);
        }
        log.info("[Notif] notifierRhApresChef : {} admin(s) notifié(s) pour ref={}", adminIds.size(), requestId);
    }

    private static String typeLabel(TypeDemande type) {
        return switch (type) {
            case PRET        -> "crédit";
            case DOCUMENT    -> "document";
            case CONGE       -> "congé";
            case FORMATION   -> "formation";
            case AUTORISATION-> "autorisation";
        };
    }

    private void notifierEmploye(Long empId, Long requestId, TypeDemande type,
                                 StatutDemande statutApi, String oracleStatus,
                                 Integer nbJours, Authentication auth) {
        Optional<EmployeeRef> empOpt = employeeRepo.findById(empId);
        String empEmail = empOpt.map(EmployeeRef::getEmail).orElse(null);

        String label = (type == TypeDemande.PRET) ? "crédit" : type.name().toLowerCase();
        String titre = switch (statutApi) {
            case VALIDEE_CHEF  -> "Votre demande " + label + " a été validée par votre chef";
            case EN_ETUDE_DG   -> "Votre demande de crédit est transmise au Directeur Général pour décision";
            case VALIDEE_RH    -> (type == TypeDemande.PRET)
                    ? "Votre demande de crédit a été approuvée par le Directeur Général"
                    : "Votre demande " + label + " a été approuvée par RH";
            case REJETEE       -> "Votre demande de " + label + " a été refusée";
            case ANNULEE       -> "Votre demande de " + label + " a été annulée";
            default            -> "Mise à jour de votre demande de " + label;
        };

        NotificationEvent event = NotificationEvent.builder()
                .employeeId(empId)
                .email(empEmail)
                .type(statutApi.name())
                .title(titre)
                .content(titre + " (Réf: " + requestId + ")")
                .referenceId(String.valueOf(requestId))
                .referenceType(type.name())
                .actionUrl("/employe/demandes")
                .sourceService("DEMANDES-SERVICE")
                .nbJours(nbJours)
                .build();

        sendNotification(event);

        // Mise à jour stats analytics (congés/refus uniquement)
        if (type == TypeDemande.CONGE || statutApi == StatutDemande.REJETEE) {
            sendAnalyticsEvent(empId, requestId, type, oracleStatus, nbJours);
        }
    }

    private void sendNotification(NotificationEvent event) {
        if (event.getEmployeeId() == null) return;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                kafkaTemplate.send(notificationTopic, String.valueOf(event.getEmployeeId()), event)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("[Notif] Échec Kafka | emp={} : {}", event.getEmployeeId(), ex.getMessage());
                            } else {
                                log.info("[Notif] Publié | type={} | emp={} | ref={} | partition={} | offset={}",
                                        event.getType(), event.getEmployeeId(), event.getReferenceId(),
                                        result.getRecordMetadata().partition(),
                                        result.getRecordMetadata().offset());
                            }
                        });
            } catch (Exception ex) {
                log.warn("[Notif] Kafka send failed (async) | emp={} : {}", event.getEmployeeId(), ex.getMessage());
            }
        });
    }

    private void sendAnalyticsEvent(Long empId, Long requestId, TypeDemande type,
                                    String oracleStatus, Integer nbJours) {
        try {
            DemandeEvent analyticsEvent = DemandeEvent.builder()
                    .destinataireId(String.valueOf(empId))
                    .typeDemande(type.name())
                    .statut(oracleStatus)
                    .referenceId(String.valueOf(requestId))
                    .nbJours(nbJours)
                    .sourceService("DEMANDES-SERVICE")
                    .build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(
                    analyticsServiceUrl + "/internal/events",
                    new HttpEntity<>(analyticsEvent, headers),
                    Void.class);
        } catch (Exception e) {
            log.warn("[Analytics] Erreur envoi event ref={} : {}", requestId, e.getMessage());
        }
    }

    /* ═══════════════════════════════════════════════════════
       CONGÉS — endpoints spécifiques /api/conges/*
       ═══════════════════════════════════════════════════════ */

    private static final int CONGES_ANNUELS_TOTAL = 30;

    /** Solde de congés de l'employé connecté pour l'année en cours. */
    @Transactional(readOnly = true)
    public Map<String, Object> getCongesSolde(Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        int year = LocalDate.now().getYear();

        BigDecimal pris = leaveRepo.sumApprovedDaysByYear(empId, year);
        long enAttente = leaveRepo.countByEmployeeIdAndStatus(empId, "EN_ATTENTE");
        int joursUtilises = pris != null ? pris.intValue() : 0;

        Map<String, Object> solde = new LinkedHashMap<>();
        solde.put("soldeTotal",       CONGES_ANNUELS_TOTAL);
        solde.put("soldeUtilise",     joursUtilises);
        solde.put("soldeRestant",     CONGES_ANNUELS_TOTAL - joursUtilises);
        solde.put("demandesEnAttente", enAttente);
        return solde;
    }

    /** Toutes les demandes de congé avec un statut Oracle donné — vue RH. */
    @Transactional(readOnly = true)
    public List<DemandeResponse> getCongesParStatut(String oracleStatus) {
        return leaveRepo.findByStatus(oracleStatus)
                .stream().map(e -> toResponse(e, null)).toList();
    }

    /** Demandes de congé de l'employé connecté (type CONGE seulement). */
    @Transactional(readOnly = true)
    public List<DemandeResponse> getMesConges(Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        String nom = resolveNom(empId);
        return leaveRepo.findByEmployeeIdOrderByCreatedAtDesc(empId)
                .stream().map(e -> toResponse(e, nom)).toList();
    }

    /** Congés de l'équipe chevauchant la plage [dateDebut, dateFin]. */
    @Transactional(readOnly = true)
    public List<DemandeResponse> getCongesEquipe(Authentication auth,
                                                  LocalDate dateDebut,
                                                  LocalDate dateFin) {
        Long empId = resolveEmployeeId(auth);
        // Employees are not managers — look up their own manager to get the team view
        boolean isManager = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CHEF")
                            || a.getAuthority().equals("ROLE_RH")
                            || a.getAuthority().equals("ROLE_ADMIN"));
        Long managerId = isManager ? empId : employeeRepo.findManagerIdByEmployeeId(empId);
        if (managerId == null) return List.of();
        return leaveRepo.findByManagerAndDateRange(
                        managerId,
                        dateDebut.toString(),
                        dateFin.toString())
                .stream().map(e -> toResponse(e, resolveNom(e.getEmployeeId()))).toList();
    }

    /* ═══════════════════════════════════════════════════════
       LECTURE PAR ID
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public Optional<DemandeResponse> getDemandeById(Long id) {
        Optional<LeaveRequest> leave = leaveRepo.findById(id);
        if (leave.isPresent()) return Optional.of(toResponse(leave.get(), resolveNom(leave.get().getEmployeeId())));

        Optional<TrainingRequest> training = trainingRepo.findById(id);
        if (training.isPresent()) return Optional.of(toResponse(training.get(), resolveNom(training.get().getEmployeeId())));

        Optional<LoanRequest> loan = loanRepo.findById(id);
        if (loan.isPresent()) return Optional.of(toResponse(loan.get(), resolveNom(loan.get().getEmployeeId())));

        Optional<DocumentRequest> doc = documentRepo.findById(id);
        if (doc.isPresent()) return Optional.of(toResponse(doc.get(), resolveNom(doc.get().getEmployeeId())));

        Optional<AuthorizationRequest> auth = authRepo.findById(id);
        if (auth.isPresent()) return Optional.of(toResponse(auth.get(), resolveNom(auth.get().getEmployeeId())));

        return Optional.empty();
    }

    /* ═══════════════════════════════════════════════════════
       ANNULATION
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public DemandeResponse annulerDemande(Long id) {
        Optional<LeaveRequest> leave = leaveRepo.findById(id);
        if (leave.isPresent()) {
            LeaveRequest e = leave.get();
            e.setStatus("ANNULE");
            return toResponse(leaveRepo.save(e), null);
        }
        Optional<TrainingRequest> training = trainingRepo.findById(id);
        if (training.isPresent()) {
            TrainingRequest e = training.get();
            e.setStatus("ANNULE");
            return toResponse(trainingRepo.save(e), null);
        }
        Optional<LoanRequest> loan = loanRepo.findById(id);
        if (loan.isPresent()) {
            LoanRequest e = loan.get();
            e.setStatus("ANNULE");
            return toResponse(loanRepo.save(e), null);
        }
        Optional<DocumentRequest> doc = documentRepo.findById(id);
        if (doc.isPresent()) {
            DocumentRequest e = doc.get();
            e.setStatus("ANNULE");
            return toResponse(documentRepo.save(e), null);
        }
        Optional<AuthorizationRequest> auth = authRepo.findById(id);
        if (auth.isPresent()) {
            AuthorizationRequest e = auth.get();
            e.setStatus("ANNULE");
            return toResponse(authRepo.save(e), null);
        }
        throw new IllegalArgumentException("Demande introuvable : " + id);
    }

    /* ═══════════════════════════════════════════════════════
       VALIDATION GÉNÉRIQUE (auto-détection du type)
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public DemandeResponse validerGenerique(Long id, ValidationRequest validation, Authentication auth) {
        TypeDemande type = detectType(id);
        if (type == null) throw new IllegalArgumentException("Demande introuvable : " + id);
        return valider(id, type, validation, auth);
    }

    private TypeDemande detectType(Long id) {
        if (leaveRepo.existsById(id))    return TypeDemande.CONGE;
        if (trainingRepo.existsById(id)) return TypeDemande.FORMATION;
        if (loanRepo.existsById(id))     return TypeDemande.PRET;
        if (documentRepo.existsById(id)) return TypeDemande.DOCUMENT;
        if (authRepo.existsById(id))     return TypeDemande.AUTORISATION;
        return null;
    }

    private String resolveNom(Long employeeId) {
        try { return employeeInfoHelper.findFullName(employeeId); } catch (Exception e) { return null; }
    }

    private record EmpInfo(String prenom, String nom, String photo) {}

    private EmpInfo resolveEmpInfo(Long empId) {
        if (empId == null) return new EmpInfo(null, null, null);
        try {
            return employeeInfoHelper.findById(empId)
                .map(e -> new EmpInfo(e.getFirstName(), e.getLastName(), e.getPhotoUrl()))
                .orElse(new EmpInfo(null, null, null));
        } catch (Exception e) {
            log.warn("[resolveEmpInfo] Cannot load employee info for id={}: {}", empId, e.getMessage());
            return new EmpInfo(null, null, null);
        }
    }

    private String initiales(String prenom, String nom) {
        char p = (prenom != null && !prenom.isEmpty()) ? Character.toUpperCase(prenom.charAt(0)) : '-';
        char n = (nom    != null && !nom.isEmpty())    ? Character.toUpperCase(nom.charAt(0))    : '-';
        return "" + p + n;
    }

    /* ═══════════════════════════════════════════════════════
       RÉSOLUTION D'IDENTITÉ
       ═══════════════════════════════════════════════════════ */

    /**
     * Résout l'employee_id Oracle depuis le JWT Keycloak.
     * Niveau 1 : via user_id = sub (UUID Keycloak)
     * Niveau 2 : via email
     * Niveau 3 : via preferred_username (comme email ou comme user_id)
     */
    private Long resolveEmployeeId(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) throw new IllegalStateException("JWT introuvable");

        String sub   = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String username = jwt.getClaimAsString("preferred_username");

        // All lookups use NOT_SUPPORTED helpers so any DB error (duplicate rows, missing table, etc.)
        // cannot mark the caller's @Transactional as rollback-only.
        Long empId = employeeInfoHelper.findEmployeeIdBySub(sub);
        if (empId != null) return empId;

        if (email != null) {
            empId = employeeInfoHelper.findEmployeeIdByEmailSafe(email);
            if (empId != null) return empId;
        }

        if (username != null) {
            if (username.contains("@")) {
                empId = employeeInfoHelper.findEmployeeIdByEmailSafe(username);
                if (empId != null) return empId;
            }
            empId = employeeInfoHelper.findEmployeeIdBySub(username);
            if (empId != null) return empId;
        }

        log.error("[resolveEmployeeId] Aucun employé ACTIF pour sub={} / email={} / username={}", sub, email, username);
        throw new IllegalStateException(
                "Aucun employé ACTIF trouvé pour sub=" + sub + " / email=" + email + " / username=" + username);
    }

    /** Same as resolveEmployeeId but returns null instead of throwing — for nullable FK contexts (e.g. APPROVED_BY). */
    private Long tryResolveEmployeeId(Authentication auth) {
        try {
            return resolveEmployeeId(auth);
        } catch (IllegalStateException e) {
            log.warn("[resolveEmployeeId] Admin without employee record: {}", e.getMessage());
            return null;
        }
    }

    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) return jwtAuth.getToken();
        return null;
    }

    /** Extrait le nom complet de l'agent connecté depuis les claims Keycloak. */
    private String extractFullNameFromToken(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) return null;
        String firstName = jwt.getClaimAsString("given_name");
        String lastName  = jwt.getClaimAsString("family_name");
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        String fullName = jwt.getClaimAsString("name");
        if (fullName != null) return fullName;
        return jwt.getClaimAsString("preferred_username");
    }

    /* ═══════════════════════════════════════════════════════
       MAPPERS toResponse()
       Convertit chaque entité → DTO de réponse unifié
       ═══════════════════════════════════════════════════════ */

    private DemandeResponse toResponse(LeaveRequest e, String ignoredNom) {
        EmpInfo emp      = resolveEmpInfo(e.getEmployeeId());
        String chefNom   = e.getApprovedBy()       != null ? resolveNom(e.getApprovedBy()) : null;
        String rhNom     = e.getApprovedByRhName();
        String desc      = e.getReason() != null ? e.getReason() : "Congé";
        String leaveTypeName = leaveQuotaService.leaveTypeIdToCode(e.getLeaveTypeId());
        return DemandeResponse.builder()
                .requestId(e.getRequestId()).id(e.getRequestId())
                .type(TypeDemande.CONGE)
                .leaveTypeName(leaveTypeName)
                .statut(StatutDemande.fromOracleStatus(e.getStatus())).statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId()).employeId(e.getEmployeeId() != null ? String.valueOf(e.getEmployeeId()) : null)
                .employeNom(emp.nom()).employePrenom(emp.prenom())
                .employeInitiales(initiales(emp.prenom(), emp.nom())).employePhoto(emp.photo())
                .description(desc).reason(e.getReason())
                .leaveTypeId(e.getLeaveTypeId())
                .startDate(e.getStartDate()).endDate(e.getEndDate()).daysCount(e.getDaysCount())
                .attachmentUrl(e.getAttachmentUrl())
                .approvedBy(e.getApprovedBy()).approvedAt(e.getApprovedAt())
                .rejectionReason(e.getRejectionReason())
                .createdAt(e.getCreatedAt())
                .dateCreation(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .dateDebut(e.getStartDate()    != null ? e.getStartDate().toString()  : null)
                .dateFin(e.getEndDate()        != null ? e.getEndDate().toString()    : null)
                .joursOuvres(e.getDaysCount()  != null ? e.getDaysCount().intValue()  : null)
                .validePar(rhNom != null ? rhNom : chefNom)
                .validateurNom(rhNom != null ? rhNom : chefNom)
                .validateurNomChef(chefNom)
                .validateurNomRh(rhNom)
                .dateValidation(e.getApprovedAt()    != null ? e.getApprovedAt().toString()    : null)
                .dateValidationRh(e.getApprovedAtRh() != null ? e.getApprovedAtRh().toString() : null)
                .commentaireChef(null).commentaireRh(e.getRejectionReason())
                .halfSalary(e.getHalfSalary())
                .medApproved(e.getMedApproved())
                .medComment(e.getMedComment())
                .dateValidationMed(e.getMedApprovedAt() != null ? e.getMedApprovedAt().toString() : null)
                .build();
    }

    private DemandeResponse toResponse(TrainingRequest e, String ignoredNom) {
        EmpInfo emp    = resolveEmpInfo(e.getEmployeeId());
        String chefNom = e.getApprovedBy()       != null ? resolveNom(e.getApprovedBy()) : null;
        String rhNom   = e.getApprovedByRhName();
        String desc    = e.getTrainingTitle() != null ? e.getTrainingTitle() : "Formation";
        return DemandeResponse.builder()
                .requestId(e.getRequestId()).id(e.getRequestId())
                .type(TypeDemande.FORMATION)
                .statut(StatutDemande.fromOracleStatus(e.getStatus())).statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId()).employeId(e.getEmployeeId() != null ? String.valueOf(e.getEmployeeId()) : null)
                .employeNom(emp.nom()).employePrenom(emp.prenom())
                .employeInitiales(initiales(emp.prenom(), emp.nom())).employePhoto(emp.photo())
                .description(desc).reason(e.getReason())
                .trainingTitle(e.getTrainingTitle()).provider(e.getProvider())
                .estimatedCost(e.getEstimatedCost()).plannedDate(e.getPlannedDate())
                .durationDays(e.getDurationDays())
                .approvedBy(e.getApprovedBy())
                .createdAt(e.getCreatedAt())
                .dateCreation(e.getCreatedAt()    != null ? e.getCreatedAt().toString()    : null)
                .dateDebut(e.getPlannedDate()     != null ? e.getPlannedDate().toString()  : null)
                .dateFin(null)
                .joursOuvres(e.getDurationDays())
                .validePar(rhNom != null ? rhNom : chefNom)
                .validateurNom(rhNom != null ? rhNom : chefNom)
                .validateurNomChef(chefNom)
                .validateurNomRh(rhNom)
                .dateValidation(null)
                .dateValidationRh(e.getApprovedAtRh() != null ? e.getApprovedAtRh().toString() : null)
                .commentaireChef(null).commentaireRh(null)
                .build();
    }

    private DemandeResponse toResponse(LoanRequest e, String ignoredNom) {
        EmpInfo emp      = resolveEmpInfo(e.getEmployeeId());
        String chefNom   = e.getApprovedByRhName();      // RH who sent to committee (stored in approvedByRhName)
        String dgNom     = e.getDgApprovedByName();      // committee/DG decision maker
        String rhFinalNom = e.getFinalApprovedByName();  // HR final validation
        String desc      = e.getReason() != null ? e.getReason()
                         : "Crédit de " + (e.getAmount() != null ? e.getAmount() : "") + " " + (e.getCurrency() != null ? e.getCurrency() : "TND");
        return DemandeResponse.builder()
                .requestId(e.getRequestId()).id(e.getRequestId())
                .type(TypeDemande.PRET)
                .statut(StatutDemande.fromOracleStatus(e.getStatus())).statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId()).employeId(e.getEmployeeId() != null ? String.valueOf(e.getEmployeeId()) : null)
                .employeNom(emp.nom()).employePrenom(emp.prenom())
                .employeInitiales(initiales(emp.prenom(), emp.nom())).employePhoto(emp.photo())
                .description(desc).reason(e.getReason())
                .amount(e.getAmount()).currency(e.getCurrency())
                .durationMonths(e.getDurationMonths()).monthlyPayment(e.getMonthlyPayment())
                .montantApprouve(e.getMontantApprouve()).nbTranches(e.getNbTranches())
                .montantTranche(e.getMontantTranche())
                .dgApprovedBy(e.getDgApprovedBy()).dgDecisionAt(e.getDgDecisionAt()).dgComment(e.getDgComment())
                .approvedBy(e.getApprovedBy()).approvedAt(e.getApprovedAt())
                .rejectionReason(e.getRejectionReason())
                .createdAt(e.getCreatedAt())
                .dateCreation(e.getCreatedAt()   != null ? e.getCreatedAt().toString() : null)
                .dateDebut(null).dateFin(null).joursOuvres(e.getDurationMonths())
                .validePar(rhFinalNom != null ? rhFinalNom : dgNom != null ? dgNom : chefNom)
                .validateurNom(rhFinalNom != null ? rhFinalNom : dgNom != null ? dgNom : chefNom)
                .validateurNomChef(chefNom)
                .validateurNomDg(dgNom)
                .validateurNomRh(rhFinalNom)
                .dateValidation(e.getApprovedAtRh()      != null ? e.getApprovedAtRh().toString()      : null)
                .dateValidationDg(e.getDgDecisionAt()    != null ? e.getDgDecisionAt().toString()      : null)
                .dateValidationRh(e.getFinalApprovedAt() != null ? e.getFinalApprovedAt().toString()   : null)
                .commentaireChef(null).commentaireRh(e.getDgComment() != null ? e.getDgComment() : e.getRejectionReason())
                .build();
    }

    private DemandeResponse toResponse(DocumentRequest e, String ignoredNom) {
        EmpInfo emp  = resolveEmpInfo(e.getEmployeeId());
        String vNom  = e.getProcessedBy() != null ? resolveNom(e.getProcessedBy()) : null;
        String desc  = e.getReason() != null ? e.getReason() : "Demande de document";
        return DemandeResponse.builder()
                .requestId(e.getRequestId()).id(e.getRequestId())
                .type(TypeDemande.DOCUMENT)
                .statut(StatutDemande.fromOracleStatus(e.getStatus())).statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId()).employeId(e.getEmployeeId() != null ? String.valueOf(e.getEmployeeId()) : null)
                .employeNom(emp.nom()).employePrenom(emp.prenom())
                .employeInitiales(initiales(emp.prenom(), emp.nom())).employePhoto(emp.photo())
                .description(desc).reason(e.getReason())
                .docTypeId(e.getDocTypeId()).copiesCount(e.getCopiesCount())
                .language(e.getLanguage()).documentUrl(e.getDocumentUrl())
                .processedBy(e.getProcessedBy()).processedAt(e.getProcessedAt())
                .createdAt(e.getCreatedAt())
                .dateCreation(e.getCreatedAt()   != null ? e.getCreatedAt().toString()   : null)
                .dateDebut(null).dateFin(null).joursOuvres(e.getCopiesCount())
                .validePar(vNom)
                .validateurNom(vNom)
                .dateValidation(e.getProcessedAt() != null ? e.getProcessedAt().toString() : null)
                .commentaireChef(null).commentaireRh(null)
                .build();
    }

    private DemandeResponse toResponse(AuthorizationRequest e, String ignoredNom) {
        EmpInfo emp  = resolveEmpInfo(e.getEmployeeId());
        String vNom  = e.getApprovedBy() != null ? resolveNom(e.getApprovedBy()) : null;
        String desc  = e.getReason() != null ? e.getReason() : "Autorisation";
        LocalDate debut = e.getStartDatetime() != null ? e.getStartDatetime().toLocalDate() : null;
        LocalDate fin   = e.getEndDatetime()   != null ? e.getEndDatetime().toLocalDate()   : null;
        return DemandeResponse.builder()
                .requestId(e.getRequestId()).id(e.getRequestId())
                .type(TypeDemande.AUTORISATION)
                .statut(StatutDemande.fromOracleStatus(e.getStatus())).statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId()).employeId(e.getEmployeeId() != null ? String.valueOf(e.getEmployeeId()) : null)
                .employeNom(emp.nom()).employePrenom(emp.prenom())
                .employeInitiales(initiales(emp.prenom(), emp.nom())).employePhoto(emp.photo())
                .description(desc).reason(e.getReason())
                .startDatetime(e.getStartDatetime()).endDatetime(e.getEndDatetime())
                .durationHours(e.getDurationHours())
                .approvedBy(e.getApprovedBy()).approvedAt(e.getApprovedAt())
                .createdAt(e.getCreatedAt())
                .dateCreation(e.getCreatedAt()  != null ? e.getCreatedAt().toString() : null)
                .dateDebut(debut != null ? debut.toString() : null)
                .dateFin(fin   != null ? fin.toString()   : null)
                .joursOuvres(e.getDurationHours() != null ? e.getDurationHours().intValue() : null)
                .validePar(vNom)
                .validateurNom(vNom)
                .dateValidation(e.getApprovedAt() != null ? e.getApprovedAt().toString() : null)
                .commentaireChef(null).commentaireRh(null)
                .build();
    }
}