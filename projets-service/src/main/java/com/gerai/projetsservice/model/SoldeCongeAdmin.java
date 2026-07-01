package com.gerai.projetsservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA représentant le solde de congés d'un employé géré côté administration RH.
 * <p>
 * Gérée via {@code /api/admin/soldes-conges}. Les données sont dénormalisées
 * (nom de l'employé, département, poste) pour faciliter l'affichage des rapports
 * sans appels inter-services.
 * </p>
 * <p>
 * {@code @Entity} : classe persistée en base Oracle.<br>
 * {@code @Table(name = "SOLDES_CONGES_ADMIN")} : nom de la table Oracle.
 * </p>
 *
 * @since 1.0
 */
@Entity @Table(name = "SOLDES_CONGES_ADMIN")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SoldeCongeAdmin {
    /** Identifiant unique du solde de congés (clé primaire générée). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "SOLDE_ID")
    private Long id;
    /** Nom complet de l'employé (dénormalisé). */
    @Column(name = "EMPLOYE_NOM")   private String  employeNom;
    /** Département de l'employé (dénormalisé). */
    @Column(name = "DEPARTEMENT")   private String  departement;
    /** Intitulé du poste de l'employé (dénormalisé). */
    @Column(name = "POSTE")         private String  poste;
    /** Solde de congés de la période précédente (en jours). */
    @Column(name = "SOLDE_PRECEDENT") private Double soldePrecedent;
    /** Solde total de congés alloués sur la période courante (en jours). */
    @Column(name = "SOLDE_TOTAL")   private Double  soldeTotal;
    /** Jours reportés depuis la période précédente. */
    @Column(name = "REPORT_SOLDE")  private Double  reportSolde;
    /** Solde actuel disponible ({@code soldeTotal + reportSolde - congesUtilises}). */
    @Column(name = "SOLDE_ACTUEL")  private Double  soldeActuel;
    /** Nombre de jours de congés effectivement utilisés. */
    @Column(name = "CONGES_UTILISES")  private Double congesUtilises;
    /** Nombre de jours de congés approuvés par la RH. */
    @Column(name = "CONGES_ACCEPTES")  private Double congesAcceptes;
    /** Nombre de jours de congés refusés par la RH. */
    @Column(name = "CONGES_REJETES")   private Double congesRejetes;
    /** Nombre de jours de congés expirés (non utilisés avant la date limite). */
    @Column(name = "CONGES_EXPIRES")   private Double congesExpires;
}
