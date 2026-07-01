package com.gerai.projetsservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerai.projetsservice.model.CampagneEvaluation;
import com.gerai.projetsservice.model.PerformanceEval;
import com.gerai.projetsservice.repository.CampagneEvaluationRepository;
import com.gerai.projetsservice.repository.PerformanceEvalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion des évaluations de performance et campagnes d'évaluation.
 * <p>
 * Expose les endpoints {@code /api/evaluations} permettant :
 * <ul>
 *   <li>La gestion des campagnes d'évaluation (CRUD, activation, clôture).</li>
 *   <li>La consultation des évaluations avec filtres par chef ou employé.</li>
 *   <li>La soumission de l'auto-évaluation par l'employé.</li>
 *   <li>La saisie de l'évaluation par le chef de projet (avec calcul du score moyen).</li>
 *   <li>La validation finale par la RH (avec ajustement du score).</li>
 *   <li>La clôture d'une évaluation.</li>
 *   <li>La création individuelle ou en lot d'évaluations pour une équipe.</li>
 * </ul>
 * </p>
 * <p>
 * Complète {@code /api/admin/evals} (endpoint legacy) qui reste inchangé.
 * </p>
 * <p>
 * {@code @RestController} : contrôleur REST retournant du JSON.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class EvaluationController {

    private final CampagneEvaluationRepository campagneRepo;
    private final PerformanceEvalRepository    evalRepo;
    private final JdbcTemplate                 jdbc;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /* ── Campagnes ───────────────────────────────────────── */

    /**
     * Retourne toutes les campagnes d'évaluation.
     *
     * @return liste des campagnes (HTTP 200)
     */
    @GetMapping("/campagnes")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH','CHEF')")
    public ResponseEntity<List<CampagneEvaluation>> getCampagnes() {
        return ResponseEntity.ok(campagneRepo.findAll());
    }

    /**
     * Crée une nouvelle campagne d'évaluation (statut initial {@code PLANIFIEE}).
     *
     * @param body données de la campagne (titre, période, année...)
     * @return la campagne créée (HTTP 201)
     */
    @PostMapping("/campagnes")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<CampagneEvaluation> createCampagne(@RequestBody CampagneEvaluation body) {
        body.setId(null);
        if (body.getStatut() == null) body.setStatut("PLANIFIEE");
        return ResponseEntity.status(HttpStatus.CREATED).body(campagneRepo.save(body));
    }

    /**
     * Active une campagne d'évaluation (passage en statut {@code ACTIVE}).
     *
     * @param id identifiant de la campagne
     * @return la campagne activée (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/campagnes/{id}/activer")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<CampagneEvaluation> activerCampagne(@PathVariable Long id) {
        return campagneRepo.findById(id).map(c -> {
            c.setStatut("ACTIVE");
            return ResponseEntity.ok(campagneRepo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Clôture une campagne d'évaluation (passage en statut {@code CLOTUREE}).
     *
     * @param id identifiant de la campagne
     * @return la campagne clôturée (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/campagnes/{id}/cloturer")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<CampagneEvaluation> cloturerCampagne(@PathVariable Long id) {
        return campagneRepo.findById(id).map(c -> {
            c.setStatut("CLOTUREE");
            return ResponseEntity.ok(campagneRepo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime une campagne d'évaluation.
     *
     * @param id identifiant de la campagne à supprimer
     * @return HTTP 204 si supprimée, HTTP 404 si introuvable
     */
    @DeleteMapping("/campagnes/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Void> deleteCampagne(@PathVariable Long id) {
        if (!campagneRepo.existsById(id)) return ResponseEntity.notFound().build();
        campagneRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ── Évaluations ─────────────────────────────────────── */

    /**
     * Retourne les évaluations de performance, filtrées optionnellement par chef ou employé.
     *
     * @param chefId    identifiant du chef évaluateur (optionnel)
     * @param employeId identifiant de l'employé évalué (optionnel)
     * @return liste des évaluations sérialisées en map (HTTP 200)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH','CHEF','EMPLOYE')")
    public ResponseEntity<List<Map<String, Object>>> getEvaluations(
            @RequestParam(required = false) Long chefId,
            @RequestParam(required = false) Long employeId) {
        var all = evalRepo.findAll().stream();
        if (chefId    != null) all = all.filter(e -> chefId.equals(e.getEvaluatorId()));
        if (employeId != null) all = all.filter(e -> employeId.equals(e.getEmployeeId()));
        return ResponseEntity.ok(all.map(this::toMap).toList());
    }

    /**
     * Soumet l'auto-évaluation de l'employé pour une évaluation donnée.
     * <p>
     * Passe le statut à {@code AUTO_SOUMISE} et enregistre le commentaire libre optionnel.
     * </p>
     *
     * @param id   identifiant de l'évaluation
     * @param body map JSON optionnelle avec {@code commentaireLibre}
     * @return l'évaluation mise à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}/auto-evaluation")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> submitAutoEvaluation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return evalRepo.findById(id).map(e -> {
            e.setStatus("AUTO_SOUMISE");
            if (body != null) {
                if (body.containsKey("commentaireLibre")) {
                    e.setComments(String.valueOf(body.get("commentaireLibre")));
                }
                // Persist auto-eval scores inside criteriaScores JSON (separate key)
                try {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    String existing = e.getCriteriaScores();
                    if (existing != null && existing.startsWith("{")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> prev = MAPPER.readValue(existing, Map.class);
                        meta.putAll(prev);
                    }
                    Map<String, Object> ae = new LinkedHashMap<>();
                    for (String k : List.of("realisationObjectifs","qualiteTravail","respectDelais",
                            "communication","autonomie","collaboration","adaptabilite",
                            "formationsCompletees","nouvellesCompetences",
                            "reussitesPeriode","defisRencontres","objectifsSuivante","commentaireLibre")) {
                        if (body.containsKey(k)) ae.put(k, body.get(k));
                    }
                    meta.put("autoEval", ae);
                    e.setCriteriaScores(MAPPER.writeValueAsString(meta));
                } catch (Exception ignored) {}
            }
            return ResponseEntity.ok(toMap(evalRepo.save(e)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Soumet l'évaluation du chef de projet.
     * <p>
     * Calcule un score moyen pondéré à partir des critères de notation et
     * passe le statut à {@code VALIDEE_CHEF}.
     * </p>
     *
     * @param id   identifiant de l'évaluation
     * @param body map JSON optionnelle avec les critères de notation et {@code commentaireGlobal}
     * @return l'évaluation mise à jour avec le score calculé (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}/evaluation-chef")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> submitChefEvaluation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return evalRepo.findById(id).map(e -> {
            e.setStatus("VALIDEE_CHEF");
            if (body != null) {
                if (body.containsKey("commentaireGlobal")) {
                    e.setComments(String.valueOf(body.get("commentaireGlobal")));
                }
                if (body.containsKey("recommandation")) {
                    e.setImprovements(String.valueOf(body.get("recommandation")));
                }
                // Compute proper weighted breakdown scores
                double sumObj = 0; int cntObj = 0;
                for (String k : new String[]{"realisationObjectifs","qualiteTravail","respectDelais","contributionProjets"}) {
                    Object v = body.get(k); if (v instanceof Number n) { sumObj += n.doubleValue(); cntObj++; }
                }
                double sumComp = 0; int cntComp = 0;
                for (String k : new String[]{"communication","autonomie","collaboration","adaptabilite","leadershipInitiative"}) {
                    Object v = body.get(k); if (v instanceof Number n) { sumComp += n.doubleValue(); cntComp++; }
                }
                // Extract formationsCompletees from the stored auto-eval
                int nForms = 0;
                try {
                    String cs = e.getCriteriaScores();
                    if (cs != null && cs.startsWith("{")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> md0 = MAPPER.readValue(cs, Map.class);
                        if (md0.containsKey("autoEval")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> ae = (Map<String, Object>) md0.get("autoEval");
                            Object fc = ae.get("formationsCompletees");
                            if (fc instanceof Number fn) nForms = fn.intValue();
                        }
                    }
                } catch (Exception ignored) {}
                int scoreObjectifs   = cntObj  > 0 ? (int) Math.round((sumObj  / cntObj  / 5.0) * 40.0) : 0;
                int scoreCompetences = cntComp > 0 ? (int) Math.round((sumComp / cntComp / 5.0) * 30.0) : 0;
                int scoreFormations  = nForms >= 3 ? 20 : nForms == 2 ? 13 : nForms == 1 ? 7 : 0;
                int scorePresence    = 10; // default: 100% presence
                int scoreFinalVal    = scoreObjectifs + scoreCompetences + scoreFormations + scorePresence;
                e.setScore((double) scoreFinalVal);
                // Persist chef eval + breakdown inside criteriaScores JSON
                try {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    String existing = e.getCriteriaScores();
                    if (existing != null && existing.startsWith("{")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> prev = MAPPER.readValue(existing, Map.class);
                        meta.putAll(prev);
                    }
                    // Store a plain copy of body to avoid Jackson serialisation issues
                    // with any non-serialisable types the request body may contain.
                    Map<String, Object> evalChefClean = new LinkedHashMap<>(body);
                    meta.put("evalChef",         evalChefClean);
                    meta.put("scoreObjectifs",   scoreObjectifs);
                    meta.put("scoreCompetences", scoreCompetences);
                    meta.put("scoreFormations",  scoreFormations);
                    meta.put("scorePresence",    scorePresence);
                    meta.put("scoreFinal",       scoreFinalVal);
                    e.setCriteriaScores(MAPPER.writeValueAsString(meta));
                } catch (Exception ex) {
                    log.error("Failed to persist chef evaluation criteriaScores for eval {}: {}", e.getEvalId(), ex.getMessage());
                }
            }
            return ResponseEntity.ok(toMap(evalRepo.save(e)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Valide une évaluation par la RH avec possibilité d'ajuster le score final.
     * <p>
     * Passe le statut à {@code VALIDEE_RH}.
     * </p>
     *
     * @param id   identifiant de l'évaluation
     * @param body map JSON optionnelle avec {@code scoreAjuste} et {@code commentaireRh}
     * @return l'évaluation validée (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}/valider-rh")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> validerRh(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return evalRepo.findById(id).map(e -> {
            e.setStatus("VALIDEE_RH");
            if (body != null && body.containsKey("scoreAjuste")) {
                Object s = body.get("scoreAjuste");
                if (s instanceof Number n) e.setScore(n.doubleValue());
            }
            if (body != null && body.containsKey("commentaireRh")) {
                e.setComments(String.valueOf(body.get("commentaireRh")));
            }
            return ResponseEntity.ok(toMap(evalRepo.save(e)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Renvoie une évaluation au chef pour correction (statut {@code RETOURNEE}).
     * La RH doit fournir un commentaire obligatoire expliquant le motif du renvoi.
     *
     * @param id   identifiant de l'évaluation
     * @param body map JSON avec {@code commentaireRetour} (obligatoire) et {@code renvoyePar}
     * @return l'évaluation mise à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}/renvoyer")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> renvoyerAuChef(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return evalRepo.findById(id).map(e -> {
            e.setStatus("RETOURNEE");
            try {
                Map<String, Object> meta = new LinkedHashMap<>();
                String existing = e.getCriteriaScores();
                if (existing != null && existing.startsWith("{")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> prev = MAPPER.readValue(existing, Map.class);
                    meta.putAll(prev);
                }
                if (body != null) {
                    if (body.containsKey("commentaireRetour"))
                        meta.put("commentaireRetour", body.get("commentaireRetour"));
                    if (body.containsKey("renvoyePar"))
                        meta.put("renvoyePar", body.get("renvoyePar"));
                }
                e.setCriteriaScores(MAPPER.writeValueAsString(meta));
            } catch (Exception ex) {}
            return ResponseEntity.ok(toMap(evalRepo.save(e)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Clôture définitivement une évaluation (statut {@code CLOTUREE}).
     *
     * @param id identifiant de l'évaluation
     * @return l'évaluation clôturée (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}/cloturer")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> cloturerEval(@PathVariable Long id) {
        return evalRepo.findById(id).map(e -> {
            e.setStatus("CLOTUREE");
            return ResponseEntity.ok(toMap(evalRepo.save(e)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Crée une évaluation individuelle pour un employé.
     *
     * @param body map JSON avec {@code chefId}, {@code employeId}, {@code campagneId},
     *             {@code titre}, {@code periode}, {@code annee} et informations de l'employé
     * @return l'évaluation créée (HTTP 201)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> creerEvaluation(@RequestBody Map<String, Object> body) {
        PerformanceEval eval = buildEval(body,
                toLong(body.get("chefId")),
                toLong(body.get("employeId")),
                String.valueOf(body.getOrDefault("titre", "Évaluation " + LocalDate.now().getYear())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(evalRepo.save(eval)));
    }

    /**
     * Crée en lot des évaluations pour tous les membres d'une équipe.
     *
     * @param body map JSON avec {@code chefId}, {@code titre} et la liste {@code membres}
     *             (chaque membre contient {@code id}, {@code nom}, {@code prenom}...)
     * @return liste des évaluations créées (HTTP 201), HTTP 400 si la liste des membres est vide
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('CHEF','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Map<String, Object>>> creerEvaluationEquipe(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> membres = (List<Map<String, Object>>) body.get("membres");
        if (membres == null || membres.isEmpty()) return ResponseEntity.badRequest().build();

        Long chefId = toLong(body.get("chefId"));
        String titre = String.valueOf(body.getOrDefault("titre",
                "Évaluation équipe " + LocalDate.now().getYear()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> m : membres) {
            Map<String, Object> ctx = new LinkedHashMap<>(body);
            ctx.putAll(m);
            ctx.put("employeId", toLong(m.get("id")));
            result.add(toMap(evalRepo.save(buildEval(ctx, chefId, toLong(m.get("id")), titre))));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /* ── Helper ──────────────────────────────────────────── */

    /**
     * Construit une entité {@link PerformanceEval} à partir d'un body JSON et du contexte.
     * <p>
     * Si une campagne est associée, récupère automatiquement sa période, année et titre.
     * Effectue un lookup Oracle pour résoudre le poste et le département de l'employé.
     * </p>
     *
     * @param body      map JSON contenant les données de l'évaluation
     * @param chefId    identifiant de l'évaluateur (chef)
     * @param employeId identifiant de l'employé évalué
     * @param titre     titre de l'évaluation (peut être surchargé par la campagne)
     * @return l'entité {@link PerformanceEval} prête à être persistée
     */
    private PerformanceEval buildEval(Map<String, Object> body, Long chefId, Long employeId, String titre) {
        PerformanceEval eval = new PerformanceEval();
        eval.setEmployeeId(employeId);
        eval.setEvaluatorId(chefId);

        // If campagneId is provided, pull period/year/title from the campaign
        Long campagneId = toLong(body.get("campagneId"));
        String periode = "ANNUEL";
        int annee = LocalDate.now().getYear();
        String resolvedTitre = titre;
        if (campagneId != null) {
            var campagne = campagneRepo.findById(campagneId).orElse(null);
            if (campagne != null) {
                if (campagne.getPeriode() != null) periode = campagne.getPeriode();
                if (campagne.getAnnee()   != null) annee   = campagne.getAnnee();
                if (campagne.getTitre()   != null) resolvedTitre = campagne.getTitre();
            }
        } else {
            if (body.get("annee") instanceof Number n) annee = n.intValue();
            periode = String.valueOf(body.getOrDefault("periode", "ANNUEL"));
        }

        eval.setPeriodYear(annee);
        eval.setPeriodQuarter(periode);
        eval.setStatus("EN_ATTENTE");
        // Look up employee's real job title and department from Oracle
        String resolvedPoste = String.valueOf(body.getOrDefault("employePoste", body.getOrDefault("poste", "")));
        String resolvedDept  = String.valueOf(body.getOrDefault("departement", ""));
        if (employeId != null) {
            try {
                Map<String, Object> row = jdbc.queryForMap(
                    "SELECT e.JOB_TITLE, d.NOM AS DEPT_NOM " +
                    "FROM GERAI.EMPLOYEES e " +
                    "LEFT JOIN GERAI.ADMIN_DEPARTEMENTS d ON e.DEPT_ID = d.DEPT_ADMIN_ID " +
                    "WHERE e.EMPLOYEE_ID = ?", employeId);
                Object jt = row.get("JOB_TITLE");
                Object dn = row.get("DEPT_NOM");
                if (jt != null && !jt.toString().isBlank()) resolvedPoste = jt.toString();
                if (dn != null && !dn.toString().isBlank()) resolvedDept  = dn.toString();
            } catch (Exception ignored) {}
        }
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("employeNom",    body.getOrDefault("employeNom",    body.getOrDefault("nom",    "")));
            meta.put("employePrenom", body.getOrDefault("employePrenom", body.getOrDefault("prenom", "")));
            meta.put("employePoste",  resolvedPoste);
            meta.put("departement",   resolvedDept);
            meta.put("campagneTitre", resolvedTitre);
            if (campagneId != null) meta.put("campagneId", campagneId);
            eval.setCriteriaScores(MAPPER.writeValueAsString(meta));
        } catch (Exception ignored) {}
        return eval;
    }

    /**
     * Sérialise une évaluation en map JSON pour la réponse REST.
     * <p>
     * Enrichit la map avec les informations de l'employé (nom, poste, département)
     * stockées en JSON dans {@code criteriaScores}, avec repli sur un lookup Oracle
     * si les valeurs sont vides.
     * </p>
     *
     * @param e l'entité évaluation à sérialiser
     * @return map de clés/valeurs représentant l'évaluation
     */
    private Map<String, Object> toMap(PerformanceEval e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",         e.getEvalId());
        m.put("statut",     e.getStatus() != null ? e.getStatus() : "EN_ATTENTE");
        m.put("scoreFinal", e.getScore() != null ? Math.round(e.getScore() * 10.0) / 10.0 : null);
        m.put("mention",    scoreMention(e.getScore()));
        m.put("employeId",  e.getEmployeeId());
        m.put("chefId",     e.getEvaluatorId());
        m.put("annee",      e.getPeriodYear());
        m.put("periode",    e.getPeriodQuarter() != null ? e.getPeriodQuarter() : "ANNUEL");
        m.put("tauxPresence",        100);
        m.put("formationsRealisees", 0);

        // Employee display info + auto-eval + chef-eval stored as JSON in criteriaScores
        String meta = e.getCriteriaScores();
        String storedPoste = "";
        String storedDept  = "";
        if (meta != null && meta.startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> md = MAPPER.readValue(meta, Map.class);
                m.put("employeNom",    md.getOrDefault("employeNom",    ""));
                m.put("employePrenom", md.getOrDefault("employePrenom", ""));
                storedPoste = String.valueOf(md.getOrDefault("employePoste", ""));
                storedDept  = String.valueOf(md.getOrDefault("departement",  ""));
                m.put("campagneTitre", md.getOrDefault("campagneTitre", "Évaluation " + e.getPeriodYear()));
                m.put("campagneId",    md.get("campagneId"));
                // Return auto-eval and chef-eval if stored
                if (md.containsKey("autoEval")) {
                    m.put("autoEval", md.get("autoEval"));
                    // Override formationsRealisees with the employee's self-reported value
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> ae = (Map<String, Object>) md.get("autoEval");
                        Object fc = ae.get("formationsCompletees");
                        if (fc instanceof Number fn) m.put("formationsRealisees", fn.intValue());
                    } catch (Exception ignored) {}
                }
                if (md.containsKey("evalChef")) {
                    m.put("evalChef", md.get("evalChef"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ec = (Map<String, Object>) md.get("evalChef");
                    m.put("recommandation", ec.getOrDefault("recommandation", e.getImprovements()));
                } else {
                    m.put("recommandation", e.getImprovements());
                }
                // Return breakdown scores — recalculate scoreFormations live from stored nForms
                if (md.containsKey("scoreObjectifs"))   m.put("scoreObjectifs",   md.get("scoreObjectifs"));
                if (md.containsKey("scoreCompetences")) m.put("scoreCompetences", md.get("scoreCompetences"));
                if (md.containsKey("scorePresence"))    m.put("scorePresence",    md.get("scorePresence"));
                int nFormsResp = 0;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ae2 = (Map<String, Object>) md.get("autoEval");
                    if (ae2 != null) {
                        Object fc2 = ae2.get("formationsCompletees");
                        if (fc2 instanceof Number fn2) nFormsResp = fn2.intValue();
                    }
                } catch (Exception ignored) {}
                int recalcFormations = nFormsResp >= 3 ? 20 : nFormsResp == 2 ? 13 : nFormsResp == 1 ? 7 : 0;
                m.put("scoreFormations", recalcFormations);
                // Recompute scoreFinal with the updated formations score
                if (md.containsKey("scoreFinal")) {
                    int storedObj  = md.containsKey("scoreObjectifs")   ? ((Number) md.get("scoreObjectifs")).intValue()   : 0;
                    int storedComp = md.containsKey("scoreCompetences") ? ((Number) md.get("scoreCompetences")).intValue() : 0;
                    int storedPres = md.containsKey("scorePresence")    ? ((Number) md.get("scorePresence")).intValue()    : 10;
                    m.put("scoreFinal", storedObj + storedComp + recalcFormations + storedPres);
                }
                // Return reason stored by renvoyerAuChef
                if (md.containsKey("commentaireRetour")) m.put("commentaireRetour", md.get("commentaireRetour"));
                if (md.containsKey("renvoyePar"))        m.put("renvoyePar",        md.get("renvoyePar"));
            } catch (Exception ignored) {
                m.put("employeNom", ""); m.put("employePrenom", "");
                m.put("departement", ""); m.put("campagneTitre", "");
                m.put("recommandation", e.getImprovements());
            }
        } else {
            m.put("employeNom",    ""); m.put("employePrenom", "");
            m.put("campagneTitre", "Évaluation " + e.getPeriodYear());
            m.put("recommandation", e.getImprovements());
        }
        // Fall back to live Oracle lookup when stored values are empty
        if ((storedPoste.isBlank() || storedDept.isBlank()) && e.getEmployeeId() != null) {
            try {
                Map<String, Object> row = jdbc.queryForMap(
                    "SELECT e2.JOB_TITLE, d.NOM AS DEPT_NOM " +
                    "FROM GERAI.EMPLOYEES e2 " +
                    "LEFT JOIN GERAI.ADMIN_DEPARTEMENTS d ON e2.DEPT_ID = d.DEPT_ADMIN_ID " +
                    "WHERE e2.EMPLOYEE_ID = ?", e.getEmployeeId());
                if (storedPoste.isBlank()) {
                    Object jt = row.get("JOB_TITLE");
                    storedPoste = (jt != null) ? jt.toString() : "";
                }
                if (storedDept.isBlank()) {
                    Object dn = row.get("DEPT_NOM");
                    storedDept = (dn != null) ? dn.toString() : "";
                }
            } catch (Exception ignored) {}
        }
        m.put("employePoste", storedPoste);
        m.put("departement",  storedDept);
        return m;
    }

    /**
     * Convertit un objet JSON (Number ou String) en {@code Long}.
     *
     * @param v valeur à convertir
     * @return la valeur {@code Long} correspondante, ou {@code null} si nulle ou non convertible
     */
    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception ignored) { return null; }
    }

    /**
     * Détermine la mention qualitative correspondant à un score numérique.
     *
     * @param score score sur 100 (peut être {@code null})
     * @return mention parmi {@code EXCELLENT}, {@code BIEN}, {@code SATISFAISANT},
     *         {@code A_AMELIORER}, {@code INSUFFISANT}
     */
    private String scoreMention(Double score) {
        if (score == null) return "A_AMELIORER";
        if (score >= 85) return "EXCELLENT";
        if (score >= 70) return "BIEN";
        if (score >= 55) return "SATISFAISANT";
        if (score >= 40) return "A_AMELIORER";
        return "INSUFFISANT";
    }
}
