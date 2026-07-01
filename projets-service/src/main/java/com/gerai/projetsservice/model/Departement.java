package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant un département de l'organisation.
 * <p>
 * Élément du référentiel RH, géré via {@code /api/admin/departements}.
 * Utilisé pour catégoriser les projets, offres d'emploi et soldes de congés.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "ADMIN_DEPARTEMENTS")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "ADMIN_DEPARTEMENTS")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Departement {
    /** Identifiant unique du département (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "DEPT_ADMIN_ID")
    private Long id;
    /** Nom officiel du département (ex. "Informatique & Développement"). */
    @Column(name = "NOM")           private String nom;
    /** Nom complet du responsable du département. */
    @Column(name = "RESPONSABLE")   private String responsable;
    /** Numéro de téléphone du département. */
    @Column(name = "TELEPHONE")     private String telephone;
    /** Adresse email officielle du département. */
    @Column(name = "EMAIL")         private String email;
    /** Capacité maximale d'employés que le département peut accueillir. */
    @Column(name = "CAPACITE")      private Integer capacite;
    /** Année de création du département. */
    @Column(name = "ANNEE_CREATION") private Integer anneeCreation;
    /** Nombre actuel d'employés dans le département. */
    @Column(name = "TOTAL_EMPLOYES") private Integer totalEmployes;
    /** Description des missions et responsabilités du département. */
    @Column(name = "DESCRIPTION", length = 2000) private String description;
}
