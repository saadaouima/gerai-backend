package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant une demande de congé soumise par un employé.
 * Mappée sur la table Oracle {@code GERAI.LEAVE_REQUESTS}.
 * <p>
 * Workflow standard : EN_ATTENTE → VALIDE_CHEF → VALIDE_RH | REFUSE | ANNULE.
 * Workflow longue maladie (type 12) : EN_ETUDE_MEDICALE → EN_ATTENTE → VALIDE_CHEF → VALIDE_RH | REFUSE.
 * <p>
 * Le quota annuel est vérifié par {@link com.gerai.demandesservice.service.LeaveQuotaService}
 * et le nombre de jours ouvrés est calculé par {@link com.gerai.demandesservice.service.WorkingDayService}.
 * <p>
 * Statuts valides (CHECK Oracle) :
 *   EN_ATTENTE | VALIDE_CHEF | VALIDE_RH | REFUSE | ANNULE | EN_ETUDE_MEDICALE
 *
 * @since 1.0
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
    @Column(name = "STATUS", nullable = false, length = 30)
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

    /** FK → EMPLOYEES.employee_id — agent RH qui valide/rejette (étape 2) */
    @Column(name = "APPROVED_BY_RH")
    private Long approvedByRh;

    @Column(name = "APPROVED_AT_RH")
    private LocalDateTime approvedAtRh;

    /** Nom complet extrait du JWT Keycloak au moment de la validation RH */
    @Column(name = "APPROVED_BY_RH_NAME", length = 200)
    private String approvedByRhName;

    /** URL pièce jointe (justificatif médical, etc.) */
    @Column(name = "ATTACHMENT_URL", length = 500)
    private String attachmentUrl;

    /** Demi-salaire (congé postnatal, allaitement) — nullable pour ddl-auto:update sur table non vide */
    @Column(name = "HALF_SALARY")
    @Builder.Default
    private Boolean halfSalary = false;

    /** Décision du comité médical (congé longue maladie) */
    @Column(name = "MED_APPROVED")
    private Boolean medApproved;

    @Column(name = "MED_APPROVED_AT")
    private LocalDateTime medApprovedAt;

    @Column(name = "MED_APPROVED_BY")
    private Long medApprovedBy;

    @Column(name = "MED_COMMENT", length = 500)
    private String medComment;

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