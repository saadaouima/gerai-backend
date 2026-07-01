package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant une compétence du référentiel RH.
 * <p>
 * Utilisée pour catégoriser les profils des candidats et des employés.
 * Gérée via {@code /api/admin/referentiel/competences}.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "REF_COMPETENCES")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "REF_COMPETENCES")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Competence {
    /** Identifiant unique de la compétence (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "COMPETENCE_ID")
    private Long id;
    /** Nom de la compétence (ex. "Java", "Spring Boot", "Gestion de projet"). */
    @Column(name = "NOM")         private String  nom;
    /** Catégorie de la compétence (ex. "TECHNIQUE", "SOFT_SKILL", "MANAGEMENT"). */
    @Column(name = "CATEGORIE")   private String  categorie;
    /** Description détaillée de la compétence et de son niveau attendu. */
    @Column(name = "DESCRIPTION", length = 1000) private String description;
    /** Indique si la compétence est active et utilisable dans les offres d'emploi. */
    @Column(name = "ACTIF")       private Boolean actif;
}
