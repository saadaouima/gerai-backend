package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.dto.RapportDataDTO;
import com.gerai.projetsservice.dto.RapportDataDTO.StatCardDTO;
import com.gerai.projetsservice.model.PerformanceEval;
import com.gerai.projetsservice.model.Project;
import com.gerai.projetsservice.model.SoldeCongeAdmin;
import com.gerai.projetsservice.repository.PerformanceEvalRepository;
import com.gerai.projetsservice.repository.ProjectMemberRepository;
import com.gerai.projetsservice.repository.ProjectRepository;
import com.gerai.projetsservice.repository.SoldeCongeAdminRepository;
import com.gerai.projetsservice.service.EmployeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour la génération des rapports RH agrégés.
 * <p>
 * Expose l'endpoint générique {@code GET /api/rapports/{type}} produisant des données
 * structurées pour les 4 types de rapports disponibles :
 * <ul>
 *   <li>{@code CONGES} — statistiques sur les congés (acceptés, refusés, utilisés, solde restant).</li>
 *   <li>{@code FORMATIONS} — statistiques sur les demandes de formation (données statiques pour l'instant).</li>
 *   <li>{@code PROJETS} — état des projets (en cours, terminés, en retard, progression).</li>
 *   <li>{@code PERFORMANCE} — synthèse des évaluations (score moyen, validées, en cours).</li>
 * </ul>
 * </p>
 * <p>
 * {@code @RestController} : contrôleur REST retournant du JSON.
 * </p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class RapportsController {

    private final SoldeCongeAdminRepository soldeRepo;
    private final ProjectRepository         projectRepo;
    private final ProjectMemberRepository   memberRepo;
    private final PerformanceEvalRepository evalRepo;
    private final EmployeService            employeService;

    /**
     * Retourne les données agrégées d'un rapport RH selon le type demandé.
     *
     * @param type type du rapport ({@code CONGES}, {@code FORMATIONS}, {@code PROJETS} ou {@code PERFORMANCE})
     * @return le DTO de rapport contenant les cartes statistiques et les lignes de données (HTTP 200)
     */
    @GetMapping("/{type}")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH','RH')")
    public ResponseEntity<RapportDataDTO> getRapport(@PathVariable String type) {
        RapportDataDTO data = switch (type.toUpperCase()) {
            case "CONGES"      -> buildConges();
            case "FORMATIONS"  -> buildFormations();
            case "PROJETS"     -> buildProjets();
            case "PERFORMANCE" -> buildPerformance();
            default            -> RapportDataDTO.builder().stats(List.of()).rows(List.of()).build();
        };
        return ResponseEntity.ok(data);
    }

    // ── CONGÉS ────────────────────────────────────────────────────────────────

    /**
     * Construit le rapport de congés à partir des données {@link SoldeCongeAdmin}.
     *
     * @return DTO avec 4 cartes statistiques (acceptés, refusés, utilisés, solde) et lignes par employé
     */
    private RapportDataDTO buildConges() {
        List<SoldeCongeAdmin> all = soldeRepo.findAll();

        long totalAcceptes = all.stream()
                .mapToLong(s -> s.getCongesAcceptes() == null ? 0 : s.getCongesAcceptes().longValue()).sum();
        long totalRejetes = all.stream()
                .mapToLong(s -> s.getCongesRejetes() == null ? 0 : s.getCongesRejetes().longValue()).sum();
        long totalUtilises = all.stream()
                .mapToLong(s -> s.getCongesUtilises() == null ? 0 : s.getCongesUtilises().longValue()).sum();
        long totalSolde = all.stream()
                .mapToLong(s -> s.getSoldeActuel() == null ? 0 : s.getSoldeActuel().longValue()).sum();

        List<StatCardDTO> stats = List.of(
                StatCardDTO.builder().label("Congés acceptés").value(totalAcceptes)
                        .icon("ti ti-circle-check").color("#2ed8b6")
                        .trend(totalAcceptes + " jours validés").trendUp(true).build(),
                StatCardDTO.builder().label("Congés refusés").value(totalRejetes)
                        .icon("ti ti-circle-x").color("#ff5370")
                        .trend("Sur l'ensemble").trendUp(false).build(),
                StatCardDTO.builder().label("Jours utilisés").value(totalUtilises)
                        .icon("ti ti-calendar-minus").color("#FFB64D")
                        .trend("Tous employés").trendUp(false).build(),
                StatCardDTO.builder().label("Solde restant").value(totalSolde)
                        .icon("ti ti-calendar-plus").color("#4680FF")
                        .trend("Jours disponibles").trendUp(true).build()
        );

        List<Map<String, Object>> rows = all.stream().map(s -> {
            String statut;
            if (s.getCongesAcceptes() != null && s.getCongesAcceptes() > 0) statut = "Validé";
            else if (s.getCongesRejetes() != null && s.getCongesRejetes() > 0)  statut = "Refusé";
            else                                                                  statut = "En attente";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employe",     s.getEmployeNom());
            row.put("departement", s.getDepartement());
            row.put("utilises",    s.getCongesUtilises()  == null ? 0 : s.getCongesUtilises().intValue());
            row.put("acceptes",    s.getCongesAcceptes()  == null ? 0 : s.getCongesAcceptes().intValue());
            row.put("rejetes",     s.getCongesRejetes()   == null ? 0 : s.getCongesRejetes().intValue());
            row.put("statut",      statut);
            return row;
        }).collect(Collectors.toList());

        return RapportDataDTO.builder().stats(stats).rows(rows).build();
    }

    // ── FORMATIONS ────────────────────────────────────────────────────────────

    /**
     * Construit le rapport de formations (données statiques, en attente d'intégration du module).
     *
     * @return DTO avec 4 cartes statistiques à zéro et aucune ligne
     */
    private RapportDataDTO buildFormations() {
        List<StatCardDTO> stats = List.of(
                StatCardDTO.builder().label("Demandes soumises").value(0)
                        .icon("ti ti-file-text").color("#4680FF").trend("Aucune demande").trendUp(false).build(),
                StatCardDTO.builder().label("En cours").value(0)
                        .icon("ti ti-player-play").color("#FFB64D").trend("Aucune active").trendUp(false).build(),
                StatCardDTO.builder().label("Complétées").value(0)
                        .icon("ti ti-trophy").color("#2ed8b6").trend("Aucune").trendUp(false).build(),
                StatCardDTO.builder().label("Budget utilisé").value("0%")
                        .icon("ti ti-coin").color("#ff5370").trend("Non configuré").trendUp(false).build()
        );
        return RapportDataDTO.builder().stats(stats).rows(List.of()).build();
    }

    // ── PROJETS ───────────────────────────────────────────────────────────────

    /**
     * Construit le rapport de projets avec métriques d'état et progression.
     * <p>
     * Détecte automatiquement les projets en retard (date de fin dépassée sans être terminé)
     * et résout le nom du chef via {@link EmployeService}.
     * </p>
     *
     * @return DTO avec 4 cartes statistiques (total, en cours, terminés, en retard) et lignes par projet
     */
    private RapportDataDTO buildProjets() {
        List<Project> all = projectRepo.findAll();
        LocalDate today = LocalDate.now();

        long enCours  = all.stream().filter(p -> "EN_COURS".equals(p.getStatus())).count();
        long termines = all.stream().filter(p -> "TERMINE".equals(p.getStatus())).count();
        long enRetard = all.stream()
                .filter(p -> !"TERMINE".equals(p.getStatus()) && p.getEndDate() != null && p.getEndDate().isBefore(today))
                .count();

        List<StatCardDTO> stats = List.of(
                StatCardDTO.builder().label("Total projets").value((long) all.size())
                        .icon("ti ti-briefcase").color("#4680FF")
                        .trend(all.size() + " projets").trendUp(true).build(),
                StatCardDTO.builder().label("En cours").value(enCours)
                        .icon("ti ti-loader").color("#FFB64D").trend("Actifs").trendUp(true).build(),
                StatCardDTO.builder().label("Terminés").value(termines)
                        .icon("ti ti-circle-check").color("#2ed8b6").trend("Livrés").trendUp(true).build(),
                StatCardDTO.builder().label("En retard").value(enRetard)
                        .icon("ti ti-alert-triangle").color("#ff5370")
                        .trend(enRetard > 0 ? "Action requise" : "Aucun retard").trendUp(enRetard == 0).build()
        );

        List<Map<String, Object>> rows = all.stream().map(p -> {
            var chef = employeService.getEmployeById(p.getCreatedBy());
            String chefNom = chef != null ? chef.getNomComplet() : "Chef #" + p.getCreatedBy();

            long memberCount = memberRepo.findActiveByProjectId(p.getProjectId()).size();

            String statutFr;
            if (!"TERMINE".equals(p.getStatus()) && p.getEndDate() != null && p.getEndDate().isBefore(today)) {
                statutFr = "En retard";
            } else {
                statutFr = switch (p.getStatus()) {
                    case "EN_COURS"   -> "En cours";
                    case "TERMINE"    -> "Terminé";
                    case "EN_ATTENTE" -> "En attente";
                    case "EN_PAUSE"   -> "En attente";
                    default           -> p.getStatus();
                };
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projet",      p.getName());
            row.put("chef",        chefNom);
            row.put("equipe",      memberCount);
            row.put("progression", p.getProgressPct() + "%");
            row.put("statut",      statutFr);
            return row;
        }).collect(Collectors.toList());

        return RapportDataDTO.builder().stats(stats).rows(rows).build();
    }

    // ── PERFORMANCE ───────────────────────────────────────────────────────────

    /**
     * Construit le rapport de performance à partir des évaluations enregistrées.
     * <p>
     * Calcule le score moyen de l'équipe, distingue les évaluations validées des évaluations
     * en cours et résout le nom de l'employé via {@link EmployeService}.
     * </p>
     *
     * @return DTO avec 4 cartes statistiques (score moyen, total, validées, en cours)
     *         et lignes par évaluation avec mention qualitative
     */
    private RapportDataDTO buildPerformance() {
        List<PerformanceEval> all = evalRepo.findAll();

        double avgScore = all.stream()
                .filter(e -> e.getScore() != null)
                .mapToDouble(PerformanceEval::getScore)
                .average().orElse(0);
        long valides  = all.stream().filter(e -> "VALIDE".equals(e.getStatus())).count();
        long enCours  = all.stream().filter(e -> !"VALIDE".equals(e.getStatus())).count();

        List<StatCardDTO> stats = List.of(
                StatCardDTO.builder().label("Score moyen équipe").value(String.format("%.0f%%", avgScore))
                        .icon("ti ti-star").color("#4680FF")
                        .trend("Toutes évaluations").trendUp(avgScore >= 70).build(),
                StatCardDTO.builder().label("Évaluations total").value((long) all.size())
                        .icon("ti ti-clipboard-list").color("#2ed8b6")
                        .trend(all.size() + " évaluations").trendUp(true).build(),
                StatCardDTO.builder().label("Validées").value(valides)
                        .icon("ti ti-circle-check").color("#2ed8b6").trend("Clôturées").trendUp(true).build(),
                StatCardDTO.builder().label("En cours").value(enCours)
                        .icon("ti ti-clock").color("#FFB64D").trend("En attente validation").trendUp(false).build()
        );

        List<Map<String, Object>> rows = all.stream().map(e -> {
            var emp = employeService.getEmployeById(e.getEmployeeId());
            String empNom = emp != null ? emp.getNomComplet() : "Employé #" + e.getEmployeeId();

            double score = e.getScore() == null ? 0 : e.getScore();
            String appreciation;
            if      (score >= 90) appreciation = "Excellent";
            else if (score >= 80) appreciation = "Très bien";
            else if (score >= 70) appreciation = "Bien";
            else                  appreciation = "Assez bien";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employe",   empNom);
            row.put("annee",     e.getPeriodYear());
            row.put("trimestre", e.getPeriodQuarter() == null ? "-" : e.getPeriodQuarter());
            row.put("score",     String.format("%.0f%%", score));
            row.put("appreciation", appreciation);
            return row;
        }).collect(Collectors.toList());

        return RapportDataDTO.builder().stats(stats).rows(rows).build();
    }
}
