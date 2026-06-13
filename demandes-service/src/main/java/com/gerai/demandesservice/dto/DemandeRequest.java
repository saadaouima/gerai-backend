package com.gerai.demandesservice.dto;

import com.gerai.demandesservice.model.TypeDemande;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de création — reçu depuis le frontend Angular.
 *
 * Les champs sont optionnels selon le type de demande :
 *   CONGE        → startDate, endDate, daysCount, leaveTypeId
 *   FORMATION    → trainingTitle, provider, estimatedCost, plannedDate, durationDays
 *   PRET         → amount, durationMonths
 *   DOCUMENT     → docTypeId, copiesCount, language
 *   AUTORISATION → startDatetime, endDatetime, durationHours
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DemandeRequest {

    @NotNull(message = "Le type de demande est obligatoire")
    private TypeDemande type;

    /** Motif / justification — commun à tous les types */
    private String reason;

    /* ── CONGÉ (LEAVE_REQUESTS) ──────────────────────── */
    private Long       leaveTypeId;
    private LocalDate  startDate;
    private LocalDate  endDate;
    private BigDecimal daysCount;
    private String     attachmentUrl;

    /* ── FORMATION (TRAINING_REQUESTS) ──────────────── */
    private String     trainingTitle;
    private String     provider;
    private BigDecimal estimatedCost;
    private LocalDate  plannedDate;
    private Integer    durationDays;
    private String     lieu;
    private String     modeFormation;

    /* ── PRÊT (LOAN_REQUESTS) ────────────────────────── */
    private BigDecimal amount;
    private String     currency;
    private Integer    durationMonths;
    /** Si null, la valeur par défaut (true) s'applique — commission requise sauf indication contraire. */
    private Boolean    needsCommission;

    /* ── DOCUMENT (DOCUMENT_REQUESTS) ────────────────── */
    private Long       docTypeId;
    private Integer    copiesCount;
    private String     language;

    /* ── AUTORISATION (AUTHORIZATION_REQUESTS) ───────── */
    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;
    private BigDecimal    durationHours;
}

