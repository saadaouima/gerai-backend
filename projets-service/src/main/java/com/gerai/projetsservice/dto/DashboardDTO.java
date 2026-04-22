package com.gerai.projetsservice.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardDTO {
    private long totalProjets;
    private long projetsEnCours;
    private long projetsTermines;
    private long projetsEnAttente;
    private long projetsEnPause;
    private long totalTaches;
    private long tachesTerminees;
    private double tauxCompletion;
}
