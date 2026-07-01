package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant une question éliminatoire (killer question) associée à une offre d'emploi.
 * <p>
 * Une réponse incorrecte à une killer question entraîne le rejet immédiat de la candidature
 * (côté serveur via {@link com.gerai.projetsservice.service.ScreeningService}).
 * Les questions sont affichées aux candidats sans la réponse attendue.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "KILLER_QUESTIONS")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "KILLER_QUESTIONS")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class KillerQuestion {

    /** Identifiant unique de la killer question (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "KQ_ID")
    private Long id;

    /** Identifiant de l'offre d'emploi à laquelle appartient cette question. */
    @Column(name = "JOB_ID", nullable = false)
    private Long jobId;

    /** Texte de la question éliminatoire posée au candidat. */
    @Column(name = "QUESTION", length = 500, nullable = false)
    private String question;

    /** Réponse attendue pour valider la question ({@code OUI} ou {@code NON}). */
    @Column(name = "EXPECTED_ANSWER", length = 3, nullable = false)
    private String expectedAnswer;

    /** Ordre d'affichage de la question dans le formulaire de candidature. */
    @Column(name = "DISPLAY_ORDER")
    private Integer displayOrder;
}
