package com.gerai.projetsservice.dto;

import lombok.*;

/**
 * DTO représentant un membre d'un projet, inclus dans {@link ProjetDTO}.
 * <p>
 * Correspond à l'interface {@code MembreProjet} du frontend Angular.
 * </p>
 *
 * @since 1.0
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MembreDTO {
    /** Identifiant Oracle de l'employé. */
    private Long   id;
    /** Prénom du membre. */
    private String prenom;
    /** Nom de famille du membre. */
    private String nom;
    /** Nom complet formaté (prénom + nom). */
    private String nomComplet;
    /** Initiales du membre (ex. {@code JD} pour Jean Dupont). */
    private String initiales;
    /** Rôle dans le projet (ex. {@code CHEF}, {@code DÉVELOPPEUR}, {@code TESTEUR}). */
    private String role;
    /** Intitulé du poste de l'employé. */
    private String poste;
    /** Adresse email du membre. */
    private String email;
    /** Numéro de téléphone professionnel. */
    private String telephone;
    /** Statut RH de l'employé. */
    private String statut;
    /** Date d'embauche au format ISO. */
    private String dateEmbauche;
    /** Département d'appartenance. */
    private String departement;
    /** URL de la photo de profil. */
    private String photo;
}
