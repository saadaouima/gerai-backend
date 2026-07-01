package com.gerai.tachesservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

/**
 * Entité JPA en lecture seule représentant une projection légère de la table GERAI.EMPLOYEES.
 * <p>
 * Seuls les champs nécessaires au service Tâches sont mappés, afin de minimiser
 * les données chargées depuis Oracle.
 * <p>
 * {@code @Entity} : déclare cette classe comme entité JPA mappée sur une table Oracle.
 * {@code @Immutable} : annotation Hibernate indiquant que cette entité ne sera jamais
 * modifiée — Hibernate ne génèrera aucun {@code UPDATE} sur cette table.
 * {@code @Table(name = "EMPLOYEES")} : mappe l'entité sur la table Oracle GERAI.EMPLOYEES.
 * {@code @Getter} : génère uniquement les getters (pas de setters car l'entité est immutable).
 * <p>
 * Cette entité est utilisée uniquement pour la résolution des identifiants employés
 * à partir du JWT (sub, email) et pour récupérer les informations de contact
 * (nom, email, UUID Keycloak) nécessaires aux notifications Kafka.
 *
 * @since 1.0
 */
@Entity
@Immutable
@Table(name = "EMPLOYEES")
@Getter
@NoArgsConstructor
public class EmployeeRef {

    /** Identifiant Oracle unique de l'employé (clé primaire, EMPLOYEES.employee_id). */
    @Id
    @Column(name = "EMPLOYEE_ID")
    private Long employeeId;

    /**
     * Identifiant UUID Keycloak de l'employé (EMPLOYEES.user_id).
     * Correspond au claim {@code sub} du JWT — utilisé pour résoudre
     * l'employee_id depuis le token d'authentification.
     */
    @Column(name = "USER_ID")
    private String userId;

    /** Prénom de l'employé (EMPLOYEES.first_name). */
    @Column(name = "FIRST_NAME")
    private String firstName;

    /** Nom de famille de l'employé (EMPLOYEES.last_name). */
    @Column(name = "LAST_NAME")
    private String lastName;

    /**
     * Adresse email professionnelle de l'employé (EMPLOYEES.email).
     * Transmise à EmailService via les événements Kafka de notification.
     */
    @Column(name = "EMAIL")
    private String email;

    /**
     * Identifiant Oracle du département de l'employé (EMPLOYEES.dept_id).
     * Utilisé pour les requêtes de tâches par département dans le contexte Chef.
     */
    @Column(name = "DEPT_ID")
    private Long deptId;

    /**
     * Identifiant Oracle du manager direct de l'employé (EMPLOYEES.manager_id).
     * Référence vers un autre EMPLOYEES.employee_id.
     */
    @Column(name = "MANAGER_ID")
    private Long managerId;

    /**
     * Statut RH de l'employé (EMPLOYEES.status).
     * Valeur attendue pour les requêtes actives : {@code 'ACTIF'}.
     */
    @Column(name = "STATUS")
    private String status;
}