package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant un jour férié national ou ponctuel.
 * <p>
 * Élément du référentiel RH, géré via {@code /api/admin/referentiel/jours-feries}.
 * Utilisé pour exclure les jours fériés du calcul des congés et du planning.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "REF_JOURS_FERIES")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "REF_JOURS_FERIES")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JourFerie {
    /** Identifiant unique du jour férié (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "JOUR_FERIE_ID")
    private Long id;
    /** Libellé du jour férié (ex. "Fête du Travail", "Aïd Al Fitr"). */
    @Column(name = "LIBELLE")     private String  libelle;
    /** Date du jour férié au format ISO (YYYY-MM-DD). */
    @Column(name = "DATE_FERIE")  private String  date;
    /** Indique si ce jour férié se reproduit chaque année à la même date. */
    @Column(name = "RECURRENT")   private Boolean recurrent;
    /** Description ou note additionnelle sur ce jour férié. */
    @Column(name = "DESCRIPTION", length = 1000) private String description;
}
