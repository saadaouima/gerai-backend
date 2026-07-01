package com.gerai_backend.gerai.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de création d'un employé, utilisé comme corps de la requête POST {@code /employees}.
 *
 * <p>Contient les champs obligatoires (département, poste) et optionnels
 * (téléphone, date de naissance, numéro national, genre, adresse, photo)
 * nécessaires à la création d'un compte employé complet sur la plateforme Synapse.</p>
 *
 * @since 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeRequest {

    /** Nom d'utilisateur Keycloak uniquement (devient {@code preferred_username}) — non stocké dans EMPLOYEES. */
    private String username;

    /** Prénom de l'employé. */
    private String firstName;

    /** Nom de famille de l'employé. */
    private String lastName;

    /** Adresse email professionnelle (unique dans EMPLOYEES et dans Keycloak). */
    private String email;

    /** Date d'embauche de l'employé. */
    private LocalDate hireDate;

    /** FK obligatoire → {@code DEPARTMENTS.dept_id}. */
    private Long deptId;

    /** FK obligatoire → {@code POSITIONS.position_id}. */
    private Long positionId;

    /** FK optionnel → {@code EMPLOYEES.employee_id} (manager direct de l'employé). */
    private Long managerId;

    /** Numéro de téléphone de l'employé (optionnel). */
    private String phone;

    /** Date de naissance de l'employé (optionnelle). */
    private LocalDate birthDate;

    /** Numéro de carte d'identité nationale (optionnel, unique). */
    private String nationalId;

    /** Genre de l'employé : {@code M}, {@code F} ou {@code AUTRE} (optionnel). */
    private String gender;

    /** Adresse postale de l'employé (optionnelle). */
    private String address;

    /** URL de la photo de profil de l'employé (optionnelle). */
    private String photoUrl;

    /** Liste des rôles Keycloak realm à attribuer à la création (ex. {@code ["employe"]}, {@code ["chef"]}). */
    private List<String> roles;
}