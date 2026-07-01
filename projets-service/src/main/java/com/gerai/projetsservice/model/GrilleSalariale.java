package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant une entrée dans la grille salariale de référence.
 * <p>
 * Élément du référentiel RH, géré via {@code /api/admin/referentiel/grille-salariale}.
 * Définit les fourchettes salariales et avantages par niveau ou catégorie de poste.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "REF_GRILLE_SALARIALE")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "REF_GRILLE_SALARIALE")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class GrilleSalariale {
    /** Identifiant unique de l'entrée dans la grille salariale. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "GRILLE_ID")
    private Long id;
    /** Niveau hiérarchique du poste (STAGIAIRE, JUNIOR, SENIOR, LEAD, MANAGER, DIRECTEUR). */
    @Column(name = "NIVEAU",   length = 50)  private String niveau;
    /** Intitulé descriptif du niveau (ex. : « Développeur Senior »). */
    @Column(name = "INTITULE", length = 200) private String intitule;
    /** Salaire minimum de la fourchette (en devise locale). */
    @Column(name = "SALAIRE_MIN")  private Double salaireMin;
    /** Salaire maximum de la fourchette (en devise locale). */
    @Column(name = "SALAIRE_MAX")  private Double salaireMax;
    /** Salaire de base proposé par défaut dans cette fourchette. */
    @Column(name = "SALAIRE_BASE") private Double salaireBase;
    /**
     * Avantages associés à ce niveau salarial, stockés en CSV Oracle.
     * Convertis en {@code List<String>} dans le contrôleur.
     */
    @Column(name = "AVANTAGES", length = 2000) private String avantages;
}
