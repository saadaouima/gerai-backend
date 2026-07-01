package com.gerai.demandesservice.dto;

import com.gerai.demandesservice.model.TypeDemande;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de création reçu depuis le frontend Angular pour soumettre une demande RH.
 * <p>
 * Les champs sont optionnels selon le type de demande :
 * <ul>
 *   <li>{@code CONGE}        → startDate, endDate, daysCount, leaveTypeId</li>
 *   <li>{@code FORMATION}    → trainingTitle, provider, estimatedCost, plannedDate, durationDays</li>
 *   <li>{@code PRET}         → amount, durationMonths</li>
 *   <li>{@code DOCUMENT}     → docTypeId, copiesCount, language</li>
 *   <li>{@code AUTORISATION} → startDatetime, endDatetime, durationHours</li>
 * </ul>
 *
 * @since 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DemandeRequest {

    /** Type de la demande RH — obligatoire, détermine les champs à utiliser. */
    @NotNull(message = "Le type de demande est obligatoire")
    private TypeDemande type;

    /** Motif / justification — commun à tous les types. */
    private String reason;

    /* ── CONGÉ (LEAVE_REQUESTS) ──────────────────────── */
    /** FK → REF_TYPES_CONGE.TYPE_ID — identifiant du type de congé demandé. */
    private Long       leaveTypeId;
    /** Date de début du congé (incluse). */
    private LocalDate  startDate;
    /** Date de fin du congé (incluse). */
    private LocalDate  endDate;
    /** Nombre de jours ouvrés calculés (peut être fourni ou calculé par le service). */
    private BigDecimal daysCount;
    /** URL du justificatif médical ou document annexe (optionnel). */
    private String     attachmentUrl;

    /* ── FORMATION (TRAINING_REQUESTS) ──────────────── */
    /** Intitulé de la formation souhaitée. */
    private String     trainingTitle;
    /** Nom de l'organisme prestataire de la formation. */
    private String     provider;
    /** Coût estimé de la formation en TND. */
    private BigDecimal estimatedCost;
    /** Date prévue de début de la formation. */
    private LocalDate  plannedDate;
    /** Durée de la formation en jours. */
    private Integer    durationDays;
    /** Lieu de la formation (ville, établissement). */
    private String     lieu;
    /** Mode de la formation : {@code PRESENTIEL}, {@code DISTANCIEL} ou {@code HYBRIDE}. */
    private String     modeFormation;

    /* ── PRÊT (LOAN_REQUESTS) ────────────────────────── */
    /** Montant du prêt demandé en TND. */
    private BigDecimal amount;
    /** Devise du prêt (par défaut {@code TND}). */
    private String     currency;
    /** Durée de remboursement en mois. */
    private Integer    durationMonths;
    /** Si null, la valeur par défaut (true) s'applique — commission requise sauf indication contraire. */
    private Boolean    needsCommission;

    /* ── DOCUMENT (DOCUMENT_REQUESTS) ────────────────── */
    /** FK → DOCUMENT_TYPES.DOC_TYPE_ID — type de document administratif demandé. */
    private Long       docTypeId;
    /** Nombre d'exemplaires du document souhaité. */
    private Integer    copiesCount;
    /** Langue du document : {@code FR} (français) ou {@code AR} (arabe). */
    private String     language;

    /* ── AUTORISATION (AUTHORIZATION_REQUESTS) ───────── */
    /** Date et heure de début de l'absence autorisée. */
    private LocalDateTime startDatetime;
    /** Date et heure de fin de l'absence autorisée. */
    private LocalDateTime endDatetime;
    /** Durée de l'absence en heures (calculée automatiquement si non fournie). */
    private BigDecimal    durationHours;
}

