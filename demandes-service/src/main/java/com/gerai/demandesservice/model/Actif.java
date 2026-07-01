package com.gerai.demandesservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entité JPA représentant un actif informatique (matériel IT) géré par l'entreprise.
 * Mappée sur la table Oracle {@code GERAI.ACTIFS}.
 * <p>
 * Un actif peut être attribué à un employé (EMPLOYE_ID) et peut faire l'objet
 * de demandes de réparation ou de renouvellement via {@link DemandeActif}.
 *
 * @since 1.0
 */
@Entity @Table(name = "ACTIFS")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Actif {

    /** Identifiant technique généré automatiquement (ACTIFS.ACTIF_ID). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ACTIF_ID")
    private Long id;

    /** Catégorie de l'actif (ex : INFORMATIQUE, MOBILIER, VEHICULE). */
    @Column(name = "CATEGORIE")        private String     categorie;

    /** Type précis de l'actif (ex : ORDINATEUR_PORTABLE, TELEPHONE). */
    @Column(name = "TYPE")             private String     type;

    /** Statut courant de l'actif : DISPONIBLE | ATTRIBUE | EN_REPARATION | HORS_SERVICE. */
    @Column(name = "STATUT")           private String     statut;

    /** Nom ou libellé commercial de l'actif. */
    @Column(name = "NOM")              private String     nom;

    /** Marque du fabricant (ex : Dell, Apple, Lenovo). */
    @Column(name = "MARQUE")           private String     marque;

    /** Modèle précis de l'appareil (ex : MacBook Pro 14, ThinkPad X1). */
    @Column(name = "MODELE")           private String     modele;

    /** Numéro de série unique de l'actif (identifiant matériel). */
    @Column(name = "NUMERO_SERIE")     private String     numeroSerie;

    /** Description libre ou note complémentaire sur l'actif. */
    @Column(name = "DESCRIPTION")      private String     description;

    /** Valeur d'achat de l'actif en devise de référence (TND). */
    @Column(name = "VALEUR")           private BigDecimal valeur;

    /** Date d'acquisition / d'achat de l'actif. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "DATE_ACQUISITION") private LocalDate  dateAcquisition;

    /** Date d'attribution de l'actif à l'employé courant. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "DATE_ATTRIBUTION") private LocalDate  dateAttribution;

    /** Date d'expiration de la garantie ou de la licence associée. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "DATE_EXPIRATION")  private LocalDate  dateExpiration;

    /** FK → EMPLOYEES.EMPLOYEE_ID — employé auquel l'actif est actuellement attribué. */
    @Column(name = "EMPLOYE_ID")       private Long       employeId;

    /** Nom complet de l'employé attributaire (dénormalisé pour affichage rapide). */
    @Column(name = "EMPLOYE_NOM")      private String     employeNom;
}
