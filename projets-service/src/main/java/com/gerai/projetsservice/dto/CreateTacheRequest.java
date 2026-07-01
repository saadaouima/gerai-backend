package com.gerai.projetsservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * DTO de création d'une nouvelle tâche dans un projet.
 * <p>
 * Transporté dans le body de {@code POST /api/affectation/taches}.
 * </p>
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CreateTacheRequest {
    /** Identifiant du projet auquel appartient la tâche (obligatoire). */
    @NotNull
    private Long   projectId;
    /** Titre de la tâche (obligatoire). */
    @NotBlank private String titre;
    /** Description détaillée de la tâche. */
    private String    description;
    /** Identifiant Oracle de l'employé assigné à la tâche. */
    private Long      assignedTo;
    /** Niveau de priorité de la tâche (ex. {@code HAUTE}, {@code NORMALE}). */
    private String    prioritize;
    /** Date d'échéance de la tâche. */
    private LocalDate echeance;
    /** Estimation du temps de réalisation en heures. */
    private Double    estimatedHours;
    /** Identifiant de la tâche parente (pour les sous-tâches). */
    private Long      parentTaskId;
}
