package com.gerai.analyticsservice.controller;

import com.gerai.analyticsservice.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints d'export de rapports Jasper (PDF + Excel).
 *
 * ReportService délègue le filtrage à StatsService qui lit le JWT.
 * Le controller passe simplement l'Authentication — aucune logique métier ici.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class ReportController {

    private final ReportService reportService;

    /* ── Congés ──────────────────────────────────────── */

    /**
     * Génère et retourne le rapport PDF récapitulatif des congés.
     * Pour un Chef, seuls les congés de son département sont inclus.
     * Pour un RH/Admin, un filtre optionnel par département peut être appliqué.
     *
     * @param auth   le contexte d'authentification pour le filtrage par rôle
     * @param deptId identifiant de département optionnel (ignoré pour les Chefs)
     * @return le fichier PDF binaire avec les en-têtes de téléchargement
     * @throws Exception en cas d'erreur JasperReports lors de la génération
     */
    @GetMapping("/conges/pdf")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<byte[]> congesPdf(
            Authentication auth,
            @RequestParam(required = false) Long deptId) throws Exception {
        log.info("[Report] PDF Congés | user={}", auth.getName());
        return pdf(reportService.generateCongesPdf(deptId, auth), "recapitulatif_conges.pdf");
    }

    /**
     * Génère et retourne le rapport Excel (XLSX) récapitulatif des congés.
     *
     * @param auth   le contexte d'authentification pour le filtrage par rôle
     * @param deptId identifiant de département optionnel (ignoré pour les Chefs)
     * @return le fichier Excel binaire avec les en-têtes de téléchargement
     * @throws Exception en cas d'erreur JasperReports lors de la génération
     */
    @GetMapping("/conges/excel")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<byte[]> congesExcel(
            Authentication auth,
            @RequestParam(required = false) Long deptId) throws Exception {
        log.info("[Report] Excel Congés | user={}", auth.getName());
        return excel(reportService.generateCongesExcel(deptId, auth), "recapitulatif_conges.xlsx");
    }

    /* ── Formations ──────────────────────────────────── */

    /**
     * Génère et retourne le rapport PDF des formations RH.
     *
     * @param auth   le contexte d'authentification pour le filtrage par rôle
     * @param deptId identifiant de département optionnel (ignoré pour les Chefs)
     * @return le fichier PDF binaire avec les en-têtes de téléchargement
     * @throws Exception en cas d'erreur JasperReports lors de la génération
     */
    @GetMapping("/formations/pdf")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<byte[]> formationsPdf(
            Authentication auth,
            @RequestParam(required = false) Long deptId) throws Exception {
        log.info("[Report] PDF Formations | user={}", auth.getName());
        return pdf(reportService.generateFormationsPdf(deptId, auth), "rapport_formations.pdf");
    }

    /**
     * Génère et retourne le rapport Excel (XLSX) des formations RH.
     *
     * @param auth   le contexte d'authentification pour le filtrage par rôle
     * @param deptId identifiant de département optionnel (ignoré pour les Chefs)
     * @return le fichier Excel binaire avec les en-têtes de téléchargement
     * @throws Exception en cas d'erreur JasperReports lors de la génération
     */
    @GetMapping("/formations/excel")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<byte[]> formationsExcel(
            Authentication auth,
            @RequestParam(required = false) Long deptId) throws Exception {
        log.info("[Report] Excel Formations | user={}", auth.getName());
        return excel(reportService.generateFormationsExcel(deptId, auth), "rapport_formations.xlsx");
    }

    /* ── Dashboard (RH uniquement) ───────────────────── */

    /**
     * Génère et retourne le rapport PDF du tableau de bord RH global.
     * Accessible uniquement aux rôles RH et ADMIN — aucun filtre par département.
     *
     * @return le fichier PDF binaire du dashboard avec les KPIs globaux
     * @throws Exception en cas d'erreur JasperReports lors de la génération
     */
    @GetMapping("/dashboard/pdf")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<byte[]> dashboardPdf() throws Exception {
        log.info("[Report] PDF Dashboard RH global");
        return pdf(reportService.generateDashboardPdf(), "dashboard_rh.pdf");
    }

    /* ── Fiche employé ───────────────────────────────── */

    /**
     * Génère et retourne le rapport PDF de la fiche signalétique d'un employé.
     * Inclut les informations de base (identité, département, poste) et l'historique
     * des demandes RH.
     *
     * @param employeId l'identifiant Oracle de l'employé (EMPLOYEES.employee_id)
     * @return le fichier PDF binaire de la fiche employé
     * @throws Exception en cas d'erreur JasperReports ou si l'employé est introuvable
     */
    @GetMapping("/employe/{employeId}/pdf")
    @PreAuthorize("hasAnyRole('RH','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<byte[]> ficheEmployePdf(@PathVariable Long employeId) throws Exception {
        log.info("[Report] Génération PDF Fiche employé ID={}", employeId);

        // On appelle la nouvelle version du service qui ne prend que l'ID
        byte[] content = reportService.generateFicheEmployePdf(employeId);

        // Le nom du fichier peut rester générique ou être dynamisé dans le service
        return pdf(content, "fiche_employe_" + employeId + ".pdf");
    }

    /* ── Helpers ─────────────────────────────────────── */

    /**
     * Construit la réponse HTTP pour un téléchargement de fichier PDF.
     *
     * @param data     le contenu binaire du PDF généré par JasperReports
     * @param filename le nom du fichier proposé au téléchargement
     * @return une réponse HTTP 200 avec le contenu PDF et l'en-tête Content-Disposition
     */
    private ResponseEntity<byte[]> pdf(byte[] data, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    /**
     * Construit la réponse HTTP pour un téléchargement de fichier Excel (XLSX).
     *
     * @param data     le contenu binaire du fichier Excel généré par JasperReports
     * @param filename le nom du fichier proposé au téléchargement
     * @return une réponse HTTP 200 avec le contenu XLSX et l'en-tête Content-Disposition
     */
    private ResponseEntity<byte[]> excel(byte[] data, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(data);
    }
}