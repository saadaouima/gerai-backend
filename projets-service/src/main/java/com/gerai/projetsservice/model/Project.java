package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité JPA centrale représentant un projet géré dans la plateforme SYNAPSE.
 * <p>
 * Un projet regroupe des membres ({@link ProjectMember}) et des tâches ({@link Task}).
 * Il est créé par un chef de projet identifié par son identifiant Oracle ({@code createdBy}).
 * Le statut suit le cycle : {@code EN_ATTENTE → EN_COURS → TERMINE/EN_PAUSE}.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "PROJECTS")} : nom de la table Oracle.<br>
 * {@code @PrePersist}/{@code @PreUpdate} : horodatage automatique.
 * </p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "PROJECTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Project {

    /** Identifiant unique du projet (clé primaire générée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PROJECT_ID")
    private Long projectId;

    /** Nom du projet (obligatoire, max 200 caractères). */
    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    /** Description détaillée du projet (stockée en CLOB Oracle). */
    @Column(name = "DESCRIPTION", columnDefinition = "CLOB")
    private String description;

    /** Code court identifiant le projet (ex. {@code SYN-2024}). */
    @Column(name = "CODE", length = 30)
    private String code;

    /** Identifiant Oracle du chef de projet créateur ({@code EMPLOYEES.EMPLOYEE_ID}). */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** Identifiant du département propriétaire du projet ({@code DEPARTMENTS.DEPT_ID}). */
    @Column(name = "DEPT_ID")
    private Long deptId;

    /** Date de démarrage prévue du projet. */
    @Column(name = "START_DATE")
    private LocalDate startDate;

    /** Date de fin prévue du projet. */
    @Column(name = "END_DATE")
    private LocalDate endDate;

    /** Niveau de priorité du projet (défaut : {@code NORMALE}). */
    @Column(name = "PRIORITY", length = 20)
    @Builder.Default
    private String priority = "NORMALE";

    /** Statut du projet (défaut : {@code EN_COURS}). */
    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private String status = "EN_COURS";

    /** Pourcentage d'avancement global du projet (0-100, défaut : 0). */
    @Column(name = "PROGRESS_PCT")
    @Builder.Default
    private Integer progressPct = 0;

    /** Date et heure de création du projet (initialisée automatiquement, non modifiable). */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Date et heure de la dernière mise à jour du projet. */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** Liste des membres du projet (chargement différé, cascade totale). */
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<ProjectMember> members = new ArrayList<>();

    /** Liste des tâches du projet (chargement différé, cascade totale). */
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();

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