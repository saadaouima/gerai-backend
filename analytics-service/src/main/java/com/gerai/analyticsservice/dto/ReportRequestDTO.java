package com.gerai.analyticsservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * DTO reçu en paramètre pour les exports de rapports.
 * Utilisé si tu veux un endpoint POST /api/reports/generate
 * avec filtres dynamiques (période, type, employé...).
 *
 * Pour les endpoints GET simples (conges/pdf, formations/pdf)
 * les paramètres passent directement en @RequestParam.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequestDTO {

    /**
     * Type de rapport à générer.
     * Valeurs : CONGES, FORMATIONS, DASHBOARD, FICHE_EMPLOYE
     */
    @NotNull(message = "Le type de rapport est obligatoire")
    private String type;

    /**
     * Format d'export souhaité.
     * Valeurs : PDF, EXCEL
     */
    @NotNull(message = "Le format est obligatoire")
    private String format;

    /* ── Filtres optionnels ───────────────────────────── */

    /** Filtrer par mois (format : YYYY-MM, ex: "2026-04") */
    private String moisDebut;
    private String moisFin;

    /** Pour le rapport fiche employé */
    private String employeId;
    private String employeNom;

    /** Filtrer par statut (EN_ATTENTE, VALIDEE_RH, REJETEE...) */
    private String statut;
}