package com.gerai.projetsservice.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de projet retourné pour les 3 espaces (Admin, Chef, Employé).
 * <p>
 * Correspond aux interfaces {@code Projet} du frontend Angular.
 * Les alias {@code dateDebut}/{@code dateFin} et le champ {@code statut}
 * assurent la compatibilité avec les noms de propriétés attendus par Angular.
 * </p>
 *
 * @since 1.0
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProjetDTO {

    /** Identifiant du projet (Angular utilise {@code id}, pas {@code projectId}). */
    private Long    id;
    /** Nom du projet (Angular utilise {@code nom}). */
    private String  nom;
    /** Description détaillée du projet. */
    private String  description;
    /** Code court identifiant le projet. */
    private String  code;

    /** Identifiant Oracle du chef de projet créateur. */
    private Long    createdBy;
    /** Nom complet du chef de projet (enrichi depuis employee-service). */
    private String  chefProjet;
    /** Identifiant du département propriétaire du projet. */
    private Long    deptId;

    /** Date de démarrage du projet. */
    private LocalDate startDate;
    /** Date de fin prévue du projet. */
    private LocalDate endDate;

    /**
     * Alias Angular pour {@code startDate}.
     *
     * @return date de démarrage du projet
     */
    public LocalDate getDateDebut() { return startDate; }

    /**
     * Alias Angular pour {@code endDate}.
     *
     * @return date de fin prévue du projet
     */
    public LocalDate getDateFin()   { return endDate;   }

    /** Statut du projet (Angular utilise {@code statut}). */
    private String  statut;
    /** Niveau de priorité du projet. */
    private String  priority;
    /** Pourcentage d'avancement du projet (Angular utilise {@code progression}). */
    private Integer progression;

    /** Nombre total de tâches du projet (calculé depuis la liste des tâches). */
    private Integer totalTaches;
    /** Nombre de tâches au statut TERMINÉ (calculé depuis la liste des tâches). */
    private Integer tachesCompletees;

    /** Liste des membres du projet (Angular utilise {@code membres}). */
    private List<MembreDTO> membres;
    /** Liste des tâches associées au projet. */
    private List<TacheDTO>  taches;

    /** Date et heure de création du projet. */
    private LocalDateTime createdAt;
    /** Date et heure de la dernière mise à jour du projet. */
    private LocalDateTime updatedAt;
}