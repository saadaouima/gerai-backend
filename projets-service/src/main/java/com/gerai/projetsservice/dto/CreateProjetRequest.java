package com.gerai.projetsservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor
public class CreateProjetRequest {
    @NotBlank private String nom;
    private String description;
    private String code;
    private LocalDate dateDebut;
    @JsonProperty("dateFin")
    private LocalDate datefin;
    private String    statut;
    private String    priority;
    private Integer   progression;
    private List<Long> membreIds;
}