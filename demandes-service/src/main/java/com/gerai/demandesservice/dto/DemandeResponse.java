package com.gerai.demandesservice.dto;

import com.gerai.demandesservice.model.StatutDemande;
import com.gerai.demandesservice.model.TypeDemande;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de réponse — exposé à Angular.
 * Contient les champs communs + les champs spécifiques selon le type.
 *
 * @JsonInclude(NON_NULL) évite de polluer le JSON avec des champs vides.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DemandeResponse {

    /* ── Commun à tous les types ──────────────────────── */
    private Long          requestId;
    private TypeDemande   type;
    private StatutDemande statut;
    private String        statusOracle;   // valeur brute Oracle pour debug
    private String        reason;
    private LocalDateTime createdAt;
    private Long          employeeId;
    private String        employeNom;     // first_name || ' ' || last_name

    /* ── Validation ───────────────────────────────────── */
    private Long          approvedBy;
    private LocalDateTime approvedAt;
    private String        rejectionReason;

    /* ── CONGÉ ────────────────────────────────────────── */
    private Long          leaveTypeId;
    private LocalDate     startDate;
    private LocalDate     endDate;
    private BigDecimal    daysCount;
    private String        attachmentUrl;

    /* ── FORMATION ────────────────────────────────────── */
    private String        trainingTitle;
    private String        provider;
    private BigDecimal    estimatedCost;
    private LocalDate     plannedDate;
    private Integer       durationDays;

    /* ── CRÉDIT (LOAN_REQUESTS) ───────────────────────── */
    private BigDecimal    amount;
    private String        currency;
    private Integer       durationMonths;
    private BigDecimal    monthlyPayment;
    private BigDecimal    montantApprouve;
    private Integer       nbTranches;
    private BigDecimal    montantTranche;
    private Long          dgApprovedBy;
    private LocalDateTime dgDecisionAt;
    private String        dgComment;

    /* ── DOCUMENT ─────────────────────────────────────── */
    private Long          docTypeId;
    private Integer       copiesCount;
    private String        language;
    private String        documentUrl;
    private Long          processedBy;
    private LocalDateTime processedAt;

    /* ── AUTORISATION ─────────────────────────────────── */
    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;
    private BigDecimal    durationHours;

    /* ── Angular-compatible field names ──────────────── */
    private Long    id;               // = requestId
    private String  employeId;        // = employeeId as string
    private String  employePrenom;
    private String  employeInitiales;
    private String  employePhoto;
    private String  description;      // composite per type
    private String  dateCreation;     // = createdAt ISO string
    private String  dateDebut;        // = startDate / plannedDate
    private String  dateFin;          // = endDate
    private Integer joursOuvres;      // = daysCount / durationDays
    private String  validePar;           // = approvedBy as string (chef)
    private String  validateurNom;       // last actor name (backward compat)
    private String  validateurNomChef;   // resolved from approvedBy (chef step)
    private String  validateurNomDg;     // resolved from dgApprovedByName (committee/DG step)
    private String  validateurNomRh;     // resolved from approvedByRh (RH step)
    private String  dateValidation;      // = approvedAt ISO string (chef)
    private String  dateValidationDg;    // = dgDecisionAt ISO string (committee/DG step)
    private String  dateValidationRh;    // = approvedAtRh ISO string (RH)
    private String  commentaireChef;
    private String  commentaireRh;    // = rejectionReason

    /* ── Congé spécifique ────────────────────────────── */
    private Boolean  halfSalary;
    private Boolean  medApproved;
    private String   medComment;
    private String   dateValidationMed;

    /** Code du type de congé (MALADIE, ANNUEL, FAMILIAL…) pour le rendu frontend */
    private String   leaveTypeName;
}