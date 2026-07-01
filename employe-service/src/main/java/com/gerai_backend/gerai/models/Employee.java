package com.gerai_backend.gerai.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant un employé de la plateforme Synapse,
 * mappée sur la table {@code EMPLOYEES} de la base Oracle 23ai.
 *
 * <p>@Entity : indique à Hibernate que cette classe est une entité JPA persistée en base.</p>
 * <p>@Table(name = "EMPLOYEES") : spécifie le nom de la table Oracle cible.</p>
 *
 * <p>La clé primaire est un {@code NUMBER IDENTITY} Oracle (auto-incrémentée),
 * plus un UUID Keycloak stocké dans la colonne {@code USER_ID}.</p>
 *
 * @since 1.0
 */
@Entity
@Table(name = "EMPLOYEES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    /* ── PK ────────────────────────────────────────────── */

    /** Clé primaire Oracle générée par IDENTITY (NUMBER auto-incrémenté). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EMPLOYEE_ID", nullable = false, updatable = false)
    private Long id;

    /* ── Lien Keycloak ────────────────────────────────── */

    /** UUID Keycloak. Colonne : USER_ID VARCHAR2(255) UNIQUE NOT NULL */
    @Column(name = "USER_ID", nullable = false, unique = true, length = 255)
    private String keycloakUserId;

    /* ── Matricule RH ─────────────────────────────────── */

    /** Ex : EMP-0042. Généré dans EmployeeService.createEmployee(). */
    @Column(name = "EMPLOYEE_CODE", nullable = false, unique = true, length = 30)
    private String employeeCode;

    /* ── Identité ─────────────────────────────────────── */

    /** Prénom de l'employé. */
    @Column(name = "FIRST_NAME", nullable = false, length = 100)
    private String firstName;

    /** Nom de famille de l'employé. */
    @Column(name = "LAST_NAME", nullable = false, length = 100)
    private String lastName;

    /** Adresse email professionnelle, unique dans toute la plateforme. */
    @Column(name = "EMAIL", nullable = false, unique = true, length = 255)
    private String email;

    /** Numéro de téléphone de l'employé (optionnel). */
    @Column(name = "PHONE", length = 30)
    private String phone;

    /** Date de naissance de l'employé (optionnelle). */
    @Column(name = "BIRTH_DATE")
    private LocalDate birthDate;

    /** Numéro de carte d'identité nationale (optionnel, unique). */
    @Column(name = "NATIONAL_ID", unique = true, length = 50)
    private String nationalId;

    /** Genre de l'employé : {@code M}, {@code F} ou {@code AUTRE}. */
    @Column(name = "GENDER", length = 10)
    private String gender;

    /** Adresse postale de l'employé (optionnelle). */
    @Column(name = "ADDRESS", length = 500)
    private String address;

    /** URL publique de la photo de profil de l'employé (optionnelle). */
    @Column(name = "PHOTO_URL", length = 500)
    private String photoUrl;

    /* ── Rattachement organisationnel ─────────────────── */

    /** FK → DEPARTMENTS.dept_id. Obligatoire. */
    @Column(name = "DEPT_ID", nullable = false)
    private Long deptId;

    /** FK → POSITIONS.position_id. Obligatoire. */
    @Column(name = "POSITION_ID", nullable = false)
    private Long positionId;

    /** FK → EMPLOYEES.employee_id (auto-référence). Optionnel. */
    @Column(name = "MANAGER_ID")
    private Long managerId;

    /* ── Contrat / Statut ─────────────────────────────── */

    /** Date d'embauche de l'employé. */
    @Column(name = "HIRE_DATE", nullable = false)
    private LocalDate hireDate;

    /**
     * Statut RH de l'employé — contrainte CHECK dans {@code V1__core_hr.sql}.
     * Valeurs possibles : {@code ACTIF}, {@code INACTIF}, {@code SUSPENDU}, {@code DEMISSION}.
     */
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIF";

    /* ── Audit ────────────────────────────────────────── */

    /** Horodatage de création de l'enregistrement (non modifiable après insertion). */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Horodatage de la dernière modification de l'enregistrement. */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /**
     * Callback JPA exécuté avant chaque insertion en base.
     * Initialise {@code createdAt} à l'horodatage courant et applique la valeur par défaut du statut.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "ACTIF";
    }

    /**
     * Callback JPA exécuté avant chaque mise à jour en base.
     * Met à jour {@code updatedAt} à l'horodatage courant.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}