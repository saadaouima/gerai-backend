package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité mappée sur GERAI.TRAINING_REQUESTS (11 colonnes).
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

    /** Lieu / site de la formation — VARCHAR2(200) */
    @Column(name = "LIEU", length = 200)
    private String lieu;

    /** Mode : PRESENTIEL | DISTANCIEL | HYBRIDE — VARCHAR2(20) */
    @Column(name = "MODE_FORMATION", length = 20)
    private String modeFormation;

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

    /** FK → EMPLOYEES.employee_id — chef qui valide (étape 1) */
    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    /** FK → EMPLOYEES.employee_id — agent RH qui valide/rejette (étape 2) */
    @Column(name = "APPROVED_BY_RH")
    private Long approvedByRh;

    @Column(name = "APPROVED_AT_RH")
    private LocalDateTime approvedAtRh;

    /** Nom complet extrait du JWT Keycloak au moment de la validation RH */
    @Column(name = "APPROVED_BY_RH_NAME", length = 200)
    private String approvedByRhName;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "EN_ATTENTE";
    }
}