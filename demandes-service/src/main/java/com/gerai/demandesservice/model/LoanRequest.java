package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité mappée sur GERAI_USER.LOAN_REQUESTS (12 colonnes).
 *
 * Statuts valides (CHECK Oracle) :
 *   EN_ATTENTE | EN_ETUDE | APPROUVE | REFUSE | REMBOURSE
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

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "EN_ATTENTE";
    }
}