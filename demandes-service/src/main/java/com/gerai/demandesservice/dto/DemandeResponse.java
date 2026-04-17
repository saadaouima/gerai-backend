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

    /* ── PRÊT ─────────────────────────────────────────── */
    private BigDecimal    amount;
    private String        currency;
    private Integer       durationMonths;
    private BigDecimal    monthlyPayment;

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
}