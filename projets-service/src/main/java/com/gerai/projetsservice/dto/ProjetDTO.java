package com.gerai.projetsservice.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/* ═══════════════════════════════════════════════════════
   ProjetDTO — retourné pour les 3 espaces
   Correspond aux interfaces Projet du front Angular
   ═══════════════════════════════════════════════════════ */

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProjetDTO {

    private Long    id;           // Angular utilise 'id', pas 'projectId'
    private String  nom;          // Angular utilise 'nom'
    private String  description;
    private String  code;

    private Long    createdBy;
    private String  chefProjet;   // nom complet du chef (enrichi)
    private Long    deptId;

    private LocalDate startDate;
    private LocalDate endDate;

    /* Alias Angular : dateDebut / datefin */
    public LocalDate getDateDebut() { return startDate; }
    public LocalDate getDatefin()   { return endDate;   }

    private String  statut;       // Angular utilise 'statut'
    private String  priority;
    private Integer progression;  // Angular utilise 'progression'

    private List<MembreDTO> membres;  // Angular utilise 'membres'
    private List<TacheDTO>  taches;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}