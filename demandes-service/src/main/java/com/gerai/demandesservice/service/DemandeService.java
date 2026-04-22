package com.gerai.demandesservice.service;

import com.gerai.demandesservice.dto.*;
import com.gerai.demandesservice.model.*;
import com.gerai.demandesservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    private final LeaveRequestRepository         leaveRepo;
    private final TrainingRequestRepository      trainingRepo;
    private final LoanRequestRepository          loanRepo;
    private final DocumentRequestRepository      documentRepo;
    private final AuthorizationRequestRepository authRepo;
    private final EmployeeRepository             employeeRepo;
    private final KafkaTemplate<String, NotificationMessage> kafkaTemplate;

    @Value("${app.kafka.topic.notifications:notification-events}")
    private String notificationsTopic;

    /* ═══════════════════════════════════════════════════════
       CRÉATION — dispatch selon TypeDemande
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public DemandeResponse creerDemande(DemandeRequest request, Authentication auth) {
        Long employeeId = resolveEmployeeId(auth);
        String employeNom = employeeRepo.findFullNameByEmployeeId(employeeId);

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
        LeaveRequest entity = LeaveRequest.builder()
                .employeeId(empId)
                .leaveTypeId(req.getLeaveTypeId() != null ? req.getLeaveTypeId() : 1L)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .daysCount(req.getDaysCount())
                .reason(req.getReason())
                .attachmentUrl(req.getAttachmentUrl())
                .status("EN_ATTENTE")
                .build();
        entity = leaveRepo.save(entity);

        notifierChef(empId, empNom, entity.getRequestId(), TypeDemande.CONGE, auth);
        return toResponse(entity, empNom);
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
                .reason(req.getReason())
                .status("EN_ATTENTE")
                .build();
        entity = trainingRepo.save(entity);

        notifierChef(empId, empNom, entity.getRequestId(), TypeDemande.FORMATION, auth);
        return toResponse(entity, empNom);
    }

    private DemandeResponse creerPret(DemandeRequest req, Long empId,
                                      String empNom, Authentication auth) {
        LoanRequest entity = LoanRequest.builder()
                .employeeId(empId)
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "TND")
                .durationMonths(req.getDurationMonths())
                .reason(req.getReason())
                .status("EN_ATTENTE")
                .build();
        entity = loanRepo.save(entity);

        notifierRh(empId, empNom, entity.getRequestId(), TypeDemande.PRET, auth);
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
        AuthorizationRequest entity = AuthorizationRequest.builder()
                .employeeId(empId)
                .startDatetime(req.getStartDatetime())
                .endDatetime(req.getEndDatetime())
                .durationHours(req.getDurationHours())
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

        all.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return all;
    }

    /* ═══════════════════════════════════════════════════════
       LECTURE — espace Chef (demandes de son équipe)
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<DemandeResponse> getDemandesEquipe(Authentication auth) {
        Long managerEmpId = resolveEmployeeId(auth);
        List<DemandeResponse> all = new ArrayList<>();

        // Congés en attente de validation Chef
        leaveRepo.findByManager(managerEmpId)
                .forEach(e -> all.add(toResponse(e, null)));
        trainingRepo.findByManager(managerEmpId)
                .forEach(e -> all.add(toResponse(e, null)));
        authRepo.findByManager(managerEmpId, "EN_ATTENTE")
                .forEach(e -> all.add(toResponse(e, null)));

        all.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
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

        return all;
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

        all.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return all;
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
        Long valideurEmpId = resolveEmployeeId(auth);

        // 1. Détecter si l'utilisateur est RH ou ADMIN
        boolean isRhOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RH") || a.getAuthority().equals("ROLE_ADMIN"));

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

        // 4. Dispatch vers les méthodes privées (Notez le nouvel argument statutTarget)
        return switch (type) {
            case CONGE        -> validerConge(requestId, oracleStatus, validation, statutTarget, valideurEmpId, auth);
            case FORMATION    -> validerFormation(requestId, oracleStatus, validation, statutTarget, valideurEmpId, auth);
            case PRET         -> validerPret(requestId, oracleStatus, validation, statutTarget, valideurEmpId, auth);
            case DOCUMENT     -> validerDocument(requestId, oracleStatus, validation, statutTarget, valideurEmpId, auth);
            case AUTORISATION -> validerAutorisation(requestId, oracleStatus, validation, statutTarget, valideurEmpId, auth);
        };
    }

    /* ═══════════════════════════════════════════════════════
   MÉTHODES DE VALIDATION PRIVÉES (CORRIGÉES)
   ═══════════════════════════════════════════════════════ */

    private DemandeResponse validerConge(Long id, String oracleStatus, ValidationRequest val,
                                         StatutDemande statutTarget, Long valideurId, Authentication auth) {
        LeaveRequest entity = leaveRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Congé introuvable : " + id));

        entity.setStatus(oracleStatus);
        entity.setApprovedBy(valideurId);
        entity.setApprovedAt(LocalDateTime.now());
        if (val.getCommentaire() != null) entity.setRejectionReason(val.getCommentaire());

        entity = leaveRepo.save(entity);

        // On utilise statutTarget pour que la notification et le DTO soient cohérents
        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.CONGE, statutTarget, oracleStatus,
                entity.getDaysCount() != null ? entity.getDaysCount().intValue() : null, auth);

        return toResponse(entity, null);
    }

    private DemandeResponse validerFormation(Long id, String oracleStatus, ValidationRequest val,
                                             StatutDemande statutTarget, Long valideurId, Authentication auth) {
        TrainingRequest entity = trainingRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Formation introuvable : " + id));

        entity.setStatus(oracleStatus);
        entity.setApprovedBy(valideurId);
        // Note: Si ta table TRAINING possède aussi APPROVED_AT, ajoute-le ici

        entity = trainingRepo.save(entity);

        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.FORMATION, statutTarget, oracleStatus, null, auth);

        return toResponse(entity, null);
    }

    private DemandeResponse validerPret(Long id, String oracleStatus, ValidationRequest val,
                                        StatutDemande statutTarget, Long valideurId, Authentication auth) {
        LoanRequest entity = loanRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Prêt introuvable : " + id));

        entity.setStatus(oracleStatus);
        entity.setApprovedBy(valideurId);
        entity.setApprovedAt(LocalDateTime.now());
        if (val.getCommentaire() != null) entity.setRejectionReason(val.getCommentaire());

        entity = loanRepo.save(entity);

        notifierEmploye(entity.getEmployeeId(), entity.getRequestId(),
                TypeDemande.PRET, statutTarget, oracleStatus, null, auth);

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
       KAFKA — Notifications
       ═══════════════════════════════════════════════════════ */

    private void notifierChef(Long empId, String empNom, Long requestId,
                              TypeDemande type, Authentication auth) {
        String managerSub = employeeRepo.findManagerKeycloakSubByEmployeeId(empId);
        String managerEmail = employeeRepo.findManagerEmailByEmployeeId(empId);

        if (managerSub == null) {
            log.warn("[Kafka] Aucun manager trouvé pour l'employé {}", empId);
            return;
        }

        NotificationMessage msg = NotificationMessage.builder()
                .destinataireId(managerSub)
                .email(managerEmail)
                .role("CHEF")
                .titre("Nouvelle demande de " + empNom)
                .message(empNom + " a soumis une demande de type " + type.name())
                .type("EN_ATTENTE")
                .statut("EN_ATTENTE")
                .referenceId(String.valueOf(requestId))
                .typeDemande(type.name())
                .sourceService("DEMANDES-SERVICE")
                .build();

        sendKafka(msg);
    }

    private void notifierRh(Long empId, String empNom, Long requestId,
                            TypeDemande type, Authentication auth) {
        NotificationMessage msg = NotificationMessage.builder()
                .role("RH")
                .titre("Nouvelle demande de " + empNom)
                .message(empNom + " a soumis une demande de type " + type.name())
                .type("EN_ATTENTE")
                .statut("EN_ATTENTE")
                .referenceId(String.valueOf(requestId))
                .typeDemande(type.name())
                .sourceService("DEMANDES-SERVICE")
                .build();

        sendKafka(msg);
    }

    private void notifierEmploye(Long empId, Long requestId, TypeDemande type,
                                 StatutDemande statutApi, String oracleStatus,
                                 Integer nbJours, Authentication auth) {
        // Récupérer le UUID Keycloak de l'employé depuis EMPLOYEES
        Optional<EmployeeRef> empOpt = employeeRepo.findById(empId);
        String empKeycloakSub = empOpt.map(EmployeeRef::getUserId).orElse(null);
        String empEmail = empOpt.map(EmployeeRef::getEmail).orElse(null);
        String empNom = empOpt
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse("Employé");

        String titre = switch (statutApi) {
            case VALIDEE_CHEF -> "Votre demande " + type.name() + " a été validée par votre chef";
            case VALIDEE_RH   -> "Votre demande " + type.name() + " a été approuvée par RH";
            case REJETEE      -> "Votre demande " + type.name() + " a été refusée";
            case ANNULEE      -> "Votre demande " + type.name() + " a été annulée";
            default           -> "Mise à jour de votre demande " + type.name();
        };

        NotificationMessage msg = NotificationMessage.builder()
                .destinataireId(empKeycloakSub)
                .email(empEmail)
                .role("EMPLOYE")
                .titre(titre)
                .message(titre + " (Réf: " + requestId + ")")
                .type(statutApi.name())
                .statut(oracleStatus)
                .referenceId(String.valueOf(requestId))
                .typeDemande(type.name())
                .nbJours(nbJours)
                .sourceService("DEMANDES-SERVICE")
                .build();

        sendKafka(msg);
    }

    private void sendKafka(NotificationMessage msg) {
        try {
            kafkaTemplate.send(notificationsTopic, msg);
            log.info("[Kafka] Notification envoyée | type={} | dest={} | ref={}",
                    msg.getType(), msg.getDestinataireId(), msg.getReferenceId());
        } catch (Exception e) {
            log.error("[Kafka] Erreur envoi notification : {}", e.getMessage());
        }
    }

    /* ═══════════════════════════════════════════════════════
       RÉSOLUTION D'IDENTITÉ
       ═══════════════════════════════════════════════════════ */

    /**
     * Résout l'employee_id Oracle depuis le JWT Keycloak.
     * Niveau 1 : via user_id = sub (UUID Keycloak)
     * Niveau 2 : via email (fallback)
     */
    private Long resolveEmployeeId(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) throw new IllegalStateException("JWT introuvable");

        // Niveau 1 : sub Keycloak
        String sub = jwt.getSubject();
        Long empId = employeeRepo.findEmployeeIdByKeycloakSub(sub);
        if (empId != null) return empId;

        // Niveau 2 : email
        String email = jwt.getClaimAsString("email");
        if (email != null) {
            empId = employeeRepo.findEmployeeIdByEmail(email);
            if (empId != null) return empId;
        }

        throw new IllegalStateException(
                "Aucun employé ACTIF trouvé pour sub=" + sub + " / email=" + email);
    }

    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) return jwtAuth.getToken();
        return null;
    }

    /* ═══════════════════════════════════════════════════════
       MAPPERS toResponse()
       Convertit chaque entité → DTO de réponse unifié
       ═══════════════════════════════════════════════════════ */

    private DemandeResponse toResponse(LeaveRequest e, String nom) {
        return DemandeResponse.builder()
                .requestId(e.getRequestId())
                .type(TypeDemande.CONGE)
                .statut(StatutDemande.fromOracleStatus(e.getStatus()))
                .statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId())
                .employeNom(nom)
                .leaveTypeId(e.getLeaveTypeId())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .daysCount(e.getDaysCount())
                .reason(e.getReason())
                .attachmentUrl(e.getAttachmentUrl())
                .approvedBy(e.getApprovedBy())
                .approvedAt(e.getApprovedAt())
                .rejectionReason(e.getRejectionReason())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private DemandeResponse toResponse(TrainingRequest e, String nom) {
        return DemandeResponse.builder()
                .requestId(e.getRequestId())
                .type(TypeDemande.FORMATION)
                .statut(StatutDemande.fromOracleStatus(e.getStatus()))
                .statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId())
                .employeNom(nom)
                .trainingTitle(e.getTrainingTitle())
                .provider(e.getProvider())
                .estimatedCost(e.getEstimatedCost())
                .plannedDate(e.getPlannedDate())
                .durationDays(e.getDurationDays())
                .reason(e.getReason())
                .approvedBy(e.getApprovedBy())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private DemandeResponse toResponse(LoanRequest e, String nom) {
        return DemandeResponse.builder()
                .requestId(e.getRequestId())
                .type(TypeDemande.PRET)
                .statut(StatutDemande.fromOracleStatus(e.getStatus()))
                .statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId())
                .employeNom(nom)
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .durationMonths(e.getDurationMonths())
                .monthlyPayment(e.getMonthlyPayment())
                .reason(e.getReason())
                .approvedBy(e.getApprovedBy())
                .approvedAt(e.getApprovedAt())
                .rejectionReason(e.getRejectionReason())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private DemandeResponse toResponse(DocumentRequest e, String nom) {
        return DemandeResponse.builder()
                .requestId(e.getRequestId())
                .type(TypeDemande.DOCUMENT)
                .statut(StatutDemande.fromOracleStatus(e.getStatus()))
                .statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId())
                .employeNom(nom)
                .docTypeId(e.getDocTypeId())
                .reason(e.getReason())
                .copiesCount(e.getCopiesCount())
                .language(e.getLanguage())
                .documentUrl(e.getDocumentUrl())
                .processedBy(e.getProcessedBy())
                .processedAt(e.getProcessedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private DemandeResponse toResponse(AuthorizationRequest e, String nom) {
        return DemandeResponse.builder()
                .requestId(e.getRequestId())
                .type(TypeDemande.AUTORISATION)
                .statut(StatutDemande.fromOracleStatus(e.getStatus()))
                .statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId())
                .employeNom(nom)
                .startDatetime(e.getStartDatetime())
                .endDatetime(e.getEndDatetime())
                .durationHours(e.getDurationHours())
                .reason(e.getReason())
                .approvedBy(e.getApprovedBy())
                .approvedAt(e.getApprovedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}