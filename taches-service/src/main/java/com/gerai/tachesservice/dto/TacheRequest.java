package com.gerai.tachesservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO reçu depuis Angular pour créer ou modifier une tâche.
 * <p>
 * Envoyé par {@code AffectationTachesComponent.saveTask()} lors de la création
 * ou modification d'une tâche dans l'espace Chef :
 * <pre>
 * {
 *   titre, priorite ("Haute"|"Moyenne"|"Basse"),
 *   assigneA ("Prénom Nom"), echeance ("yyyy-MM-dd"),
 *   projet (nom du projet)
 * }
 * </pre>
 * <p>
 * Le service {@code TacheService} résout les identifiants Oracle ({@code employee_id},
 * {@code project_id}) à partir des valeurs textuelles ({@code assigneA}, {@code projet})
 * si les identifiants directs ({@code assigneId}, {@code projetId}) ne sont pas fournis.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TacheRequest {

    /**
     * Titre de la tâche, obligatoire et limité à 300 caractères
     * pour correspondre à la contrainte Oracle VARCHAR2(300).
     */
    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 300, message = "Le titre ne doit pas dépasser 300 caractères")
    private String titre;

    /**
     * Priorité envoyée par Angular, convertie en valeur Oracle par le service.
     * Mapping : Haute → HAUTE, Moyenne → NORMALE, Basse → FAIBLE, Critique → CRITIQUE.
     */
    @NotBlank(message = "La priorité est obligatoire")
    private String priorite;

    /**
     * Nom complet ("Prénom Nom") de l'employé à assigner.
     * Le service résout l'identifiant Oracle correspondant via
     * {@code EmployeeQueryRepository.findEmployeeIdByFullName()}.
     * Ignoré si {@code assigneId} est fourni directement.
     */
    private String assigneA;

    /**
     * Identifiant Oracle de l'employé assigné (EMPLOYEES.employee_id).
     * Prioritaire sur {@code assigneA} si les deux sont fournis.
     */
    private Long assigneId;

    /** Date d'échéance de la tâche, obligatoire. */
    @NotNull(message = "La date d'échéance est obligatoire")
    private LocalDate echeance;

    /**
     * Nom du projet auquel rattacher la tâche.
     * Le service résout l'identifiant Oracle correspondant via Feign.
     * Ignoré si {@code projetId} est fourni directement.
     */
    private String projet;

    /**
     * Identifiant Oracle du projet (PROJECTS.project_id).
     * Prioritaire sur {@code projet} si les deux sont fournis.
     */
    private Long projetId;

    /** Description détaillée de la tâche (optionnelle). */
    private String description;

    /**
     * Pourcentage de progression initial de la tâche (0-100), optionnel.
     * Par défaut 0 à la création si non spécifié.
     */
    @Min(0) @Max(100)
    private Integer progression;

    /**
     * Statut initial de la tâche lors de la création.
     * Valeurs Oracle acceptées : A_FAIRE | EN_COURS | EN_REVUE | TERMINE | BLOQUE.
     * Par défaut {@code A_FAIRE} si non spécifié.
     */
    private String statut;

    /** Estimation du temps nécessaire en heures (TASKS.estimated_hours), optionnel. */
    private BigDecimal heuresEstimees;
}