package com.gerai.projetsservice.dto;

import lombok.*;

/**
 * DTO des statistiques agrégées du tableau de bord administrateur.
 * <p>
 * Retourné par {@code GET /api/admin/dashboard}.
 * </p>
 *
 * @since 1.0
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardDTO {
    /** Nombre total de projets dans la plateforme. */
    private long totalProjets;
    /** Nombre de projets au statut {@code EN_COURS}. */
    private long projetsEnCours;
    /** Nombre de projets au statut {@code TERMINE}. */
    private long projetsTermines;
    /** Nombre de projets au statut {@code EN_ATTENTE}. */
    private long projetsEnAttente;
    /** Nombre de projets au statut {@code EN_PAUSE}. */
    private long projetsEnPause;
    /** Nombre total de tâches dans tous les projets. */
    private long totalTaches;
    /** Nombre de tâches au statut {@code TERMINEE}. */
    private long tachesTerminees;
    /** Taux de complétion global (tâches terminées / tâches totales × 100). */
    private double tauxCompletion;
}
