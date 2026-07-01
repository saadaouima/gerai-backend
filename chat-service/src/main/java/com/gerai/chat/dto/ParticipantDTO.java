package com.gerai.chat.dto;

import lombok.*;

/**
 * DTO représentant un participant à une conversation.
 * <p>
 * Inclus dans {@link ConversationDTO} pour afficher les membres d'une conversation
 * avec leur nom, rôle et statut de présence.
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ParticipantDTO {

    /** Identifiant Oracle de l'employé participant (PK de EMPLOYEES). */
    private Long   employeeId;

    /** Nom de famille de l'employé. */
    private String nom;

    /** Prénom de l'employé. */
    private String prenom;

    /** Nom complet ({@code prenom + " " + nom}), calculé lors du mapping DTO. */
    private String nomComplet;

    /** Rôle dans la conversation : {@code ADMIN} (créateur de groupe) ou {@code MEMBRE}. */
    private String role;

    /** {@code true} si l'employé est actuellement connecté (WebSocket ou session Keycloak active). */
    private boolean enLigne;
}