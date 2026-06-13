package com.gerai.analyticsservice.service;

import com.gerai.analyticsservice.config.JwtHelper;
import com.gerai.analyticsservice.dto.CongeStatsDTO;
import com.gerai.analyticsservice.dto.DashboardSummaryDTO;
import com.gerai.analyticsservice.dto.FormationStatsDTO;
import com.gerai.analyticsservice.event.DemandeEvent;
import com.gerai.analyticsservice.model.AbsenceStats;
import com.gerai.analyticsservice.repository.AbsenceStatsRepository;
import com.gerai.analyticsservice.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final AnalyticsRepository    repo;
    private final AbsenceStatsRepository absenceRepo;
    private final JwtHelper              jwt;

    /* ═══════════════════════════════════════════════════════
       DASHBOARD
       ═══════════════════════════════════════════════════════ */

    /**
     * Dashboard adapté au rôle :
     *  - ADMIN/RH  → KPIs globaux tous départements
     *  - CHEF      → KPIs filtrés sur son seul département
     */
    @Cacheable("dashboard")
    @Transactional(readOnly = true)
    public DashboardSummaryDTO getDashboard(Authentication auth) {
        log.debug("[Stats] getDashboard | rôle={}", jwt.getPrimaryRole(auth));
        try {
            if (jwt.isChef(auth)) return buildDashboardChef(auth);
            return buildDashboardGlobal();
        } catch (Exception e) {
            log.error("[Stats] getDashboard error: {}", e.getMessage());
            return DashboardSummaryDTO.builder()
                    .demandesParType(new LinkedHashMap<>())
                    .demandesParStatut(new LinkedHashMap<>())
                    .demandesParMois(new LinkedHashMap<>())
                    .build();
        }
    }

    private DashboardSummaryDTO buildDashboardGlobal() {
        long total    = repo.countTotal();
        long enAttente = repo.countByStatut("EN_ATTENTE");
        long refuses  = repo.countByStatut("REFUSE");
        long annules  = repo.countByStatut("ANNULE");
        long validees = repo.countByStatut("VALIDE_RH")
                + repo.countByStatut("APPROUVE_RH")
                + repo.countByStatut("APPROUVE")
                + repo.countByStatut("LIVRE");

        Double tauxAbsent = repo.getTauxAbsenteismeMoisCourant();

        return DashboardSummaryDTO.builder()
                .totalDemandes(total)
                .demandesEnAttente(enAttente)
                .demandesValidees(validees)
                .demandesRejetees(refuses)
                .demandesAnnulees(annules)
                .demandesParType(toMap(repo.countGroupByType()))
                .demandesParStatut(toMap(repo.countGroupByStatut()))
                .demandesParMois(toMap(repo.countGroupByMois()))
                .tauxAcceptation(total > 0 ? round2((validees * 100.0) / total) : 0)
                .tauxRejet(total > 0 ? round2((refuses * 100.0) / total) : 0)
                .absentsAujourdhui(repo.countAbsentsAujourdhui())
                .tauxAbsenteismeMoisCourant(tauxAbsent != null ? round2(tauxAbsent) : 0.0)
                .projetsActifs(repo.countProjetsActifs())
                .tachesOuvertes(repo.countTachesOuvertes())
                .build();
    }

    private DashboardSummaryDTO buildDashboardChef(Authentication auth) {
        Long deptId = resolveDeptId(auth);
        if (deptId == null) {
            log.warn("[Stats] Dept introuvable pour Chef {}, fallback global", jwt.getEmail(auth));
            return buildDashboardGlobal();
        }

        Double tauxAbsent = repo.getTauxAbsenteismeParDept(deptId);

        // Pour le Chef, les demandes sont filtrées via les rapports congés/formations
        // Le dashboard montre les KPIs de son équipe
        return DashboardSummaryDTO.builder()
                .totalDemandes(0)          // non pertinent pour un Chef
                .demandesEnAttente(0)
                .demandesValidees(0)
                .demandesRejetees(0)
                .demandesAnnulees(0)
                .demandesParType(new LinkedHashMap<>())
                .demandesParStatut(new LinkedHashMap<>())
                .demandesParMois(new LinkedHashMap<>())
                .tauxAcceptation(0)
                .tauxRejet(0)
                .absentsAujourdhui(repo.countAbsentsAujourdhuiParDept(deptId))
                .tauxAbsenteismeMoisCourant(tauxAbsent != null ? round2(tauxAbsent) : 0.0)
                .projetsActifs(repo.countProjetsActifsParDept(deptId))
                .tachesOuvertes(repo.countTachesOuvertesParDept(deptId))
                .build();
    }

    /* ═══════════════════════════════════════════════════════
       STATS CONGÉS
       ═══════════════════════════════════════════════════════ */

    @Cacheable("conge-stats")
    @Transactional(readOnly = true)
    public CongeStatsDTO getCongeStats() {
        try {
            long total     = repo.countTotalConges();
            long valides   = repo.countCongesValides();
            long refuses   = repo.countCongesRefuses();
            long enAttente = repo.countCongesEnAttente();
            Double avg     = repo.avgJoursConge();

            return CongeStatsDTO.builder()
                    .totalConges(total)
                    .congesValides(valides)
                    .congesRefuses(refuses)
                    .congesEnAttente(enAttente)
                    .moyenneJours(avg != null ? round2(avg) : 0.0)
                    .tauxAcceptation(total > 0 ? round2((valides * 100.0) / total) : 0)
                    .tauxRejet(total > 0 ? round2((refuses * 100.0) / total) : 0)
                    .congesParMois(toMap(repo.countCongesGroupByMois()))
                    .congesParType(new LinkedHashMap<>())
                    .build();
        } catch (Exception e) {
            log.error("[Stats] getCongeStats error: {}", e.getMessage());
            return new CongeStatsDTO();
        }
    }

    /* ═══════════════════════════════════════════════════════
       STATS FORMATIONS
       ═══════════════════════════════════════════════════════ */

    @Cacheable("formation-stats")
    @Transactional(readOnly = true)
    public FormationStatsDTO getFormationStats() {
        try {
            long total     = repo.countTotalFormations();
            long validees  = repo.countFormationsValidees();
            long refuses   = repo.countFormationsRefusees();
            long enAttente = repo.countFormationsEnAttente();

            return FormationStatsDTO.builder()
                    .totalFormations(total)
                    .formationsValidees(validees)
                    .formationsRefusees(refuses)
                    .formationsEnAttente(enAttente)
                    .budgetTotal(round2(repo.sumBudgetFormations()))
                    .moyenneDureeJours(round2(repo.avgDureeFormations()))
                    .tauxValidation(total > 0 ? round2((validees * 100.0) / total) : 0)
                    .formationsParMois(toMap(repo.countFormationsGroupByMois()))
                    .formationsParType(new LinkedHashMap<>())
                    .build();
        } catch (Exception e) {
            log.error("[Stats] getFormationStats error: {}", e.getMessage());
            return new FormationStatsDTO();
        }
    }

    /* ═══════════════════════════════════════════════════════
       PAR MOIS + TYPE
       ═══════════════════════════════════════════════════════ */

    @Cacheable("par-mois")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDemandesParMoisEtType() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : repo.countGroupByMoisAndType()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mois",  row[0]);
            m.put("type",  row[1]);
            m.put("total", safeL(row, 2));
            result.add(m);
        }
        return result;
    }

    /* ═══════════════════════════════════════════════════════
       DONNÉES RAPPORT — CONGÉS (avec filtrage automatique)

       Le deptId de l'URL est ignoré pour les Chefs :
       c'est toujours LEUR département qui est utilisé.
       Pour RH/ADMIN, le deptId de l'URL sert de filtre optionnel.
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCongesForReport(Long deptIdFromUrl,
                                                        Authentication auth) {
        try { return getCongesForReportInternal(deptIdFromUrl, auth); }
        catch (Exception e) { log.error("[Stats] getCongesForReport error: {}", e.getMessage()); return List.of(); }
    }

    private List<Map<String, Object>> getCongesForReportInternal(Long deptIdFromUrl,
                                                                  Authentication auth) {
        List<Object[]> rows;

        if (jwt.isChef(auth)) {
            // Chef → forcer son département, ignorer l'URL
            Long deptId = resolveDeptId(auth);
            log.info("[Stats] Congés rapport Chef | dept={}", deptId);
            rows = deptId != null
                    ? repo.listeCongesParDepartement(deptId)
                    : repo.listeCongesForReport();

        } else if (deptIdFromUrl != null) {
            // RH avec filtre département optionnel
            log.info("[Stats] Congés rapport RH | dept={}", deptIdFromUrl);
            rows = repo.listeCongesParDepartement(deptIdFromUrl);

        } else {
            // RH global
            log.info("[Stats] Congés rapport RH global");
            rows = repo.listeCongesForReport();
        }

        return rowsToMaps(rows,
                "REQUESTID","MATRICULE","EMPLOYE_NOM","DEPARTEMENT",
                "DATE_DEBUT","DATE_FIN","NB_JOURS","STATUT",
                "MOTIF","COMMENTAIRE_RH","DATE_CREATION");
    }

    /* ═══════════════════════════════════════════════════════
       DONNÉES RAPPORT — FORMATIONS
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFormationsForReport(Long deptIdFromUrl,
                                                            Authentication auth) {
        try { return getFormationsForReportInternal(deptIdFromUrl, auth); }
        catch (Exception e) { log.error("[Stats] getFormationsForReport error: {}", e.getMessage()); return List.of(); }
    }

    private List<Map<String, Object>> getFormationsForReportInternal(Long deptIdFromUrl,
                                                                      Authentication auth) {
        List<Object[]> rows;

        if (jwt.isChef(auth)) {
            Long deptId = resolveDeptId(auth);
            log.info("[Stats] Formations rapport Chef | dept={}", deptId);
            rows = deptId != null
                    ? repo.listeFormationsParDepartement(deptId)
                    : repo.listeFormationsForReport();

        } else if (deptIdFromUrl != null) {
            log.info("[Stats] Formations rapport RH | dept={}", deptIdFromUrl);
            rows = repo.listeFormationsParDepartement(deptIdFromUrl);

        } else {
            log.info("[Stats] Formations rapport RH global");
            rows = repo.listeFormationsForReport();
        }

        return rowsToMaps(rows,
                "REQUEST_ID","EMPLOYE_NOM","MATRICULE","DEPARTEMENT",
                "TRAINING_TITLE","PROVIDER","PLANNED_DATE",
                "DURATION_DAYS","ESTIMATED_COST","STATUT","DATE_CREATION");
    }

    /* ═══════════════════════════════════════════════════════
       DONNÉES RAPPORT — PROJETS
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProjetsForReport(Long deptIdFromUrl,
                                                         Authentication auth) {
        try { return getProjetsForReportInternal(deptIdFromUrl, auth); }
        catch (Exception e) { log.error("[Stats] getProjetsForReport error: {}", e.getMessage()); return List.of(); }
    }

    private List<Map<String, Object>> getProjetsForReportInternal(Long deptIdFromUrl,
                                                                   Authentication auth) {
        List<Object[]> rows;

        if (jwt.isChef(auth)) {
            Long employeeId = resolveEmployeeId(auth);
            log.info("[Stats] Projets rapport Chef | employeeId={}", employeeId);
            rows = employeeId != null
                    ? repo.listeProjetsParCreateur(employeeId)
                    : repo.listeProjetsForReport();

        } else {
            log.info("[Stats] Projets rapport RH global");
            rows = repo.listeProjetsForReport();
        }

        return rowsToMaps(rows,
                "PROJET", "PRIORITE", "DATE_DEBUT", "DATE_FIN",
                "NB_MEMBRES", "TOTAL_TACHES", "TACHES_COMPLETEES",
                "PROGRESSION", "STATUT");
    }

    /* ═══════════════════════════════════════════════════════
       FICHE EMPLOYÉ
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDemandesEmploye(Long employeeId) {
        return rowsToMaps(repo.demandesParEmploye(employeeId),
                "TYPE","STATUT","DATE_CREATION","DESCRIPTION");
    }

    /* ═══════════════════════════════════════════════════════
       STATS PAR DÉPARTEMENT & TOP ABSENCES
       ═══════════════════════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStatsParDepartement() {
        return rowsToMaps(repo.statsParDepartement(),
                "DEPT_NAME","HEADCOUNT","NB_CONGES","NB_FORMATIONS","NB_PROJETS");
    }

    @Cacheable("top-5-absences")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTop5EmployesAbsences(Authentication auth) {
        if (jwt.isChef(auth)) {
            Long deptId = resolveDeptId(auth);
            if (deptId != null) {
                return rowsToMaps(repo.top5EmployesAbsencesParDept(deptId),
                        "NOM","DEPARTEMENT","TOTAL_JOURS");
            }
        }
        return rowsToMaps(repo.top5EmployesAbsences(),
                "NOM","DEPARTEMENT","TOTAL_JOURS");
    }

    /* ═══════════════════════════════════════════════════════
       KAFKA EVENT → ABSENCE_STATS
       ═══════════════════════════════════════════════════════ */

    @Transactional
    public void saveEvent(DemandeEvent event) {
        Long empId = parseLong(event.getDestinataireId());

        if ("CONGE".equalsIgnoreCase(event.getTypeDemande())
                && "VALIDE_RH".equalsIgnoreCase(event.getStatut())
                && empId != null
                && event.getNbJours() != null
                && event.getNbJours() > 0) {

            upsertAbsenceStats(empId, event.getNbJours().doubleValue(), false);

        } else if ("REFUSE".equalsIgnoreCase(event.getStatut()) && empId != null) {
            upsertAbsenceStats(empId, 0.0, true);
        }

        invalidateAllCaches();
    }

    private void upsertAbsenceStats(Long empId, double jours, boolean isRefus) {
        int annee = LocalDateTime.now().getYear();
        int mois  = LocalDateTime.now().getMonthValue();

        AbsenceStats stats = absenceRepo
                .findByEmployeIdAndAnneeAndMois(empId, annee, mois)
                .orElse(AbsenceStats.builder()
                        .employeId(empId).annee(annee).mois(mois)
                        .nbJoursConge(0.0).nbDemandes(0)
                        .nbValidees(0).nbRefusees(0).build());

        if (isRefus) stats.ajouterDemandeRefusee();
        else         stats.ajouterCongeValide(jours);

        absenceRepo.save(stats);
        log.info("[Stats] AbsenceStats | emp={} | {}/{} | jours={}", empId, mois, annee, jours);
    }

    /* ═══════════════════════════════════════════════════════
       CACHE
       ═══════════════════════════════════════════════════════ */

    @CacheEvict(value={"dashboard","conge-stats","formation-stats","par-mois","top-5-absences"},
            allEntries=true)
    public void invalidateAllCaches() {
        log.info("[Stats] Caches invalidés");
    }

    /* ═══════════════════════════════════════════════════════
       RÉSOLUTION DU DÉPARTEMENT — Stratégie en 3 niveaux
       ═══════════════════════════════════════════════════════ */

    /**
     * Résout le dept_id Oracle pour un Chef connecté.
     *
     * Niveau 1 : claim JWT "dept_id" (zéro requête Oracle) — idéal
     * Niveau 2 : requête Oracle via user_id Keycloak (sub) — fiable
     * Niveau 3 : requête Oracle via email — fallback ultime
     */
    private Long resolveDeptId(Authentication auth) {
        // Niveau 1 : claim custom JWT
        Long deptId = jwt.getDeptId(auth);
        if (deptId != null) {
            log.debug("[Stats] dept_id depuis JWT claim : {}", deptId);
            return deptId;
        }

        // Niveau 2 : Oracle via sub Keycloak
        String sub = jwt.getSubject(auth);
        if (sub != null) {
            deptId = repo.findDeptIdBySubject(sub);
            if (deptId != null) {
                log.debug("[Stats] dept_id depuis Oracle (sub={}) : {}", sub, deptId);
                return deptId;
            }
        }

        // Niveau 3 : Oracle via email
        String email = jwt.getEmail(auth);
        if (email != null) {
            deptId = repo.findDeptIdByEmail(email);
            log.debug("[Stats] dept_id depuis Oracle (email={}) : {}", email, deptId);
            return deptId;
        }

        log.warn("[Stats] Impossible de résoudre dept_id pour {}", auth.getName());
        return null;
    }

    private Long resolveEmployeeId(Authentication auth) {
        String sub = jwt.getSubject(auth);
        if (sub != null) {
            Long empId = repo.findEmployeeIdBySubject(sub);
            if (empId != null) return empId;
        }
        String email = jwt.getEmail(auth);
        if (email != null) {
            Long empId = repo.findEmployeeIdByEmail(email);
            if (empId != null) return empId;
        }
        log.warn("[Stats] Impossible de résoudre employee_id pour {}", auth.getName());
        return null;
    }

    /* ═══════════════════════════════════════════════════════
       HELPERS
       ═══════════════════════════════════════════════════════ */

    private Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        if (rows == null) return m;
        for (Object[] r : rows) {
            m.put(r[0] != null ? r[0].toString() : "INCONNU", safeL(r, 1));
        }
        return m;
    }

    private List<Map<String, Object>> rowsToMaps(List<Object[]> rows, String... cols) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows == null) return result;
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < cols.length && i < row.length; i++) {
                m.put(cols[i], row[i]);
            }
            result.add(m);
        }
        return result;
    }

    private long safeL(Object[] row, int idx) {
        if (row == null || idx >= row.length || row[idx] == null) return 0L;
        return row[idx] instanceof Number n ? n.longValue() : 0L;
    }
    // Dans StatsService.java

    /**
     * Récupère les infos de base pour l'en-tête de la fiche
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEmployeInfos(Long employeeId) {
        List<Object[]> rows = repo.findEmployeBasicInfos(employeeId);
        Map<String, Object> infos = new HashMap<>();

        if (rows != null && !rows.isEmpty()) {
            Object[] row = rows.get(0);

            // On mappe les colonnes selon l'ordre de la nouvelle requête
            infos.put("PRENOM",          row[0] != null ? row[0].toString() : "");
            infos.put("NOM",             row[1] != null ? row[1].toString() : "");
            infos.put("NOM_COMPLET",     infos.get("PRENOM") + " " + infos.get("NOM"));
            infos.put("MATRICULE",       row[2] != null ? row[2].toString() : "N/A");
            infos.put("DEPARTEMENT",     row[3] != null ? row[3].toString() : "Non assigné");
            infos.put("EMAIL",           row[4] != null ? row[4].toString() : "-");
            infos.put("TELEPHONE",       row[5] != null ? row[5].toString() : "-");
            infos.put("DATE_EMBAUCHE",   row[6] != null ? row[6].toString() : "-");
        } else {
            infos.put("NOM_COMPLET", "Employé Inconnu");
            infos.put("MATRICULE", "N/A");
        }
        return infos;
    }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}