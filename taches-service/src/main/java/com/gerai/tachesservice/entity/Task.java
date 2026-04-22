package com.gerai.tachesservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité mappée sur GERAI_USER.TASKS (15 colonnes — V2__projects_tasks.sql).
 *
 * Priorités (CHECK) : FAIBLE | NORMALE | HAUTE | CRITIQUE
 * Statuts   (CHECK) : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE
 */
@Entity
@Table(name = "TASKS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TASK_ID")
    private Long taskId;

    /** FK → PROJECTS.project_id NN */
    @Column(name = "PROJECT_ID", nullable = false)
    private Long projectId;

    /** VARCHAR2(300) NN */
    @Column(name = "TITLE", nullable = false, length = 300)
    private String title;

    /** CLOB */
    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    /** FK → EMPLOYEES.employee_id (null = non assigné) */
    @Column(name = "ASSIGNED_TO")
    private Long assignedTo;

    /** FK → EMPLOYEES.employee_id NN (créateur) */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** Auto-référence pour les sous-tâches */
    @Column(name = "PARENT_TASK_ID")
    private Long parentTaskId;

    /**
     * VARCHAR2(20) NN DEFAULT 'NORMALE'
     * Valeurs : FAIBLE | NORMALE | HAUTE | CRITIQUE
     */
    @Column(name = "PRIORITY", nullable = false, length = 20)
    @Builder.Default
    private String priority = "NORMALE";

    /**
     * VARCHAR2(30) NN DEFAULT 'A_FAIRE'
     * Valeurs : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE
     */
    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private String status = "A_FAIRE";

    /** NUMBER(3) DEFAULT 0 — 0 à 100 */
    @Column(name = "PROGRESS_PCT")
    @Builder.Default
    private Integer progressPct = 0;

    /** DATE */
    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    /** NUMBER(6,2) */
    @Column(name = "ESTIMATED_HOURS")
    private BigDecimal estimatedHours;

    /** NUMBER(6,2) */
    @Column(name = "ACTUAL_HOURS")
    private BigDecimal actualHours;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null)   this.status = "A_FAIRE";
        if (this.priority == null) this.priority = "NORMALE";
        if (this.progressPct == null) this.progressPct = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
