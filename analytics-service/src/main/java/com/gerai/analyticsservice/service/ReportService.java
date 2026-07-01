package com.gerai.analyticsservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service de génération des rapports RH au format PDF et Excel via JasperReports.
 *
 * Rapports disponibles :
 * <ul>
 *   <li>Congés : {@code reports/conges_report.jrxml} (PDF) / {@code reports/conges_report_excel.jrxml} (Excel)</li>
 *   <li>Formations : {@code reports/formations_report.jrxml} (PDF) / {@code reports/formations_report_excel.jrxml} (Excel)</li>
 *   <li>Projets : {@code reports/projets_report.jrxml} (PDF)</li>
 *   <li>Dashboard RH : {@code reports/dashboard_report.jrxml} (PDF)</li>
 *   <li>Fiche employé : {@code /reports/fiche_employe.jrxml} (PDF)</li>
 * </ul>
 *
 * Le filtrage des données par rôle (Chef vs RH/Admin) est délégué à {@link StatsService} ;
 * ce service se concentre uniquement sur la compilation et le remplissage JasperReports.
 * Les données sont normalisées (clés en majuscules, {@code Long}/{@code Integer} convertis
 * en {@code BigDecimal}) pour respecter les contraintes des templates JRXML.
 *
 * {@code @Service} : bean Spring géré par le conteneur IoC.
 * {@code @Slf4j} : injecte un logger SLF4J pour la traçabilité des appels.
 * {@code @RequiredArgsConstructor} : génère le constructeur avec injection de {@link StatsService}.
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final StatsService statsService;

    /* ── Congés ──────────────────────────────────────── */

    /**
     * Génère le rapport PDF récapitulatif des congés.
     * Pour un Chef, seuls les congés de son département sont inclus.
     * Pour un RH/Admin, un filtre optionnel par département peut être appliqué.
     *
     * @param deptId identifiant de département optionnel (null = tous les départements pour RH)
     * @param auth   le contexte d'authentification pour le filtrage par rôle
     * @return le contenu binaire du PDF généré
     * @throws JRException en cas d'erreur JasperReports lors de la compilation ou du remplissage
     */
    public byte[] generateCongesPdf(Long deptId, Authentication auth) throws JRException {
        return pdf("reports/conges_report.jrxml", buildCongesParams(),
                statsService.getCongesForReport(deptId, auth));
    }

    /**
     * Génère le rapport Excel (XLSX) récapitulatif des congés.
     * Le filtrage par rôle est identique à {@link #generateCongesPdf(Long, Authentication)}.
     *
     * @param deptId identifiant de département optionnel (null = tous les départements pour RH)
     * @param auth   le contexte d'authentification pour le filtrage par rôle
     * @return le contenu binaire du fichier XLSX généré
     * @throws JRException en cas d'erreur JasperReports lors de la compilation ou de l'export
     */
    public byte[] generateCongesExcel(Long deptId, Authentication auth) throws JRException {
        return excel("reports/conges_report_excel.jrxml", buildCongesParams(),
                statsService.getCongesForReport(deptId, auth));
    }

    /* ── Formations ──────────────────────────────────── */

    /**
     * Génère le rapport PDF des formations RH.
     * Le filtrage par rôle est identique à {@link #generateCongesPdf(Long, Authentication)}.
     *
     * @param deptId identifiant de département optionnel (null = tous les départements pour RH)
     * @param auth   le contexte d'authentification pour le filtrage par rôle
     * @return le contenu binaire du PDF généré
     * @throws JRException en cas d'erreur JasperReports
     */
    public byte[] generateFormationsPdf(Long deptId, Authentication auth) throws JRException {
        return pdf("reports/formations_report.jrxml", buildFormationsParams(),
                statsService.getFormationsForReport(deptId, auth));
    }

    /**
     * Génère le rapport Excel (XLSX) des formations RH.
     * Le filtrage par rôle est identique à {@link #generateCongesPdf(Long, Authentication)}.
     *
     * @param deptId identifiant de département optionnel (null = tous les départements pour RH)
     * @param auth   le contexte d'authentification pour le filtrage par rôle
     * @return le contenu binaire du fichier XLSX généré
     * @throws JRException en cas d'erreur JasperReports lors de l'export
     */
    public byte[] generateFormationsExcel(Long deptId, Authentication auth) throws JRException {
        return excel("reports/formations_report_excel.jrxml", buildFormationsParams(),
                statsService.getFormationsForReport(deptId, auth));
    }

    /* ── Projets ─────────────────────────────────────── */

    /**
     * Génère le rapport PDF des projets RH.
     * Pour un Chef, seuls les projets dont il est créateur sont inclus.
     * Pour un RH/Admin, tous les projets sont retournés.
     *
     * @param deptId identifiant de département optionnel (non utilisé pour les projets, filtré par créateur)
     * @param auth   le contexte d'authentification pour la résolution du rôle
     * @return le contenu binaire du PDF généré
     * @throws JRException en cas d'erreur JasperReports
     */
    public byte[] generateProjetsPdf(Long deptId, Authentication auth) throws JRException {
        return pdf("reports/projets_report.jrxml", buildProjetsParams(deptId, auth),
                statsService.getProjetsForReport(deptId, auth));
    }

    /* ── Fiche employé ───────────────────────────────── */

    /**
     * Génère le rapport PDF de la fiche signalétique d'un employé.
     * * @param id L'identifiant de l'employé (EMPLOYEE_ID)
     * @return le flux d'octets du PDF généré
     */
    public byte[] generateFicheEmployePdf(Long id) {
        try {
            log.info("[Report] Génération fiche employé pour ID: {}", id);

            // 1. Récupération des informations de base (Oracle)
            Map<String, Object> empInfos = statsService.getEmployeInfos(id);

            // 2. Préparation des paramètres pour Jasper
            Map<String, Object> params = new HashMap<>();
            params.put("EMPLOYE_NOM",    empInfos.getOrDefault("NOM_COMPLET", "Inconnu"));
            params.put("MATRICULE",      empInfos.getOrDefault("MATRICULE", "N/A"));
            params.put("DEPARTEMENT",    empInfos.getOrDefault("DEPARTEMENT", "N/A"));
            params.put("EMAIL",          empInfos.getOrDefault("EMAIL", "-"));
            params.put("DATE_EMBAUCHE",  empInfos.getOrDefault("DATE_EMBAUCHE", "-"));
            params.put("EMPLOYE_ID",     id);

            // Date de génération formatée
            params.put("GENERATED_DATE", new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()));

            // 3. Récupération de l'historique (Tableau des demandes)
            // Cette méthode doit retourner une List<Map<String, Object>>
            List<Map<String, Object>> historique = statsService.getDemandesEmploye(id);

            // 4. Chargement du fichier .jrxml ou .jasper
            InputStream reportStream = getClass().getResourceAsStream("/reports/fiche_employe.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

            // 5. Remplissage du rapport avec les données
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(historique);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);

            // 6. Export en PDF
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception e) {
            log.error("[Report] Erreur lors de la génération du PDF pour l'employé {}", id, e);
            throw new RuntimeException("Erreur lors de la génération du rapport PDF", e);
        }
    }

    /* ── Dashboard (RH global — null = pas de filtre rôle) ── */

    /**
     * Génère le rapport PDF du tableau de bord RH global.
     * Aucun filtre par département — toutes les données sont agrégées.
     * Inclut les KPIs principaux : absences, formations, projets, demandes.
     *
     * @return le contenu binaire du PDF généré
     * @throws JRException en cas d'erreur JasperReports lors de la compilation ou du remplissage
     */
    public byte[] generateDashboardPdf() throws JRException {
        // getDashboard(null) retourne le dashboard global sans filtre département
        var dashboard  = statsService.getDashboard(null);
        var formations = statsService.getFormationStats();
        var conges     = statsService.getCongeStats();

        Map<String, Object> p = base("Tableau de Bord RH");
        p.put("KPI_CONGES_EN_COURS",     dashboard.getAbsentsAujourdhui());
        p.put("KPI_CONGES_MOIS",
                dashboard.getDemandesParType().getOrDefault("CONGE", 0L));
        p.put("KPI_JOURS_CONGES",        conges.getTotalConges());
        p.put("KPI_TAUX_ABSENTEISME",    dashboard.getTauxAbsenteismeMoisCourant());
        p.put("KPI_ABSENTS_JOUR",        dashboard.getAbsentsAujourdhui());
        p.put("KPI_DEMANDES_EN_ATTENTE", dashboard.getDemandesEnAttente());
        p.put("KPI_DEMANDES_TRAITEES",   dashboard.getDemandesValidees());
        p.put("KPI_DEMANDES_REFUSEES",   dashboard.getDemandesRejetees());
        p.put("KPI_FORMATIONS_EN_COURS", formations.getFormationsEnAttente());
        p.put("KPI_BUDGET_FORMATION",    formations.getBudgetTotal());
        p.put("KPI_PROJETS_ACTIFS",      dashboard.getProjetsActifs());
        p.put("KPI_TACHES_OUVERTES",     dashboard.getTachesOuvertes());
        p.put("REPORT_PERIOD",
                "Mensuel – " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/yyyy")));

        return pdf("reports/dashboard_report.jrxml", p, statsService.getStatsParDepartement());
    }

    /* ══════════════════════════════════════════════════
       MOTEUR JASPER
       ══════════════════════════════════════════════════ */

    /**
     * Compile et remplit un rapport JasperReports, puis l'exporte en PDF.
     *
     * @param path   le chemin classpath du fichier JRXML (ex : "reports/conges_report.jrxml")
     * @param params la map des paramètres passés au rapport Jasper
     * @param data   les données de la source de données (liste de maps clé-valeur)
     * @return le contenu binaire du PDF généré
     * @throws JRException en cas d'erreur lors de la compilation, du remplissage ou de l'export
     */
    private byte[] pdf(String path, Map<String, Object> params,
                       List<Map<String, Object>> data) throws JRException {
        return JasperExportManager.exportReportToPdf(fill(path, params, data));
    }

    /**
     * Compile et remplit un rapport JasperReports, puis l'exporte en Excel (XLSX).
     * La configuration Excel désactive les pages multiples, supprime les espaces vides,
     * détecte les types de cellules et masque l'arrière-plan blanc.
     *
     * @param path   le chemin classpath du fichier JRXML (ex : "reports/conges_report_excel.jrxml")
     * @param params la map des paramètres passés au rapport Jasper
     * @param data   les données de la source de données (liste de maps clé-valeur)
     * @return le contenu binaire du fichier XLSX généré
     * @throws JRException en cas d'erreur lors de la compilation, du remplissage ou de l'export
     */
    private byte[] excel(String path, Map<String, Object> params,
                         List<Map<String, Object>> data) throws JRException {
        try {
            JasperPrint print = fill(path, params, data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JRXlsxExporter exp = new JRXlsxExporter();
            exp.setExporterInput(new SimpleExporterInput(print));
            exp.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            SimpleXlsxReportConfiguration cfg = new SimpleXlsxReportConfiguration();
            cfg.setOnePagePerSheet(false);
            cfg.setRemoveEmptySpaceBetweenRows(true);
            cfg.setDetectCellType(true);
            cfg.setWhitePageBackground(false);
            exp.setConfiguration(cfg);
            exp.exportReport();
            return out.toByteArray();
        } catch (Exception e) {
            throw new JRException("Erreur Excel : " + path, e);
        }
    }

    /**
     * Charge, compile et remplit un rapport JasperReports depuis le classpath.
     * Normalise les données : clés converties en majuscules, {@code Long}/{@code Integer}
     * convertis en {@code BigDecimal} pour la compatibilité avec les champs numériques JRXML.
     *
     * @param path   le chemin classpath du fichier JRXML
     * @param params la map des paramètres Jasper
     * @param data   les données brutes à injecter dans la source de données
     * @return le {@link JasperPrint} prêt à exporter
     * @throws JRException en cas d'erreur de compilation, de chargement ou de remplissage
     */
    private JasperPrint fill(String path, Map<String, Object> params,
                             List<Map<String, Object>> data) throws JRException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            JasperReport report = JasperCompileManager.compileReport(in);
            Collection<Map<String, ?>> normalized = new ArrayList<>();
            if (data != null) {
                for (Map<String, Object> row : data) {
                    Map<String, Object> up = new LinkedHashMap<>();
                    row.forEach((k, v) -> {
                        Object val = v;
                        if (v instanceof Long l)    val = java.math.BigDecimal.valueOf(l);
                        else if (v instanceof Integer i) val = java.math.BigDecimal.valueOf(i);
                        up.put(k.toUpperCase(), val);
                    });
                    normalized.add(up);
                }
            }
            log.info("[Report] {} — {} ligne(s)", path, normalized.size());
            return JasperFillManager.fillReport(report, params,
                    new JRMapCollectionDataSource(normalized));
        } catch (Exception e) {
            log.error("[Report] Erreur Jasper {} : {}", path, e.getMessage());
            throw new JRException("Erreur rapport : " + path, e);
        }
    }

    /* ── Builders ────────────────────────────────────── */

    /**
     * Crée la map de paramètres de base commune à tous les rapports Jasper.
     * Contient les clés REPORT_TITLE et GENERATED_DATE.
     *
     * @param titre le titre du rapport à afficher dans l'en-tête
     * @return la map de paramètres de base initialisée
     */
    private Map<String, Object> base(String titre) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("REPORT_TITLE",   titre);
        p.put("GENERATED_DATE",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        return p;
    }

    /**
     * Construit la map de paramètres spécifiques au rapport des congés.
     * Récupère les statistiques globales de congé depuis {@link StatsService}.
     * Paramètres : TOTAL_CONGES, CONGES_VALIDES, CONGES_REFUSES, MOYENNE_JOURS.
     *
     * @return la map de paramètres pour le rapport congés, enrichie des KPIs
     */
    private Map<String, Object> buildCongesParams() {
        Map<String, Object> p = base("Récapitulatif des Congés");
        var s = statsService.getCongeStats();
        p.put("TOTAL_CONGES",   s.getTotalConges());
        p.put("CONGES_VALIDES", s.getCongesValides());
        p.put("CONGES_REFUSES", s.getCongesRefuses());
        p.put("MOYENNE_JOURS",  s.getMoyenneJours());
        return p;
    }

    /**
     * Construit la map de paramètres spécifiques au rapport des formations.
     * Paramètres : TOTAL_FORMATIONS, FORMATIONS_VALIDEES, BUDGET_TOTAL, MOYENNE_DUREE.
     *
     * @return la map de paramètres pour le rapport formations, enrichie des KPIs
     */
    private Map<String, Object> buildFormationsParams() {
        Map<String, Object> p = base("Rapport des Formations");
        var s = statsService.getFormationStats();
        p.put("TOTAL_FORMATIONS",    s.getTotalFormations());
        p.put("FORMATIONS_VALIDEES", s.getFormationsValidees());
        p.put("BUDGET_TOTAL",        s.getBudgetTotal());
        p.put("MOYENNE_DUREE",       s.getMoyenneDureeJours());
        return p;
    }

    /**
     * Construit la map de paramètres spécifiques au rapport des projets.
     * Calcule les totaux par statut directement à partir des données du rapport.
     * Paramètres : TOTAL_PROJETS, PROJETS_EN_COURS, PROJETS_TERMINES.
     *
     * @param deptId identifiant de département optionnel (non utilisé pour les projets)
     * @param auth   le contexte d'authentification pour la résolution du rôle
     * @return la map de paramètres pour le rapport projets, enrichie des KPIs
     */
    private Map<String, Object> buildProjetsParams(Long deptId, Authentication auth) {
        Map<String, Object> p = base("Rapport des Projets");
        List<Map<String, Object>> rows = statsService.getProjetsForReport(deptId, auth);
        long total    = rows.size();
        long enCours  = rows.stream().filter(r -> "EN_COURS".equals(r.get("STATUT"))).count();
        long termines = rows.stream().filter(r -> "TERMINE".equals(r.get("STATUT"))).count();
        p.put("TOTAL_PROJETS",    total);
        p.put("PROJETS_EN_COURS", enCours);
        p.put("PROJETS_TERMINES", termines);
        return p;
    }
}