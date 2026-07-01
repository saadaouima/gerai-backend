package com.gerai_backend.gerai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO représentant un membre d'équipe, utilisé par {@link com.gerai_backend.gerai.controllers.EquipeController}
 * pour transmettre les informations d'un employé au frontend Angular.
 *
 * @since 1.0
 */
@Data
@Builder
public class MembreEquipeDTO {

    /** Identifiant Oracle ({@code EMPLOYEE_ID}) du membre. */
    private Long   id;

    /** UUID Keycloak du membre ({@code USER_ID}). */
    private String keycloakId;

    /** Nom de famille du membre. */
    private String nom;

    /** Prénom du membre. */
    private String prenom;

    /** Adresse email professionnelle du membre. */
    private String email;

    /** Libellé du poste occupé par le membre. */
    private String poste;

    /** Nom du département auquel appartient le membre. */
    private String departement;

    /** Numéro de téléphone du membre. */
    private String telephone;

    /** Statut RH du membre : {@code ACTIF} ou {@code INACTIF}. */
    private String statut;

    /** Date d'embauche du membre au format ISO ({@code yyyy-MM-dd}). */
    private String dateEmbauche;

    /** Indique si le membre est actuellement présent (statut {@code ACTIF}). */
    private boolean present;

    /** URL ou chemin de la photo de profil (avatar) du membre. */
    private String avatar;
}
