package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

/**
 * Vue JPA légère en lecture seule sur la table {@code GERAI.EMPLOYEES}.
 * <p>
 * {@code @Immutable} : Hibernate n'effectue aucun dirty-check sur cette entité,
 * ce qui améliore les performances car elle n'est utilisée qu'en lecture.
 * <p>
 * Utilisée exclusivement pour résoudre les identités Oracle depuis le JWT Keycloak :
 * {@code sub} → {@code EMPLOYEE_ID}, ou email → {@code EMPLOYEE_ID}.
 * Seuls les champs nécessaires à la résolution d'identité sont exposés.
 *
 * @since 1.0
 */
@Entity
@Immutable
@Table(name = "EMPLOYEES")
@Getter
@NoArgsConstructor
public class EmployeeRef {

    /** Identifiant technique Oracle de l'employé (EMPLOYEES.EMPLOYEE_ID). */
    @Id
    @Column(name = "EMPLOYEE_ID")
    private Long employeeId;

    /** UUID Keycloak — correspond au claim {@code sub} du JWT. */
    @Column(name = "USER_ID")
    private String userId;

    /** Prénom de l'employé. */
    @Column(name = "FIRST_NAME")
    private String firstName;

    /** Nom de famille de l'employé. */
    @Column(name = "LAST_NAME")
    private String lastName;

    /** Adresse email professionnelle de l'employé. */
    @Column(name = "EMAIL")
    private String email;

    /** FK → DEPARTMENTS.DEPT_ID — département de rattachement de l'employé. */
    @Column(name = "DEPT_ID")
    private Long deptId;

    /** FK → EMPLOYEES.EMPLOYEE_ID — identifiant du responsable hiérarchique direct. */
    @Column(name = "MANAGER_ID")
    private Long managerId;

    /** Statut de l'employé : {@code ACTIF} | {@code INACTIF} | {@code CONGE}. */
    @Column(name = "STATUS")
    private String status;

    /** URL de la photo de profil de l'employé (stockée sur le serveur de fichiers). */
    @Column(name = "PHOTO_URL")
    private String photoUrl;

    /** Intitulé du poste occupé par l'employé. */
    @Column(name = "JOB_TITLE")
    private String jobTitle;
}