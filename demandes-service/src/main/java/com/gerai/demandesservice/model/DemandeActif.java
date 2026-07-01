package com.gerai.demandesservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entité JPA représentant une demande de réparation ou de renouvellement d'un actif IT.
 * Mappée sur la table Oracle {@code GERAI.DEMANDES_ACTIFS}.
 * <p>
 * Une demande est soumise par un employé pour un actif spécifique ({@link Actif}).
 * L'administrateur IT traite la demande en mettant à jour le statut et le commentaire.
 *
 * @since 1.0
 */
@Entity @Table(name = "DEMANDES_ACTIFS")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DemandeActif {

    /** Identifiant technique de la demande (DEMANDES_ACTIFS.DEMANDE_ACTIF_ID). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "DEMANDE_ACTIF_ID")
    private Long id;

    /** FK → ACTIFS.ACTIF_ID — actif concerné par la demande. */
    @Column(name = "ACTIF_ID")                           private Long   actifId;

    /** Nom de l'actif (dénormalisé pour affichage sans jointure). */
    @Column(name = "ACTIF_NOM")                          private String actifNom;

    /** Type de l'actif (dénormalisé pour affichage sans jointure). */
    @Column(name = "ACTIF_TYPE")                         private String actifType;

    /** FK → EMPLOYEES.EMPLOYEE_ID — employé qui soumet la demande. */
    @Column(name = "EMPLOYE_ID")                         private Long   employeId;

    /** Nom de l'employé (dénormalisé pour affichage rapide). */
    @Column(name = "EMPLOYE_NOM")                        private String employeNom;

    /** Nature de la demande : {@code REPARATION} ou {@code RENOUVELLEMENT}. */
    @Column(name = "TYPE")                               private String type;

    /** Description détaillée du problème ou du besoin de renouvellement. */
    @Column(name = "DESCRIPTION", length = 1000)         private String description;

    /** Niveau d'urgence de la demande : {@code NORMALE} ou {@code URGENTE}. */
    @Column(name = "URGENCE")                            private String urgence;

    /** Statut de traitement : {@code EN_ATTENTE} | {@code EN_COURS} | {@code RESOLUE} | {@code REJETEE}. */
    @Column(name = "STATUT")                             private String statut;

    /** Commentaire ou motif de décision saisi par l'administrateur IT. */
    @Column(name = "COMMENTAIRE_ADMIN", length = 1000)   private String commentaireAdmin;

    /** Horodatage de création de la demande — positionné automatiquement par Hibernate. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @CreationTimestamp
    @Column(name = "DATE_CREATION", updatable = false)   private LocalDateTime dateCreation;

    /** Horodatage de traitement de la demande par l'administrateur IT. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "DATE_TRAITEMENT")                    private LocalDateTime dateTraitement;
}
