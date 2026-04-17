package com.gerai.analyticsservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité en lecture seule mappée sur la vue Oracle V_ALL_DEMANDES.
 *
 * La vue agrège les 5 tables de demandes :
 *   LEAVE_REQUESTS, TRAINING_REQUESTS, LOAN_REQUESTS,
 *   DOCUMENT_REQUESTS, AUTHORIZATION_REQUESTS
 *
 * Utilisée par AnalyticsRepository pour les requêtes JPQL/natives.
 * @Immutable interdit toute opération d'écriture Hibernate sur cette entité.
 *
 * IMPORTANT : le champ 'id' n'est PAS une PK unique globale — deux demandes
 * de types différents peuvent avoir le même request_id. La vraie clé unique
 * est (ID, TYPE). Pour Hibernate, on accepte cette contrainte car la vue
 * est en lecture seule et on ne fait jamais de findById() dessus.
 */
@Entity
@Immutable
@Table(name = "V_ALL_DEMANDES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Demande {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "EMPLOYE_ID")
    private Long employeId;

    @Column(name = "EMPLOYE_NOM")
    private String employeNom;

    @Column(name = "DEPARTEMENT")
    private String departement;

    /**
     * Type de demande.
     * Valeurs : CONGE | FORMATION | PRET | DOCUMENT | AUTORISATION
     */
    @Column(name = "TYPE")
    private String type;

    /**
     * Statut selon la table source.
     * LEAVE_REQUESTS   : EN_ATTENTE | VALIDE_CHEF | VALIDE_RH | REFUSE | ANNULE
     * TRAINING_REQUESTS: EN_ATTENTE | APPROUVE_CHEF | APPROUVE_RH | REFUSE | ANNULE
     * LOAN_REQUESTS    : EN_ATTENTE | EN_ETUDE | APPROUVE | REFUSE | REMBOURSE
     * DOCUMENT_REQUESTS: EN_ATTENTE | EN_COURS | PRET | LIVRE | REFUSE
     * AUTH_REQUESTS    : EN_ATTENTE | APPROUVE | REFUSE
     */
    @Column(name = "STATUT")
    private String statut;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "DATE_DEBUT")
    private LocalDateTime dateDebut;

    @Column(name = "DATE_FIN")
    private LocalDateTime dateFin;

    @Column(name = "DATE_CREATION")
    private LocalDateTime dateCreation;

    @Column(name = "NB_JOURS")
    private BigDecimal nbJours;

    @Column(name = "MONTANT")
    private BigDecimal montant;

    @Column(name = "APPROUVE_PAR_RH")
    private Long approuveParRh;

    @Column(name = "DATE_APPROBATION_RH")
    private LocalDateTime dateApprobationRh;

    @Column(name = "COMMENTAIRE_RH")
    private String commentaireRh;

    @Column(name = "COMMENTAIRE_CHEF")
    private String commentaireChef;
}