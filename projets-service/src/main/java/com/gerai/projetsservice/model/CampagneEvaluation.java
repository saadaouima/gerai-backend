package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant une campagne d'évaluation de performance.
 * <p>
 * Une campagne regroupe des évaluations {@link PerformanceEval} sur une période
 * déterminée (trimestrielle ou annuelle). Elle suit un workflow à 3 états :
 * {@code PLANIFIEE → ACTIVE → CLOTUREE}.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "CAMPAGNES_EVALUATION")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "CAMPAGNES_EVALUATION")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CampagneEvaluation {
    /** Identifiant unique de la campagne (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "CAMPAGNE_ID")
    private Long id;
    /** Titre descriptif de la campagne (ex. "Évaluation Annuelle 2024"). */
    @Column(name = "TITRE")       private String titre;
    /** Période couverte par la campagne : {@code T1}, {@code T2}, {@code T3}, {@code T4} ou {@code ANNUEL}. */
    @Column(name = "PERIODE")     private String periode;
    /** Année de la campagne d'évaluation. */
    @Column(name = "ANNEE")       private Integer annee;
    /** Date de début de la période d'évaluation (format ISO YYYY-MM-DD). */
    @Column(name = "DATE_DEBUT")  private String dateDebut;
    /** Date de fin de la période d'évaluation (format ISO YYYY-MM-DD). */
    @Column(name = "DATE_FIN")    private String dateFin;
    /** Description détaillée des objectifs de la campagne. */
    @Column(name = "DESCRIPTION", length = 2000) private String description;
    /** Nom du responsable RH ayant créé la campagne. */
    @Column(name = "CREE_PAR")    private String creePar;
    /** Statut de la campagne : {@code PLANIFIEE}, {@code ACTIVE} ou {@code CLOTUREE}. */
    @Column(name = "STATUT")      private String statut;
}
