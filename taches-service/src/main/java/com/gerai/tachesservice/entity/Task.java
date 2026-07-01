package com.gerai.tachesservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité JPA principale du microservice, mappée sur la table GERAI.TASKS.
 * <p>
 * Représente une tâche Kanban dans le système SYNAPSE. Une tâche est toujours
 * rattachée à un projet et peut être assignée à un employé. Elle dispose d'une
 * priorité, d'un statut et d'un suivi de progression.
 * <p>
 * Structure Oracle (V2__projects_tasks.sql) — 15 colonnes :
 * task_id, project_id, title, description, assigned_to, created_by,
 * parent_task_id, priority, status, progress_pct, due_date,
 * estimated_hours, actual_hours, created_at, updated_at.
 * <p>
 * Contraintes CHECK Oracle :
 * <ul>
 *   <li>Priorités : FAIBLE | NORMALE | HAUTE | CRITIQUE</li>
 *   <li>Statuts : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE</li>
 * </ul>
 * <p>
 * {@code @Entity} : déclare cette classe comme entité JPA.
 * {@code @Table(name = "TASKS")} : mappe sur la table Oracle GERAI.TASKS.
 * {@code @Builder} : permet la construction fluide des instances dans le service.
 *
 * @since 1.0
 */
@Entity
@Table(name = "TASKS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {

    /** Identifiant Oracle auto-généré de la tâche (clé primaire, TASKS.task_id). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TASK_ID")
    private Long taskId;

    /**
     * Identifiant Oracle du projet parent (TASKS.project_id).
     * Clé étrangère vers PROJECTS.project_id, non nulle.
     */
    @Column(name = "PROJECT_ID", nullable = false)
    private Long projectId;

    /**
     * Titre de la tâche (TASKS.title).
     * VARCHAR2(300), non nul.
     */
    @Column(name = "TITLE", nullable = false, length = 300)
    private String title;

    /**
     * Description détaillée de la tâche (TASKS.description).
     * Stockée en CLOB Oracle pour les contenus longs.
     */
    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    /**
     * Identifiant Oracle de l'employé assigné à la tâche (TASKS.assigned_to).
     * Clé étrangère vers EMPLOYEES.employee_id. Peut être null si la tâche n'est pas encore assignée.
     */
    @Column(name = "ASSIGNED_TO")
    private Long assignedTo;

    /**
     * Identifiant Oracle de l'employé créateur de la tâche (TASKS.created_by).
     * Clé étrangère vers EMPLOYEES.employee_id, non nulle.
     */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /**
     * Identifiant Oracle de la tâche parente (TASKS.parent_task_id).
     * Auto-référence permettant d'organiser les sous-tâches.
     * Null pour une tâche principale.
     */
    @Column(name = "PARENT_TASK_ID")
    private Long parentTaskId;

    /**
     * Niveau de priorité de la tâche (TASKS.priority).
     * VARCHAR2(20), non nul. Valeurs Oracle : FAIBLE | NORMALE | HAUTE | CRITIQUE.
     * Valeur par défaut : {@code NORMALE}.
     */
    @Column(name = "PRIORITY", nullable = false, length = 20)
    @Builder.Default
    private String priority = "NORMALE";

    /**
     * Statut courant de la tâche dans le workflow Kanban (TASKS.status).
     * VARCHAR2(30), non nul. Valeurs Oracle : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE.
     * Valeur par défaut : {@code A_FAIRE}.
     */
    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private String status = "A_FAIRE";

    /**
     * Pourcentage d'avancement de la tâche (TASKS.progress_pct).
     * NUMBER(3) Oracle, valeurs entre 0 et 100. Valeur par défaut : 0.
     */
    @Column(name = "PROGRESS_PCT")
    @Builder.Default
    private Integer progressPct = 0;

    /**
     * Date d'échéance prévue pour la tâche (TASKS.due_date).
     * Utilisée par le scheduler pour détecter les tâches en retard.
     */
    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    /**
     * Nombre d'heures estimées pour réaliser la tâche (TASKS.estimated_hours).
     * NUMBER(6,2) Oracle, optionnel.
     */
    @Column(name = "ESTIMATED_HOURS")
    private BigDecimal estimatedHours;

    /**
     * Nombre d'heures réellement passées sur la tâche (TASKS.actual_hours).
     * NUMBER(6,2) Oracle, mis à jour au fil de l'avancement.
     */
    @Column(name = "ACTUAL_HOURS")
    private BigDecimal actualHours;

    /**
     * Horodatage de création de la tâche (TASKS.created_at).
     * Initialisé automatiquement par {@link #onCreate()}, non modifiable après insertion.
     */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Horodatage de la dernière modification de la tâche (TASKS.updated_at).
     * Mis à jour automatiquement par {@link #onUpdate()} à chaque modification.
     */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /**
     * Callback JPA exécuté avant l'insertion en base.
     * Initialise {@code createdAt} à l'heure courante et applique les valeurs
     * par défaut pour {@code status}, {@code priority} et {@code progressPct}
     * si ceux-ci n'ont pas été définis explicitement.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null)   this.status = "A_FAIRE";
        if (this.priority == null) this.priority = "NORMALE";
        if (this.progressPct == null) this.progressPct = 0;
    }

    /**
     * Callback JPA exécuté avant chaque mise à jour en base.
     * Met à jour {@code updatedAt} à l'heure courante.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
