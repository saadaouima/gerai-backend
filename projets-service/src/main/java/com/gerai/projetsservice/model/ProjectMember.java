package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant l'appartenance d'un employé à un projet.
 * <p>
 * La contrainte d'unicité {@code uk_pm_proj_emp} garantit qu'un employé
 * ne peut être membre d'un même projet qu'une seule fois.
 * Un membre peut avoir le rôle {@code CHEF} ou {@code MEMBRE}.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "PROJECT_MEMBERS")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "PROJECT_MEMBERS",
        uniqueConstraints = @UniqueConstraint(name = "uk_pm_proj_emp",
                columnNames = {"PROJECT_ID", "EMPLOYEE_ID"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectMember {

    /** Identifiant unique de l'appartenance (clé primaire générée). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MEMBER_ID")
    private Long memberId;

    /** Référence au projet parent (chargement différé). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = false)
    private Project project;

    /** Identifiant Oracle de l'employé membre du projet. */
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /** Rôle de l'employé dans le projet (défaut : {@code MEMBRE}). */
    @Column(name = "ROLE", length = 50)
    @Builder.Default
    private String role = "MEMBRE";

    /** Date et heure d'intégration de l'employé au projet (initialisée automatiquement). */
    @Column(name = "JOINED_AT", nullable = false)
    private LocalDateTime joinedAt;

    /** Indique si la participation est active : {@code 1} = actif, {@code 0} = inactif. */
    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    /**
     * Initialise la date d'intégration avant la première persistance.
     */
    @PrePersist
    protected void onCreate() { this.joinedAt = LocalDateTime.now(); }
}