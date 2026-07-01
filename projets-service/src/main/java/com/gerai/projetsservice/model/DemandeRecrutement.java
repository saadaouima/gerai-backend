package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Entité JPA représentant une demande de recrutement soumise par un chef de projet.
 * <p>
 * Suit un workflow RH : {@code EN_ATTENTE → APPROUVEE/REJETEE → CONVERTIE} (conversion
 * en offre d'emploi publiée via {@code /api/chef/recrutement/{id}/convertir}).
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "DEMANDES_RECRUTEMENT")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "DEMANDES_RECRUTEMENT")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DemandeRecrutement {
    /** Identifiant unique de la demande de recrutement. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "RECRUTEMENT_ID")
    private Long id;
    /** Identifiant Keycloak du chef de projet ayant soumis la demande. */
    @Column(name = "CHEF_ID")       private String chefId;
    /** Nom complet du chef de projet (dénormalisé). */
    @Column(name = "CHEF_NOM")      private String chefNom;
    /** URL de la photo de profil du chef de projet. */
    @Column(name = "CHEF_PHOTO")    private String chefPhoto;
    /** Département pour lequel le recrutement est demandé. */
    @Column(name = "DEPARTEMENT")   private String departement;
    /** Intitulé du poste à pourvoir. */
    @Column(name = "TITRE_POSTE")   private String titrePoste;
    /** Niveau d'expérience requis pour le poste (ex. {@code JUNIOR}, {@code SENIOR}). */
    @Column(name = "ROLE_POSTE")    private String role;
    /** Nombre de postes à pourvoir. */
    @Column(name = "NOMBRE_POSTES") private Integer nombrePostes;
    /** Type de contrat souhaité (ex. {@code CDI}, {@code CDD}, {@code STAGE}). */
    @Column(name = "TYPE_CONTRAT")  private String typeContrat;
    /** Justification métier du besoin de recrutement. */
    @Column(name = "JUSTIFICATION", length = 2000) private String justification;
    /** Niveau d'urgence de la demande : {@code NORMALE}, {@code URGENTE} ou {@code CRITIQUE}. */
    @Column(name = "URGENCE")       private String urgence;
    /** Statut de traitement : {@code EN_ATTENTE}, {@code APPROUVEE}, {@code CONVERTIE} ou {@code REJETEE}. */
    @Column(name = "STATUT")          private String statut;
    /** Date à laquelle la demande a été soumise par le chef. */
    @Column(name = "DATE_DEMANDE")    private LocalDate dateDemande;
    /** Commentaire de la RH lors du traitement (motif de rejet ou note de validation). */
    @Column(name = "COMMENTAIRE_RH",  length = 2000) private String commentaireRh;
    /** Nom du responsable RH ayant traité la demande. */
    @Column(name = "TRAITEE_PAR")     private String traiteePar;
    /** Date à laquelle la demande a été traitée (approuvée, rejetée ou convertie). */
    @Column(name = "DATE_TRAITEMENT") private LocalDate dateTraitement;
}
