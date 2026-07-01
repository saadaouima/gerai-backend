package com.gerai.projetsservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;
/**
 * DTO de création d'un nouveau projet.
 * <p>
 * Transporté dans le body de {@code POST /api/affectation/projets}.
 * </p>
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CreateProjetRequest {
    /** Nom du projet (obligatoire). */
    @NotBlank private String nom;
    /** Description textuelle du projet. */
    private String description;
    /** Code unique identifiant le projet (ex. {@code PRJ-001}). */
    private String code;
    /** Date de démarrage prévue du projet. */
    private LocalDate dateDebut;
    /** Date de fin prévue du projet (sérialisée JSON en {@code dateFin}). */
    @JsonProperty("dateFin")
    private LocalDate datefin;
    /** Statut initial du projet (ex. {@code EN_COURS}, {@code EN_ATTENTE}). */
    private String    statut;
    /** Niveau de priorité (ex. {@code HAUTE}, {@code NORMALE}, {@code BASSE}). */
    private String    priority;
    /** Pourcentage d'avancement initial (0-100). */
    private Integer   progression;
    /** Identifiants Oracle des employés à affecter comme membres du projet. */
    private List<Long> membreIds;
}