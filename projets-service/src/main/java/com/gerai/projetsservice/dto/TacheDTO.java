package com.gerai.projetsservice.dto;

import lombok.*;
import java.time.LocalDate;

/**
 * DTO de tâche retourné pour les espaces chef et employé.
 * <p>
 * Correspond à l'interface {@code Tache} du frontend Angular.
 * Les noms de champs sont alignés sur les conventions Angular (français).
 * </p>
 *
 * @since 1.0
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TacheDTO {
    /** Identifiant unique de la tâche. */
    private Long   id;
    /** Titre de la tâche (Angular utilise {@code titre}). */
    private String titre;
    /** Nom du projet auquel appartient la tâche. */
    private String projetNom;
    /** Couleur associée au projet (pour l'affichage dans le calendrier). */
    private String projetCouleur;
    /** Identifiant du projet auquel appartient la tâche. */
    private Long   projetId;
    /** Identifiant Oracle de l'employé assigné à la tâche. */
    private Long   assignedTo;
    /** Niveau de priorité (Angular utilise {@code priorite}). */
    private String priorite;
    /** Date d'échéance de la tâche (Angular utilise {@code echeance}). */
    private LocalDate echeance;
    /** Indicateur de complétion (Angular utilise {@code terminee} ; {@code true} si statut = TERMINEE). */
    private boolean terminee;
    /** Statut détaillé de la tâche (ex. {@code A_FAIRE}, {@code EN_COURS}, {@code TERMINEE}). */
    private String  statut;
    /** Pourcentage d'avancement de la tâche (0-100). */
    private Integer progressPct;
    /** Description détaillée de la tâche. */
    private String  description;
    /** Estimation initiale du temps en heures. */
    private Double  estimatedHours;
    /** Temps réellement passé sur la tâche en heures. */
    private Double  actualHours;
}