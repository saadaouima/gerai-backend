package com.gerai.projetsservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor
public class CreateProjetRequest {
    @NotBlank private String nom;
    private String description;
    private String code;
    private LocalDate dateDebut;
    private LocalDate datefin;
    private String    statut;
    private String    priority;
    private Integer   progression;
    private List<Long> membreIds; // IDs Oracle des membres à ajouter
}