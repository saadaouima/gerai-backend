package com.gerai.projetsservice.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * DTO de données d'un rapport RH retourné par {@link com.gerai.projetsservice.controller.RapportsController}.
 * <p>
 * Contient des cartes statistiques pour l'affichage en tableau de bord
 * et des lignes de données tabulaires.
 * </p>
 *
 * @since 1.0
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RapportDataDTO {

    /** Liste des cartes statistiques à afficher (KPIs). */
    private List<StatCardDTO> stats;
    /** Lignes du tableau de données du rapport. */
    private List<Map<String, Object>> rows;

    /**
     * Carte statistique individuelle affichée dans le tableau de bord.
     *
     * @since 1.0
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatCardDTO {
        /** Libellé de la métrique (ex. "Congés acceptés"). */
        private String  label;
        /** Valeur de la métrique (peut être un {@code Long}, un {@code String} ou un pourcentage). */
        private Object  value;
        /** Classe d'icône Tabler Icons (ex. {@code ti ti-circle-check}). */
        private String  icon;
        /** Couleur hexadécimale de la carte (ex. {@code #2ed8b6}). */
        private String  color;
        /** Texte de tendance affiché sous la valeur. */
        private String  trend;
        /** Indique si la tendance est positive ({@code true}) ou négative ({@code false}). */
        private boolean trendUp;
    }
}
