package com.gerai.projetsservice.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor
public class UpdateProjetRequest {
    private String    nom;
    private String    description;
    private LocalDate dateDebut;
    private LocalDate datefin;
    private String    statut;
    private Integer   progression;
    private List<Long> membreIds;
}