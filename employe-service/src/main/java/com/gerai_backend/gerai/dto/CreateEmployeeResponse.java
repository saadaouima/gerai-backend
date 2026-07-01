package com.gerai_backend.gerai.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de réponse retourné après la création d'un employé via {@code POST /employees}.
 * Contient les informations de l'employé créé en base Oracle et les données du compte Keycloak.
 *
 * <p>Le champ {@code temporaryPassword} est affiché une seule fois dans la réponse
 * et n'est jamais persisté en base de données.</p>
 *
 * @since 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeResponse {

    /** Identifiant Oracle (NUMBER IDENTITY) de l'employé — clé primaire de la table EMPLOYEES. */
    private Long id;

    /** Matricule RH généré automatiquement au format {@code EMP-XXXX}. */
    private String employeeCode;

    /** Prénom de l'employé. */
    private String firstName;

    /** Nom de famille de l'employé. */
    private String lastName;

    /** Adresse email professionnelle de l'employé. */
    private String email;

    /** Date d'embauche de l'employé. */
    private LocalDate hireDate;

    /** FK → {@code DEPARTMENTS.dept_id} — identifiant du département de l'employé. */
    private Long deptId;

    /** FK → {@code POSITIONS.position_id} — identifiant du poste de l'employé. */
    private Long positionId;

    /** FK optionnel → {@code EMPLOYEES.employee_id} — identifiant du manager direct. */
    private Long managerId;

    /** Login Keycloak de l'employé (format {@code prenom.nom} normalisé). */
    private String username;

    /** Statut RH de l'employé ({@code ACTIF} par défaut à la création). */
    private String status;

    /** UUID Keycloak de l'employé (colonne {@code USER_ID} dans EMPLOYEES). */
    private String keycloakUserId;

    /** Mot de passe temporaire généré — affiché une seule fois, jamais stocké en base de données. */
    private String temporaryPassword;

    /** Horodatage de création de l'enregistrement en base Oracle. */
    private LocalDateTime createdAt;
}