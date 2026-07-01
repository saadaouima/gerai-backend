package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant une offre d'emploi publiée par l'organisation.
 * <p>
 * Créée manuellement par la RH ou automatiquement lors de la conversion
 * d'une {@link DemandeRecrutement} via {@code /api/chef/recrutement/{id}/convertir}.
 * Les offres au statut {@code OUVERT} sont visibles publiquement via
 * {@code GET /api/public/jobs}.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "ADMIN_JOBS")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "ADMIN_JOBS")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {
    /** Identifiant unique de l'offre d'emploi (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "JOB_ID")
    private Long id;
    /** Intitulé du poste à pourvoir. */
    @Column(name = "TITRE")            private String  titre;
    /** Statut de l'offre : {@code OUVERT}, {@code EN_COURS}, {@code FERME} ou {@code EN_ATTENTE}. */
    @Column(name = "STATUT")           private String  statut;
    /** Date de publication de l'offre au format ISO (YYYY-MM-DD). */
    @Column(name = "DATE_PUBLICATION") private String  datePublication;
    /** Niveau d'expérience requis : {@code JUNIOR}, {@code SENIOR}, {@code LEAD}, {@code MANAGER}, {@code DIRECTEUR} ou {@code STAGIAIRE}. */
    @Column(name = "ROLE_POSTE")       private String  role;
    /** Nombre de postes à pourvoir pour cette offre. */
    @Column(name = "POSTES")           private Integer postes;
    /** Département recruteur. */
    @Column(name = "DEPARTEMENT")      private String  departement;
    /** Type de contrat proposé : {@code CDI}, {@code CDD}, {@code STAGE}, {@code FREELANCE} ou {@code ALTERNANCE}. */
    @Column(name = "TYPE_CONTRAT")     private String  typeContrat;
    /** Lieu de travail (ville, site ou "Télétravail"). */
    @Column(name = "LIEU")             private String  lieu;
    /** Description détaillée du poste, des missions et des prérequis. */
    @Column(name = "DESCRIPTION", length = 2000) private String description;
}
