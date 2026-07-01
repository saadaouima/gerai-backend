package com.gerai.chat.dto;

import lombok.*;

/**
 * DTO représentant un utilisateur du système pour la liste de contacts du chat.
 * <p>
 * Retourné par {@code GET /api/chat/users}, cet objet combine les données
 * de la table Oracle {@code EMPLOYEES} et de Keycloak.
 *
 * @since 1.0
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class UserDTO {

    /** UUID Keycloak de l'utilisateur (champ {@code sub} du JWT, colonne {@code USER_ID} dans EMPLOYEES). */
    private String keycloakId;

    /** ID Oracle de l'employé ({@code EMPLOYEES.EMPLOYEE_ID}), résolu depuis le claim JWT {@code employee_id}. */
    private Long   employeeId;

    /** Nom de famille de l'employé. */
    private String nom;

    /** Prénom de l'employé. */
    private String prenom;

    /** Nom complet ({@code prenom + " " + nom}) pré-calculé pour l'affichage. */
    private String nomComplet;

    /** Nom d'utilisateur Keycloak (ou email si username non défini). */
    private String username;

    /** {@code true} si l'employé est actuellement en ligne (WebSocket ou session Keycloak active). */
    private boolean enLigne;
}