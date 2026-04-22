package com.gerai.projetsservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateTacheRequest {
    @NotNull
    private Long   projectId;
    @NotBlank private String titre;
    private String    description;
    private Long      assignedTo;
    private String    prioritize;
    private LocalDate echeance;
    private Double    estimatedHours;
    private Long      parentTaskId;
}
