package com.gerai.tachesservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO reçu depuis Angular pour créer ou modifier une tâche.
 *
 * Angular envoie (AffectationTachesComponent.saveTask()):
 * {
 *   titre, priorite ("Haute"|"Moyenne"|"Basse"),
 *   assigneA ("Prénom Nom"), echeance ("yyyy-MM-dd"),
 *   projet (nom du projet)
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TacheRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 300, message = "Le titre ne doit pas dépasser 300 caractères")
    private String titre;

    /**
     * Label Angular → valeur Oracle :
     *   Haute    → HAUTE
     *   Moyenne  → NORMALE
     *   Basse    → FAIBLE
     *   Critique → CRITIQUE
     */
    @NotBlank(message = "La priorité est obligatoire")
    private String priorite;

    /**
     * "Prénom Nom" de l'assigné — le service résout l'employee_id Oracle
     * via findByFullName(), ou utilise directement assigneId si fourni.
     */
    private String assigneA;

    /** Employee_id Oracle — prioritaire sur assigneA si fourni */
    private Long assigneId;

    @NotNull(message = "La date d'échéance est obligatoire")
    private LocalDate echeance;

    /** Nom du projet — le service résout le project_id Oracle */
    private String projet;

    /** Project_id Oracle — prioritaire sur projet si fourni */
    private Long projetId;

    /** Description optionnelle */
    private String description;

    /** Progression 0-100 */
    @Min(0) @Max(100)
    private Integer progression;

    /** Statut Oracle : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE */
    private String statut;

    private BigDecimal heuresEstimees;
}