package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant une tâche appartenant à un projet.
 * <p>
 * Une tâche peut être assignée à un employé, avoir une tâche parente (sous-tâche),
 * et suit un workflow de statuts : {@code A_FAIRE → EN_COURS → TERMINEE}.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "TASKS")} : nom de la table Oracle.<br>
 * {@code @PrePersist}/{@code @PreUpdate} : horodatage automatique.
 * </p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "TASKS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {

    /** Identifiant unique de la tâche (clé primaire générée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TASK_ID")
    private Long taskId;

    /** Référence au projet parent (chargement différé). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = false)
    private Project project;

    /** Titre de la tâche (obligatoire, max 300 caractères). */
    @Column(name = "TITLE", nullable = false, length = 300)
    private String title;

    /** Description détaillée de la tâche (stockée en CLOB Oracle). */
    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    /** Identifiant Oracle de l'employé assigné à la tâche (FK EMPLOYEES). */
    @Column(name = "ASSIGNED_TO")
    private Long assignedTo;

    /** Identifiant Oracle de l'employé créateur de la tâche (FK EMPLOYEES). */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** Référence à la tâche parente pour la gestion des sous-tâches (auto-référence). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_TASK_ID")
    private Task parentTask;

    /** Niveau de priorité de la tâche (défaut : {@code NORMALE}). */
    @Column(name = "PRIORITY", length = 20)
    @Builder.Default
    private String priority = "NORMALE";

    /** Statut courant de la tâche (défaut : {@code A_FAIRE}). */
    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private String status = "A_FAIRE";

    /** Pourcentage d'avancement de la tâche (0-100, défaut : 0). */
    @Column(name = "PROGRESS_PCT")
    @Builder.Default
    private Integer progressPct = 0;

    /** Date d'échéance de la tâche. */
    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    /** Estimation initiale du temps de réalisation en heures. */
    @Column(name = "ESTIMATED_HOURS")
    private Double estimatedHours;

    /** Temps réellement passé sur la tâche en heures. */
    @Column(name = "ACTUAL_HOURS")
    private Double actualHours;

    /** Date et heure de création de la tâche (initialisée automatiquement, non modifiable). */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Date et heure de la dernière mise à jour de la tâche. */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /**
     * Initialise la date de création avant la première persistance.
     */
    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    /**
     * Met à jour la date de modification avant chaque mise à jour.
     */
    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}