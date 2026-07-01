package com.gerai.projetsservice.dto;

import lombok.*;

/**
 * DTO représentant un employé, utilisé pour l'affectation aux projets.
 * <p>
 * Peuplé depuis {@code employee-service} via Feign.
 * Retourné par {@code GET /api/affectation/employes}.
 * </p>
 *
 * @since 1.0
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeDTO {
    /** Identifiant Oracle de l'employé ({@code EMPLOYEES.EMPLOYEE_ID}). */
    private Long   id;
    /** Prénom de l'employé. */
    private String prenom;
    /** Nom de famille de l'employé. */
    private String nom;
    /** Adresse email professionnelle. */
    private String email;
    /** Intitulé du poste occupé. */
    private String poste;
    /** Département d'appartenance. */
    private String departement;
    /** Nom complet formaté (prénom + nom). */
    private String nomComplet;
    /** Numéro de téléphone professionnel. */
    private String telephone;
    /** Statut RH de l'employé (ex. {@code ACTIF}, {@code INACTIF}). */
    private String statut;
    /** Date d'embauche au format ISO (YYYY-MM-DD). */
    private String dateEmbauche;
    /** URL de la photo de profil de l'employé. */
    private String photo;
    /** Identifiant du projet auquel l'employé est actuellement affecté. */
    private Long   projetId;
    /** Nom du projet auquel l'employé est actuellement affecté. */
    private String projetNom;
}
