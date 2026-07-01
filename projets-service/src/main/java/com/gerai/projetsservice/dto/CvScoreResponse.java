package com.gerai.projetsservice.dto;

import lombok.*;

import java.util.List;

/**
 * DTO contenant le résultat du scoring IA d'un CV par le service Groq.
 * <p>
 * Retourné par {@code POST /api/admin/candidates/{id}/ai-score}.
 * </p>
 *
 * @since 1.0
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CvScoreResponse {
    /** Score global sur 100 calculé par l'IA. */
    private Integer      score;
    /** Niveau qualitatif : {@code EXCELLENT} (≥80), {@code BON} (≥60), {@code PASSABLE} (≥40), {@code INSUFFISANT} (<40). */
    private String       niveau;
    /** Compétences du candidat qui correspondent aux exigences de l'offre. */
    private List<String> competencesMatchees;
    /** Compétences requises par l'offre absentes du profil du candidat. */
    private List<String> competencesManquantes;
    /** Points forts identifiés dans le CV du candidat. */
    private List<String> pointsForts;
    /** Recommandation textuelle générée par l'IA pour l'étape suivante du recrutement. */
    private String       recommandation;
}
