package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité mappée sur GERAI_USER.LEAVE_REQUESTS (13 colonnes).
 *
 * Statuts valides (CHECK Oracle) :
 *   EN_ATTENTE | VALIDE_CHEF | VALIDE_RH | REFUSE | ANNULE
 */
@Entity
@Table(name = "LEAVE_REQUESTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REQUEST_ID")
    private Long requestId;

    /** FK → EMPLOYEES.employee_id (NUMBER, résolu depuis le JWT via findByKeycloakSub) */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** FK → LEAVE_TYPES.leave_type_id */
    @Column(name = "LEAVE_TYPE_ID", nullable = false)
    private Long leaveTypeId;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    /** NUMBER(4,1) — calculé ou saisi par l'employé */
    @Column(name = "DAYS_COUNT", nullable = false)
    private BigDecimal daysCount;

    /** VARCHAR2(500) — motif de la demande */
    @Column(name = "REASON", length = 500)
    private String reason;

    /**
     * VARCHAR2(20) — statut courant.
     * Valeurs : EN_ATTENTE | VALIDE_CHEF | VALIDE_RH | REFUSE | ANNULE
     */
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private String status = "EN_ATTENTE";

    /** FK → EMPLOYEES.employee_id (RH ou Chef qui a approuvé) */
    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    /** Motif du refus saisi par RH ou Chef */
    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    /** URL pièce jointe (justificatif médical, etc.) */
    @Column(name = "ATTACHMENT_URL", length = 500)
    private String attachmentUrl;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "EN_ATTENTE";
    }
}