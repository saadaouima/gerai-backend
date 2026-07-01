package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant une demande de prêt/crédit soumise par un employé.
 * Mappée sur la table Oracle {@code GERAI.LOAN_REQUESTS}.
 * <p>
 * Workflow multi-niveaux :
 * EN_ATTENTE → (avis chef) → EN_ETUDE_DG → (vote comité) → VALIDEE_DG → (DG/RH) → APPROUVE | REFUSE.
 * Si {@code needsCommission = false}, le crédit passe directement à VALIDEE_DG sans vote du comité.
 * <p>
 * Le vote du comité est géré par {@link com.gerai.demandesservice.service.ComiteService} et
 * la décision finale par {@link com.gerai.demandesservice.service.DemandeService#decisionDg}.
 * <p>
 * Statuts valides (CHECK Oracle) :
 *   EN_ATTENTE | EN_ETUDE | EN_ETUDE_DG | VALIDEE_DG | APPROUVE | REFUSE | REMBOURSE
 *
 * @since 1.0
 */
@Entity
@Table(name = "LOAN_REQUESTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REQUEST_ID")
    private Long requestId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** Montant du prêt — NUMBER(12,2) NN */
    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;

    /** Devise — VARCHAR2(10) NN (ex: "TND") */
    @Column(name = "CURRENCY", nullable = false, length = 10)
    @Builder.Default
    private String currency = "TND";

    /** Durée de remboursement en mois — NUMBER(3) NN */
    @Column(name = "DURATION_MONTHS", nullable = false)
    private Integer durationMonths;

    /** Mensualité calculée — NUMBER(10,2) */
    @Column(name = "MONTHLY_PAYMENT")
    private BigDecimal monthlyPayment;

    /** Motif/justification — VARCHAR2(500) */
    @Column(name = "REASON", length = 500)
    private String reason;

    /**
     * VARCHAR2(20).
     * Valeurs : EN_ATTENTE | EN_ETUDE | APPROUVE | REFUSE | REMBOURSE
     */
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private String status = "EN_ATTENTE";

    /** FK → EMPLOYEES.employee_id */
    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    /** Motif du refus */
    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    /** FK → EMPLOYEES.employee_id — agent RH qui valide/rejette (étape 2) */
    @Column(name = "APPROVED_BY_RH")
    private Long approvedByRh;

    @Column(name = "APPROVED_AT_RH")
    private LocalDateTime approvedAtRh;

    /** Nom complet extrait du JWT Keycloak au moment de la validation RH */
    @Column(name = "APPROVED_BY_RH_NAME", length = 200)
    private String approvedByRhName;

    /* ── Champs DG (décision finale) ─────────────────── */

    /** Montant approuvé par le DG (peut différer du montant demandé) */
    @Column(name = "MONTANT_APPROUVE")
    private BigDecimal montantApprouve;

    /** Nombre de mensualités accordées */
    @Column(name = "NB_TRANCHES")
    private Integer nbTranches;

    /** Montant de chaque tranche = MONTANT_APPROUVE / NB_TRANCHES */
    @Column(name = "MONTANT_TRANCHE")
    private BigDecimal montantTranche;

    /** FK → EMPLOYEES.employee_id — DG qui a rendu la décision */
    @Column(name = "DG_APPROVED_BY")
    private Long dgApprovedBy;

    @Column(name = "DG_DECISION_AT")
    private LocalDateTime dgDecisionAt;

    /** Commentaire du DG */
    @Column(name = "DG_COMMENT", length = 500)
    private String dgComment;

    /** Nom complet extrait du JWT Keycloak au moment de la décision du comité/DG */
    @Column(name = "DG_APPROVED_BY_NAME", length = 200)
    private String dgApprovedByName;

    /** Validation finale RH après décision du comité */
    @Column(name = "FINAL_APPROVED_BY")
    private Long finalApprovedBy;

    @Column(name = "FINAL_APPROVED_AT")
    private LocalDateTime finalApprovedAt;

    @Column(name = "FINAL_APPROVED_BY_NAME", length = 200)
    private String finalApprovedByName;

    /**
     * Indique si ce prêt nécessite l'avis d'une commission de prêt (workflow étape 3).
     * true  → EN_ATTENTE → (VALIDEE_CHEF) → EN_ETUDE_DG → VALIDEE_DG → VALIDEE_RH
     * false → EN_ATTENTE → (VALIDEE_CHEF) → VALIDEE_RH  (validation directe Direction RH)
     */
    @Column(name = "NEEDS_COMMISSION", nullable = false)
    @Builder.Default
    private boolean needsCommission = true;

    /** Horodatage de création de la demande — positionné par {@code @PrePersist}. */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Initialise {@code createdAt} à l'heure courante et le statut à {@code EN_ATTENTE} si null,
     * avant l'insertion JPA.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "EN_ATTENTE";
    }
}