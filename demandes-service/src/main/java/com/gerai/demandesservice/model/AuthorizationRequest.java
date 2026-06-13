package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité mappée sur GERAI.AUTHORIZATION_REQUESTS (10 colonnes).
 *
 * Statuts valides (CHECK Oracle) :
 *   EN_ATTENTE | APPROUVE | REFUSE
 */
@Entity
@Table(name = "AUTHORIZATION_REQUESTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthorizationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REQUEST_ID")
    private Long requestId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** Début de l'absence — TIMESTAMP NN */
    @Column(name = "START_DATETIME", nullable = false)
    private LocalDateTime startDatetime;

    /** Fin de l'absence — TIMESTAMP NN */
    @Column(name = "END_DATETIME", nullable = false)
    private LocalDateTime endDatetime;

    /** Durée en heures — NUMBER(4,2) */
    @Column(name = "DURATION_HOURS")
    private BigDecimal durationHours;

    /** Motif — VARCHAR2(500) NN */
    @Column(name = "REASON", nullable = false, length = 500)
    private String reason;

    /**
     * VARCHAR2(20).
     * Valeurs : EN_ATTENTE | APPROUVE | REFUSE
     */
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private String status = "EN_ATTENTE";

    /** FK → EMPLOYEES.employee_id (Chef ou RH qui approuve) */
    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "EN_ATTENTE";
    }
}