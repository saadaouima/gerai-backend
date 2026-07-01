package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.model.KillerQuestion;
import com.gerai.projetsservice.model.ScreeningQuestion;
import com.gerai.projetsservice.repository.KillerQuestionRepository;
import com.gerai.projetsservice.repository.ScreeningQuestionRepository;
import com.gerai.projetsservice.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion des questions de présélection (screening et killer questions).
 * <p>
 * Expose deux groupes d'endpoints :
 * <ul>
 *   <li><strong>Public</strong> ({@code /api/public/jobs/{jobId}/...}) — consultation sans authentification
 *       pour que les candidats répondent aux questions avant de postuler.</li>
 *   <li><strong>Admin</strong> ({@code /api/admin/jobs/{jobId}/...}) — CRUD complet avec réponses attendues
 *       pour la configuration par les recruteurs.</li>
 * </ul>
 * </p>
 * <p>
 * Les <em>killer questions</em> sont des questions éliminatoires : une réponse incorrecte
 * bloque immédiatement la candidature. Les <em>screening questions</em> sont des questions
 * pondérées qui génèrent un score de présélection.
 * </p>
 * <p>
 * {@code @RestController} : contrôleur REST retournant du JSON.
 * </p>
 *
 * @since 1.0
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class ScreeningController {

    private final KillerQuestionRepository killerRepo;
    private final ScreeningQuestionRepository screeningRepo;
    private final ScreeningService screeningService;

    /* ══════════════════════════════════════════════════════════════════════
       PUBLIC — no auth required
       ══════════════════════════════════════════════════════════════════════ */

    /**
     * Retourne les questions de présélection visibles aux candidats pour une offre donnée.
     * <p>
     * Les réponses attendues/correctes sont exclues de la réponse pour des raisons de sécurité.
     * </p>
     *
     * @param jobId identifiant de l'offre d'emploi
     * @return map avec {@code killerQuestions} et {@code screeningQuestions} (sans réponses) (HTTP 200)
     */
    @GetMapping("/api/public/jobs/{jobId}/screening")
    public ResponseEntity<Map<String, Object>> getPublicScreening(@PathVariable Long jobId) {
        List<Map<String, Object>> killers = killerRepo
                .findByJobIdOrderByDisplayOrderAsc(jobId)
                .stream()
                .map(kq -> Map.<String, Object>of(
                        "id",           kq.getId(),
                        "question",     kq.getQuestion(),
                        "displayOrder", kq.getDisplayOrder() != null ? kq.getDisplayOrder() : 0
                )).toList();

        List<Map<String, Object>> screening = screeningRepo
                .findByJobIdOrderByDisplayOrderAsc(jobId)
                .stream()
                .map(sq -> Map.<String, Object>of(
                        "id",           sq.getId(),
                        "question",     sq.getQuestion(),
                        "type",         sq.getType(),
                        "options",      sq.getOptions() != null ? sq.getOptions() : List.of(),
                        "weight",       sq.getWeight(),
                        "displayOrder", sq.getDisplayOrder() != null ? sq.getDisplayOrder() : 0
                )).toList();

        return ResponseEntity.ok(Map.of(
                "killerQuestions",    killers,
                "screeningQuestions", screening
        ));
    }

    /**
     * Valide les réponses aux killer questions avant l'affichage du formulaire de candidature.
     * <p>
     * Retourne {@code {"passed": true}} si toutes les conditions éliminatoires sont satisfaites,
     * ou {@code {"passed": false, "failedQuestion": "..."}} si l'une d'elles ne l'est pas.
     * </p>
     *
     * @param jobId   identifiant de l'offre d'emploi
     * @param answers map des réponses indexées par identifiant de question
     * @return résultat de validation avec indicateur {@code passed} (HTTP 200)
     */
    @PostMapping("/api/public/jobs/{jobId}/validate-killer")
    public ResponseEntity<Map<String, Object>> validateKiller(
            @PathVariable Long jobId,
            @RequestBody Map<Long, String> answers) {
        String failed = screeningService.validateKillers(jobId, answers);
        if (failed != null) {
            return ResponseEntity.ok(Map.of("passed", false, "failedQuestion", failed));
        }
        return ResponseEntity.ok(Map.of("passed", true));
    }

    /* ══════════════════════════════════════════════════════════════════════
       ADMIN — full data including correct answers
       ══════════════════════════════════════════════════════════════════════ */

    /**
     * Retourne les questions de présélection complètes (avec réponses attendues) pour l'administration.
     *
     * @param jobId identifiant de l'offre d'emploi
     * @return map avec {@code killerQuestions} et {@code screeningQuestions} complets (HTTP 200)
     */
    @GetMapping("/api/admin/jobs/{jobId}/screening")
    public ResponseEntity<Map<String, Object>> getAdminScreening(@PathVariable Long jobId) {
        return ResponseEntity.ok(Map.of(
                "killerQuestions",    killerRepo.findByJobIdOrderByDisplayOrderAsc(jobId),
                "screeningQuestions", screeningRepo.findByJobIdOrderByDisplayOrderAsc(jobId)
        ));
    }

    /* ── Killer questions CRUD ───────────────────────────────────────────── */

    /**
     * Ajoute une killer question à une offre d'emploi.
     *
     * @param jobId identifiant de l'offre d'emploi
     * @param body  données de la killer question (texte, réponse attendue, ordre...)
     * @return la killer question créée (HTTP 200)
     */
    @PostMapping("/api/admin/jobs/{jobId}/killer-questions")
    public ResponseEntity<KillerQuestion> addKiller(
            @PathVariable Long jobId,
            @RequestBody KillerQuestion body) {
        body.setId(null);
        body.setJobId(jobId);
        if (body.getDisplayOrder() == null) {
            body.setDisplayOrder(killerRepo.findByJobIdOrderByDisplayOrderAsc(jobId).size() + 1);
        }
        return ResponseEntity.ok(killerRepo.save(body));
    }

    /**
     * Met à jour une killer question existante.
     *
     * @param jobId identifiant de l'offre d'emploi
     * @param id    identifiant de la killer question
     * @param body  nouvelles données (texte, réponse attendue, ordre)
     * @return la killer question mise à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/api/admin/jobs/{jobId}/killer-questions/{id}")
    public ResponseEntity<KillerQuestion> updateKiller(
            @PathVariable Long jobId, @PathVariable Long id,
            @RequestBody KillerQuestion body) {
        return killerRepo.findById(id).map(kq -> {
            kq.setQuestion(body.getQuestion());
            kq.setExpectedAnswer(body.getExpectedAnswer());
            if (body.getDisplayOrder() != null) kq.setDisplayOrder(body.getDisplayOrder());
            return ResponseEntity.ok(killerRepo.save(kq));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime une killer question.
     *
     * @param jobId identifiant de l'offre d'emploi (non utilisé pour la suppression)
     * @param id    identifiant de la killer question à supprimer
     * @return HTTP 204 après suppression
     */
    @DeleteMapping("/api/admin/jobs/{jobId}/killer-questions/{id}")
    public ResponseEntity<Void> deleteKiller(@PathVariable Long jobId, @PathVariable Long id) {
        killerRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ── Screening questions CRUD ────────────────────────────────────────── */

    /**
     * Ajoute une question de screening à une offre d'emploi.
     *
     * @param jobId identifiant de l'offre d'emploi
     * @param body  données de la question (texte, type, options, réponse correcte, poids, ordre)
     * @return la question de screening créée (HTTP 200)
     */
    @PostMapping("/api/admin/jobs/{jobId}/screening-questions")
    public ResponseEntity<ScreeningQuestion> addScreening(
            @PathVariable Long jobId,
            @RequestBody ScreeningQuestion body) {
        body.setId(null);
        body.setJobId(jobId);
        if (body.getWeight() == null) body.setWeight(5);
        if (body.getDisplayOrder() == null) {
            body.setDisplayOrder(screeningRepo.findByJobIdOrderByDisplayOrderAsc(jobId).size() + 1);
        }
        return ResponseEntity.ok(screeningRepo.save(body));
    }

    /**
     * Met à jour une question de screening existante.
     *
     * @param jobId identifiant de l'offre d'emploi
     * @param id    identifiant de la question de screening
     * @param body  nouvelles données (texte, type, options, réponse correcte, poids, ordre)
     * @return la question mise à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/api/admin/jobs/{jobId}/screening-questions/{id}")
    public ResponseEntity<ScreeningQuestion> updateScreening(
            @PathVariable Long jobId, @PathVariable Long id,
            @RequestBody ScreeningQuestion body) {
        return screeningRepo.findById(id).map(sq -> {
            sq.setQuestion(body.getQuestion());
            sq.setType(body.getType());
            sq.setOptions(body.getOptions());
            sq.setCorrectAnswer(body.getCorrectAnswer());
            if (body.getWeight() != null) sq.setWeight(body.getWeight());
            if (body.getDisplayOrder() != null) sq.setDisplayOrder(body.getDisplayOrder());
            return ResponseEntity.ok(screeningRepo.save(sq));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime une question de screening.
     *
     * @param jobId identifiant de l'offre d'emploi (non utilisé pour la suppression)
     * @param id    identifiant de la question à supprimer
     * @return HTTP 204 après suppression
     */
    @DeleteMapping("/api/admin/jobs/{jobId}/screening-questions/{id}")
    public ResponseEntity<Void> deleteScreening(@PathVariable Long jobId, @PathVariable Long id) {
        screeningRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
