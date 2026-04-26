package com.gerai.projetsservice.dto;

import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TacheDTO {
    private Long   id;
    private String titre;        // Angular utilise 'titre'
    private String projetNom;
    private String projetCouleur;
    private Long   projetId;
    private Long   assignedTo;
    private String priorite;     // Angular utilise 'priorite'
    private LocalDate echeance;  // Angular utilise 'echeance'
    private boolean terminee;    // Angular utilise 'terminee' (TERMINE = true)
    private String  statut;
    private Integer progressPct;
    private String  description;
    private Double  estimatedHours;
    private Double  actualHours;
}