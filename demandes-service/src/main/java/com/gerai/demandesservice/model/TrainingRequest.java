package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité mappée sur GERAI_USER.TRAINING_REQUESTS (11 colonnes).
 *
 * Statuts valides (CHECK Oracle) :
 *   EN_ATTENTE | APPROUVE_CHEF | APPROUVE_RH | REFUSE | ANNULE
 */
@Entity
@Table(name = "TRAINING_REQUESTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REQUEST_ID")
    private Long requestId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** Intitulé de la formation — VARCHAR2(200) NN */
    @Column(name = "TRAINING_TITLE", nullable = false, length = 200)
    private String trainingTitle;

    /** Organisme prestataire — VARCHAR2(150) */
    @Column(name = "PROVIDER", length = 150)
    private String provider;

    /** Coût estimé en TND — NUMBER(10,2) */
    @Column(name = "ESTIMATED_COST")
    private BigDecimal estimatedCost;

    /** Date prévue de début de la formation */
    @Column(name = "PLANNED_DATE")
    private LocalDate plannedDate;

    /** Durée en jours — NUMBER(3) */
    @Column(name = "DURATION_DAYS")
    private Integer durationDays;

    /** Justification de la demande — VARCHAR2(500) */
    @Column(name = "REASON", length = 500)
    private String reason;

    /**
     * VARCHAR2(20).
     * Valeurs : EN_ATTENTE | APPROUVE_CHEF | APPROUVE_RH | REFUSE | ANNULE
     */
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private String status = "EN_ATTENTE";

    /** FK → EMPLOYEES.employee_id (RH qui a approuvé) */
    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "EN_ATTENTE";
    }
}