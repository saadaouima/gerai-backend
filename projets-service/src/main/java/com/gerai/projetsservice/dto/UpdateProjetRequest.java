package com.gerai.projetsservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDate;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor
public class UpdateProjetRequest {
    private String    nom;
    private String    description;
    private LocalDate dateDebut;
    @JsonProperty("dateFin")
    private LocalDate datefin;
    private String    statut;
    private Integer   progression;
    private List<Long> membreIds;
}