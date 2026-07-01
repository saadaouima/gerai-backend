package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/**
 * Entité JPA représentant une question de screening (présélection) associée à une offre d'emploi.
 * <p>
 * Contrairement aux {@link KillerQuestion}, les questions de screening sont pondérées
 * et contribuent à un score de présélection (0-100) calculé par
 * {@link com.gerai.projetsservice.service.ScreeningService}.
 * La réponse correcte n'est pas exposée aux candidats.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "SCREENING_QUESTIONS")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "SCREENING_QUESTIONS")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ScreeningQuestion {

    /** Identifiant unique de la question (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "SQ_ID")
    private Long id;

    /** Identifiant de l'offre d'emploi à laquelle appartient cette question. */
    @Column(name = "JOB_ID", nullable = false)
    private Long jobId;

    /** Texte de la question posée au candidat. */
    @Column(name = "QUESTION", length = 500, nullable = false)
    private String question;

    /** Type de question : {@code YES_NO}, {@code SINGLE_CHOICE} ou {@code NUMBER_SCALE}. */
    @Column(name = "TYPE", length = 20, nullable = false)
    private String type;

    /** Options de réponse pour les questions {@code SINGLE_CHOICE}, stockées en CSV Oracle. */
    @Column(name = "OPTIONS", length = 1000)
    @Convert(converter = StringListConverter.class)
    private List<String> options;

    /** Réponse idéale utilisée pour le scoring (non exposée aux candidats). */
    @Column(name = "CORRECT_ANSWER", length = 200)
    private String correctAnswer;

    /** Poids relatif de la question dans le calcul du score de screening (1-10). */
    @Column(name = "WEIGHT", nullable = false)
    private Integer weight;

    /** Ordre d'affichage de la question dans le formulaire de candidature. */
    @Column(name = "DISPLAY_ORDER")
    private Integer displayOrder;
}
