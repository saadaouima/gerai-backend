package com.gerai.projetsservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDate;
import java.util.List;
/**
 * DTO de mise à jour partielle d'un projet existant.
 * <p>
 * Transporté dans le body de {@code PUT /api/affectation/projets/{id}}.
 * Seuls les champs non nuls sont appliqués.
 * </p>
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class UpdateProjetRequest {
    /** Nouveau nom du projet (optionnel). */
    private String    nom;
    /** Nouvelle description du projet (optionnel). */
    private String    description;
    /** Nouvelle date de démarrage (optionnel). */
    private LocalDate dateDebut;
    /** Nouvelle date de fin (optionnel, sérialisée JSON en {@code dateFin}). */
    @JsonProperty("dateFin")
    private LocalDate datefin;
    /** Nouveau statut du projet (optionnel). */
    private String    statut;
    /** Nouveau pourcentage d'avancement (optionnel). */
    private Integer   progression;
    /** Liste des identifiants d'employés à ajouter comme membres (optionnel). */
    private List<Long> membreIds;
}