package com.gerai.demandesservice.dto;

import com.gerai.demandesservice.model.StatutDemande;
import com.gerai.demandesservice.model.TypeDemande;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de réponse unifié exposé à Angular pour toutes les demandes RH (congé, formation, prêt, document, autorisation).
 * Contient les champs communs à tous les types ainsi que les champs spécifiques selon le type.
 * <p>
 * {@code @JsonInclude(NON_NULL)} : évite de polluer le JSON avec des champs vides selon le type.
 *
 * @since 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DemandeResponse {

    /* ── Commun à tous les types ──────────────────────── */
    /** Identifiant technique de la demande (clé primaire de la table Oracle cible). */
    private Long          requestId;
    /** Type de la demande RH (CONGE, FORMATION, PRET, DOCUMENT, AUTORISATION). */
    private TypeDemande   type;
    /** Statut API unifié de la demande (indépendant de la table Oracle). */
    private StatutDemande statut;
    /** Valeur brute du champ STATUS en base Oracle (pour debug et compatibilité admin). */
    private String        statusOracle;
    /** Motif ou justification de la demande. */
    private String        reason;
    /** Horodatage de création de la demande. */
    private LocalDateTime createdAt;
    /** Identifiant Oracle de l'employé auteur de la demande (EMPLOYEES.EMPLOYEE_ID). */
    private Long          employeeId;
    /** Nom complet de l'employé ({@code FIRST_NAME || ' ' || LAST_NAME}). */
    private String        employeNom;

    /* ── Validation ───────────────────────────────────── */
    /** Identifiant Oracle du valideur (chef ou RH — étape 1). */
    private Long          approvedBy;
    /** Horodatage de la validation de l'étape 1 (chef). */
    private LocalDateTime approvedAt;
    /** Motif de refus saisi par le chef ou le RH. */
    private String        rejectionReason;

    /* ── CONGÉ ────────────────────────────────────────── */
    /** FK → REF_TYPES_CONGE.TYPE_ID — identifiant du type de congé. */
    private Long          leaveTypeId;
    /** Date de début du congé (incluse). */
    private LocalDate     startDate;
    /** Date de fin du congé (incluse). */
    private LocalDate     endDate;
    /** Nombre de jours ouvrés de congé. */
    private BigDecimal    daysCount;
    /** URL du justificatif médical ou annexe. */
    private String        attachmentUrl;

    /* ── FORMATION ────────────────────────────────────── */
    /** Intitulé de la formation. */
    private String        trainingTitle;
    /** Nom de l'organisme prestataire. */
    private String        provider;
    /** Coût estimé de la formation en TND. */
    private BigDecimal    estimatedCost;
    /** Date prévue de début de la formation. */
    private LocalDate     plannedDate;
    /** Durée de la formation en jours. */
    private Integer       durationDays;

    /* ── CRÉDIT (LOAN_REQUESTS) ───────────────────────── */
    /** Montant du prêt demandé. */
    private BigDecimal    amount;
    /** Devise du prêt (ex : {@code TND}). */
    private String        currency;
    /** Durée de remboursement en mois. */
    private Integer       durationMonths;
    /** Mensualité calculée. */
    private BigDecimal    monthlyPayment;
    /** Montant finalement approuvé par le DG ou le comité (peut différer du montant demandé). */
    private BigDecimal    montantApprouve;
    /** Nombre de mensualités accordées par le DG ou le comité. */
    private Integer       nbTranches;
    /** Montant de chaque mensualité ({@code montantApprouve / nbTranches}). */
    private BigDecimal    montantTranche;
    /** Identifiant Oracle du DG ou du membre du comité ayant rendu la décision. */
    private Long          dgApprovedBy;
    /** Horodatage de la décision DG ou comité. */
    private LocalDateTime dgDecisionAt;
    /** Commentaire ou motif de décision du DG ou du comité. */
    private String        dgComment;

    /* ── DOCUMENT ─────────────────────────────────────── */
    /** FK → DOCUMENT_TYPES.DOC_TYPE_ID — type de document administratif. */
    private Long          docTypeId;
    /** Nombre d'exemplaires demandés. */
    private Integer       copiesCount;
    /** Langue du document ({@code FR} ou {@code AR}). */
    private String        language;
    /** URL du document généré par le service RH. */
    private String        documentUrl;
    /** Identifiant Oracle de l'agent RH ayant traité la demande. */
    private Long          processedBy;
    /** Horodatage du traitement de la demande par le service RH. */
    private LocalDateTime processedAt;

    /* ── AUTORISATION ─────────────────────────────────── */
    /** Date et heure de début de l'absence autorisée. */
    private LocalDateTime startDatetime;
    /** Date et heure de fin de l'absence autorisée. */
    private LocalDateTime endDatetime;
    /** Durée de l'absence en heures. */
    private BigDecimal    durationHours;

    /* ── Champs compatibles Angular (alias) ──────────── */
    /** Alias de {@code requestId} — identifiant de la demande pour le frontend Angular. */
    private Long    id;
    /** Identifiant Oracle de l'employé sous forme de chaîne (compatibilité Angular). */
    private String  employeId;
    /** Prénom de l'employé (résolu depuis EMPLOYEES). */
    private String  employePrenom;
    /** Initiales de l'employé (ex : {@code AM} pour Alice Martin). */
    private String  employeInitiales;
    /** URL de la photo de profil de l'employé. */
    private String  employePhoto;
    /** Description composite de la demande selon le type (titre formation, motif congé, etc.). */
    private String  description;
    /** Date de création de la demande au format ISO-8601 ({@code = createdAt}). */
    private String  dateCreation;
    /** Date de début au format ISO-8601 ({@code = startDate} ou {@code plannedDate}). */
    private String  dateDebut;
    /** Date de fin au format ISO-8601 ({@code = endDate}). */
    private String  dateFin;
    /** Nombre de jours ouvrés ou durée en jours ({@code = daysCount} ou {@code durationDays}). */
    private Integer joursOuvres;
    /** Nom du valideur final (chef ou RH selon l'étape). */
    private String  validePar;
    /** Nom du dernier acteur ayant modifié le statut (rétrocompatibilité). */
    private String  validateurNom;
    /** Nom du chef qui a validé (résolu depuis {@code approvedBy}). */
    private String  validateurNomChef;
    /** Nom du DG ou membre du comité ayant rendu la décision (résolu depuis {@code dgApprovedByName}). */
    private String  validateurNomDg;
    /** Nom du gestionnaire RH ayant validé en dernière étape (résolu depuis {@code approvedByRhName}). */
    private String  validateurNomRh;
    /** Horodatage de validation chef au format ISO-8601. */
    private String  dateValidation;
    /** Horodatage de la décision DG ou comité au format ISO-8601. */
    private String  dateValidationDg;
    /** Horodatage de validation RH au format ISO-8601. */
    private String  dateValidationRh;
    /** Commentaire du chef (non utilisé actuellement, prévu pour extension). */
    private String  commentaireChef;
    /** Commentaire RH ou motif de refus ({@code = rejectionReason}). */
    private String  commentaireRh;

    /* ── Congé spécifique ────────────────────────────── */
    /** {@code true} si le congé est rémunéré à demi-salaire (congé postnatal, allaitement). */
    private Boolean  halfSalary;
    /** Décision du comité médical pour un congé longue maladie ({@code true} = approuvé). */
    private Boolean  medApproved;
    /** Commentaire du comité médical pour le congé longue maladie. */
    private String   medComment;
    /** Horodatage de la décision du comité médical au format ISO-8601. */
    private String   dateValidationMed;

    /** Code du type de congé (ex : {@code MALADIE}, {@code ANNUEL}, {@code FAMILIAL}) pour le rendu frontend. */
    private String   leaveTypeName;
}