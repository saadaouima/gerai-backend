package com.gerai.analyticsservice.controller;

import com.gerai.analyticsservice.dto.CongeStatsDTO;
import com.gerai.analyticsservice.dto.FormationStatsDTO;
import com.gerai.analyticsservice.service.ReportService;
import com.gerai.analyticsservice.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Alias REST pour les rapports JSON consommés par le dashboard chef.
 * GET /api/rapports/{type} → { stats: [...], rows: [...] }
 * GET /api/rapports/export/{type} → PDF binaire (alias de /api/reports)
 */
@Slf4j
@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class RapportsController {

    private final StatsService  statsService;
    private final ReportService reportService;

    /**
     * Retourne les données JSON d'un rapport (statistiques + lignes de détail)
     * pour un type donné, consommées par le dashboard Angular.
     * Types supportés : "conges", "formations", "projets", "performance".
     *
     * @param type le type de rapport demandé (insensible à la casse)
     * @param auth le contexte d'authentification pour le filtrage par rôle/département
     * @return une map contenant les clés "stats" (indicateurs) et "rows" (données détaillées),
     *         ou HTTP 404 si le type est inconnu
     */
    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> getRapport(
            @PathVariable String type,
            Authentication auth) {

        try {
            return switch (type.toLowerCase()) {
                case "conges"      -> ResponseEntity.ok(buildConges(auth));
                case "formations"  -> ResponseEntity.ok(buildFormations(auth));
                case "projets"     -> ResponseEntity.ok(buildProjets(auth));
                case "performance" -> ResponseEntity.ok(buildPerformance(auth));
                default            -> ResponseEntity.notFound().build();
            };
        } catch (Exception e) {
            log.error("[Rapports] Erreur type={} : {}", type, e.getMessage());
            return ResponseEntity.ok(emptyRapport());
        }
    }

    /**
     * Exporte un rapport au format PDF pour un type donné.
     * Types supportés : "conges", "formations", "projets", "dashboard".
     *
     * @param type le type de rapport à exporter (insensible à la casse)
     * @param auth le contexte d'authentification pour le filtrage par rôle/département
     * @return le fichier PDF binaire avec les en-têtes Content-Disposition appropriés,
     *         ou HTTP 404 si le type est inconnu
     * @throws Exception en cas d'erreur JasperReports lors de la génération
     */
    @GetMapping("/export/{type}")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable String type,
            Authentication auth) {

        try {
            return switch (type.toLowerCase()) {
                case "conges"      -> pdf(reportService.generateCongesPdf(null, auth),     "recapitulatif_conges.pdf");
                case "formations"  -> pdf(reportService.generateFormationsPdf(null, auth), "rapport_formations.pdf");
                case "projets"     -> pdf(reportService.generateProjetsPdf(null, auth),    "rapport_projets.pdf");
                case "dashboard"   -> pdf(reportService.generateDashboardPdf(),            "dashboard_rh.pdf");
                default            -> ResponseEntity.notFound().build();
            };
        } catch (Exception e) {
            log.error("[Export PDF] Erreur type={} : {}", type, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8")
                    .body(("Erreur export PDF : " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/export-excel/{type}")
    public ResponseEntity<byte[]> exportExcel(
            @PathVariable String type,
            Authentication auth) {

        try {
            return switch (type.toLowerCase()) {
                case "conges"     -> excel(reportService.generateCongesExcel(null, auth),     "recapitulatif_conges.xlsx");
                case "formations" -> excel(reportService.generateFormationsExcel(null, auth), "rapport_formations.xlsx");
                default           -> ResponseEntity.notFound().build();
            };
        } catch (Exception e) {
            log.error("[Export Excel] Erreur type={} : {}", type, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8")
                    .body(("Erreur export Excel : " + e.getMessage()).getBytes());
        }
    }

    /* ── Builders ─────────────────────────────────────── */

    /**
     * Construit la structure JSON du rapport congés avec les indicateurs
     * statistiques et les lignes de détail des demandes de congé.
     *
     * @param auth le contexte d'authentification pour le filtrage par département
     * @return une map avec "stats" (4 cartes KPI) et "rows" (liste des congés)
     */
    private Map<String, Object> buildConges(Authentication auth) {
        CongeStatsDTO stats;
        try { stats = statsService.getCongeStats(); }
        catch (Exception e) { log.warn("[Rapports] getCongeStats failed: {}", e.getMessage()); stats = new CongeStatsDTO(); }

        List<Map<String, Object>> rawRows;
        try { rawRows = statsService.getCongesForReport(null, auth); }
        catch (Exception e) { log.warn("[Rapports] getCongesForReport failed: {}", e.getMessage()); rawRows = List.of(); }

        List<Map<String, Object>> statCards = List.of(
            stat("En attente",  stats.getCongesEnAttente(),  "ti ti-clock",        "#FFB64D", false),
            stat("Validés",     stats.getCongesValides(),    "ti ti-circle-check", "#2ed8b6", true),
            stat("Refusés",     stats.getCongesRefuses(),    "ti ti-circle-x",     "#ff5370", false),
            stat("Jours moy.",  round(stats.getMoyenneJours()), "ti ti-calendar", "#4680FF", true)
        );

        List<Map<String, Object>> rows = rawRows.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employe", r.getOrDefault("EMPLOYE_NOM", ""));
            row.put("type",    r.getOrDefault("TYPE_CONGE", "Congé annuel"));
            row.put("debut",   r.getOrDefault("DATE_DEBUT", ""));
            row.put("fin",     r.getOrDefault("DATE_FIN", ""));
            row.put("jours",   r.getOrDefault("NB_JOURS", 0));
            row.put("statut",  mapStatut(String.valueOf(r.getOrDefault("STATUT", ""))));
            return row;
        }).toList();

        return Map.of("stats", statCards, "rows", rows);
    }

    /**
     * Construit la structure JSON du rapport formations avec les indicateurs
     * statistiques et les lignes de détail des demandes de formation.
     *
     * @param auth le contexte d'authentification pour le filtrage par département
     * @return une map avec "stats" (4 cartes KPI) et "rows" (liste des formations)
     */
    private Map<String, Object> buildFormations(Authentication auth) {
        FormationStatsDTO stats;
        try { stats = statsService.getFormationStats(); }
        catch (Exception e) { log.warn("[Rapports] getFormationStats failed: {}", e.getMessage()); stats = new FormationStatsDTO(); }

        List<Map<String, Object>> rawRows;
        try { rawRows = statsService.getFormationsForReport(null, auth); }
        catch (Exception e) { log.warn("[Rapports] getFormationsForReport failed: {}", e.getMessage()); rawRows = List.of(); }

        List<Map<String, Object>> statCards = List.of(
            stat("Soumises",         stats.getTotalFormations(),      "ti ti-file-text",   "#4680FF", true),
            stat("En attente",       stats.getFormationsEnAttente(),  "ti ti-player-play", "#FFB64D", true),
            stat("Complétées",       stats.getFormationsValidees(),   "ti ti-trophy",      "#2ed8b6", true),
            stat("Budget (DT)",      round(stats.getBudgetTotal()),   "ti ti-coin",        "#ff5370", false)
        );

        List<Map<String, Object>> rows = rawRows.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employe",   r.getOrDefault("EMPLOYE_NOM", ""));
            row.put("formation", r.getOrDefault("TRAINING_TITLE", ""));
            row.put("duree",     r.getOrDefault("DURATION_DAYS", 0) + "j");
            Object cout = r.getOrDefault("ESTIMATED_COST", 0);
            row.put("cout",      cout + " DT");
            row.put("statut",    mapStatut(String.valueOf(r.getOrDefault("STATUT", ""))));
            return row;
        }).toList();

        return Map.of("stats", statCards, "rows", rows);
    }

    /**
     * Construit la structure JSON du rapport projets avec les KPIs d'avancement
     * et les lignes de détail des projets (tâches, membres, progression).
     *
     * @param auth le contexte d'authentification pour le filtrage par département/créateur
     * @return une map avec "stats" (4 cartes KPI) et "rows" (liste des projets)
     */
    private Map<String, Object> buildProjets(Authentication auth) {
        List<Map<String, Object>> rawRows;
        try { rawRows = statsService.getProjetsForReport(null, auth); }
        catch (Exception e) { log.warn("[Rapports] getProjetsForReport failed: {}", e.getMessage()); rawRows = List.of(); }

        long projetsActifs = rawRows.stream().filter(r -> "EN_COURS".equals(r.get("STATUT"))).count();
        long termines      = rawRows.stream().filter(r -> "TERMINE".equals(r.get("STATUT"))).count();
        long tachesOuv     = rawRows.stream().mapToLong(r -> {
            long total = toLong(r.get("TOTAL_TACHES"));
            long done  = toLong(r.get("TACHES_COMPLETEES"));
            return Math.max(0, total - done);
        }).sum();

        List<Map<String, Object>> statCards = List.of(
            stat("Total projets",  (long) rawRows.size(), "ti ti-briefcase",     "#4680FF", true),
            stat("En cours",       projetsActifs,          "ti ti-loader",        "#FFB64D", true),
            stat("Terminés",       termines,               "ti ti-circle-check",  "#2ed8b6", true),
            stat("Tâches ouv.",    tachesOuv,              "ti ti-checkbox",      "#ff5370", false)
        );

        List<Map<String, Object>> rows = rawRows.stream().map(r -> {
            long total     = toLong(r.get("TOTAL_TACHES"));
            long completees = toLong(r.get("TACHES_COMPLETEES"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projet",      r.getOrDefault("PROJET", ""));
            row.put("priorite",    mapPriorite(String.valueOf(r.getOrDefault("PRIORITE", ""))));
            row.put("debut",       r.getOrDefault("DATE_DEBUT", "—"));
            row.put("fin",         r.getOrDefault("DATE_FIN",   "—"));
            row.put("equipe",      r.getOrDefault("NB_MEMBRES", 0));
            row.put("taches",      completees + " / " + total);
            row.put("progression", r.getOrDefault("PROGRESSION", 0) + "%");
            row.put("statut",      mapStatutProjet(String.valueOf(r.getOrDefault("STATUT", ""))));
            return row;
        }).toList();

        return Map.of("stats", statCards, "rows", rows);
    }

    /**
     * Construit la structure JSON du rapport performance avec le taux d'acceptation,
     * le taux d'absentéisme et le top 5 des employés les plus absents.
     *
     * @param auth le contexte d'authentification pour le filtrage par département
     * @return une map avec "stats" (4 cartes KPI) et "rows" (top absences)
     */
    private Map<String, Object> buildPerformance(Authentication auth) {
        long absents = 0;
        double tauxAbs = 0;
        try {
            var d = statsService.getDashboard(auth);
            tauxAbs = d.getTauxAbsenteismeMoisCourant();
            absents = d.getAbsentsAujourdhui();
        } catch (Exception e) { log.warn("[Rapports] getDashboard(perf) failed: {}", e.getMessage()); }

        // Tâches ouvertes from projets rawRows (avoids NULL DEPT_ID issue for chefs)
        long tachesOuv = 0;
        try {
            tachesOuv = statsService.getProjetsForReport(null, auth).stream()
                .mapToLong(r -> Math.max(0, toLong(r.get("TOTAL_TACHES")) - toLong(r.get("TACHES_COMPLETEES"))))
                .sum();
        } catch (Exception e) { log.warn("[Rapports] getProjetsForReport(perf) failed: {}", e.getMessage()); }

        // Taux acceptation from formations rawRows (meaningful for both chef and RH)
        double tauxAcc = 0;
        try {
            var fRows = statsService.getFormationsForReport(null, auth);
            long fTotal = fRows.size();
            long fAcceptes = fRows.stream().filter(r -> {
                String s = String.valueOf(r.getOrDefault("STATUT", ""));
                return s.equals("APPROUVE_RH") || s.equals("PLANIFIEE")
                    || s.equals("EN_COURS")    || s.equals("COMPLETEE");
            }).count();
            tauxAcc = fTotal > 0 ? Math.round((fAcceptes * 100.0) / fTotal * 10.0) / 10.0 : 0;
        } catch (Exception e) { log.warn("[Rapports] getFormationsForReport(perf) failed: {}", e.getMessage()); }

        List<Map<String, Object>> statCards = List.of(
            stat("Taux acceptation", round(tauxAcc) + "%", "ti ti-star",        "#4680FF", tauxAcc >= 70),
            stat("Tâches ouvertes",  tachesOuv,            "ti ti-checkbox",    "#2ed8b6", true),
            stat("Absents auj.",     absents,               "ti ti-user-off",   "#ff5370", false),
            stat("Absentéisme",      round(tauxAbs) + "%",  "ti ti-clock-check","#FFB64D", tauxAbs < 5)
        );

        List<Map<String, Object>> rows;
        try {
            rows = statsService.getTop5EmployesAbsences(auth).stream().map(r -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("employe",     r.getOrDefault("NOM", ""));
                row.put("departement", r.getOrDefault("DEPARTEMENT", ""));
                row.put("absences",    r.getOrDefault("TOTAL_JOURS", 0));
                row.put("score",       "—");
                row.put("appreciation","—");
                return row;
            }).toList();
        } catch (Exception e) { log.warn("[Rapports] getTop5EmployesAbsences failed: {}", e.getMessage()); rows = List.of(); }

        return Map.of("stats", statCards, "rows", rows);
    }

    /* ── Helpers ──────────────────────────────────────── */

    /**
     * Retourne une structure de rapport vide (stats et rows vides),
     * utilisée comme valeur de repli en cas d'erreur.
     *
     * @return une map avec deux listes vides
     */
    private Map<String, Object> emptyRapport() {
        return Map.of("stats", List.of(), "rows", List.of());
    }

    /**
     * Crée une carte KPI pour le rapport Angular.
     *
     * @param label   libellé affiché sur la carte
     * @param value   valeur numérique ou chaîne à afficher
     * @param icon    classe CSS de l'icône Tabler (ex : "ti ti-clock")
     * @param color   couleur hexadécimale ou CSS de la carte
     * @param trendUp {@code true} si la tendance est positive (affichage vert)
     * @return une map représentant la carte KPI
     */
    private Map<String, Object> stat(String label, Object value, String icon, String color, boolean trendUp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label",   label);
        m.put("value",   value);
        m.put("icon",    icon);
        m.put("color",   color);
        m.put("trend",   "");
        m.put("trendUp", trendUp);
        return m;
    }

    /**
     * Traduit un code de priorité Oracle en libellé lisible en français.
     *
     * @param raw le code brut Oracle (ex : "CRITIQUE", "HAUTE", "NORMALE", "FAIBLE")
     * @return le libellé français correspondant, ou la valeur brute si inconnue
     */
    private String mapPriorite(String raw) {
        return switch (raw) {
            case "CRITIQUE" -> "Critique";
            case "HAUTE"    -> "Haute";
            case "NORMALE"  -> "Normale";
            case "FAIBLE"   -> "Faible";
            default         -> raw;
        };
    }

    /**
     * Convertit un objet Oracle en {@code long}, en retournant 0 en cas de null
     * ou de valeur non numérique.
     *
     * @param val la valeur Oracle à convertir (Number, String, etc.)
     * @return la valeur {@code long} correspondante, ou 0 si la conversion échoue
     */
    private long toLong(Object val) {
        if (val == null) return 0L;
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    /**
     * Traduit un statut de projet Oracle en libellé lisible en français.
     *
     * @param raw le code brut Oracle (ex : "EN_COURS", "TERMINE", "PLANIFIE")
     * @return le libellé français correspondant, ou la valeur brute si inconnue
     */
    private String mapStatutProjet(String raw) {
        return switch (raw) {
            case "EN_COURS"   -> "En cours";
            case "TERMINE"    -> "Terminé";
            case "PLANIFIE"   -> "Planifié";
            case "EN_PAUSE"   -> "En pause";
            case "ANNULE"     -> "Annulé";
            default           -> raw;
        };
    }

    /**
     * Traduit un statut de demande RH Oracle en libellé lisible en français.
     * Couvre les statuts des congés, formations et autres types de demandes.
     *
     * @param raw le code brut Oracle (ex : "EN_ATTENTE", "VALIDE_RH", "REFUSE")
     * @return le libellé français correspondant, ou la valeur brute si inconnue
     */
    private String mapStatut(String raw) {
        return switch (raw) {
            case "EN_ATTENTE"    -> "En attente";
            case "VALIDE_CHEF"   -> "Validé chef";
            case "VALIDE_RH"     -> "Validé";
            case "APPROUVE_CHEF" -> "Approuvé chef";
            case "APPROUVE_RH"   -> "Approuvé";
            case "REFUSE"        -> "Refusé";
            case "ANNULE"        -> "Annulé";
            default              -> raw;
        };
    }

    /**
     * Arrondit un Double à une décimale pour l'affichage dans les cartes KPI.
     * Retourne 0 si la valeur est nulle.
     *
     * @param d la valeur à arrondir
     * @return la valeur arrondie à 1 décimale, ou 0 si {@code d} est null
     */
    private Object round(Double d) {
        if (d == null) return 0;
        return Math.round(d * 10.0) / 10.0;
    }

    /**
     * Construit la réponse HTTP pour un téléchargement de fichier PDF.
     *
     * @param data     le contenu binaire du PDF
     * @param filename le nom du fichier proposé au téléchargement
     * @return une réponse HTTP 200 avec le contenu PDF et l'en-tête Content-Disposition
     */
    private ResponseEntity<byte[]> pdf(byte[] data, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    /**
     * Construit la réponse HTTP pour un téléchargement de fichier Excel (XLSX).
     *
     * @param data     le contenu binaire du fichier Excel
     * @param filename le nom du fichier proposé au téléchargement
     * @return une réponse HTTP 200 avec le contenu XLSX et l'en-tête Content-Disposition
     */
    private ResponseEntity<byte[]> excel(byte[] data, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }
}
