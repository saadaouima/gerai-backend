package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.dto.CvScoreResponse;
import com.gerai.projetsservice.model.Candidate;
import com.gerai.projetsservice.repository.CandidateRepository;
import com.gerai.projetsservice.repository.JobRepository;
import com.gerai.projetsservice.service.CvScoringService;
import com.gerai.projetsservice.service.RecruitmentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contrôleur REST pour la gestion des candidats côté administration RH.
 * <p>
 * Expose les endpoints {@code /api/admin/candidates} permettant :
 * <ul>
 *   <li>La consultation et le filtrage des candidats (tous, shortlist).</li>
 *   <li>La mise à jour du statut, des notes recruteur et du flag shortlist.</li>
 *   <li>Le scoring IA individuel ou en lot via Groq LLM (llama-3.3-70b-versatile).</li>
 *   <li>La génération automatique de questions d'entretien par IA.</li>
 *   <li>La génération et l'envoi d'emails de rejet ou d'offre d'emploi.</li>
 * </ul>
 * </p>
 * <p>
 * {@code @RestController} : contrôleur REST retournant du JSON.<br>
 * {@code @RequestMapping} : préfixe de chemin commun.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/candidates")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AdminCandidateController {

    private final CandidateRepository             repo;
    private final JobRepository                   jobRepo;
    private final CvScoringService                scoringService;
    private final JavaMailSender                  mailSender;
    private final RecruitmentNotificationService  recruitmentNotif;

    @Value("${app.mail.from:noreply@synapse.ma}")
    private String mailFrom;

    /**
     * Retourne la liste de tous les candidats.
     *
     * @return liste complète des candidats (HTTP 200)
     */
    @GetMapping
    public ResponseEntity<List<Candidate>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * Retourne uniquement les candidats marqués en shortlist.
     *
     * @return liste des candidats shortlistés (HTTP 200)
     */
    @GetMapping("/shortlist")
    public ResponseEntity<List<Candidate>> getShortlist() {
        return ResponseEntity.ok(repo.findByShortlisteTrue());
    }

    /**
     * Met à jour le statut d'un candidat et envoie une notification si le statut change.
     *
     * @param id   identifiant du candidat
     * @param body map JSON contenant la clé {@code statut} avec la nouvelle valeur
     * @return le candidat mis à jour (HTTP 200), ou HTTP 404 si introuvable
     */
    @PutMapping("/{id}/statut")
    public ResponseEntity<Candidate> updateStatut(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body) {
        return repo.findById(id).map(c -> {
            String ancienStatut = c.getStatut();
            c.setStatut(body.get("statut"));
            Candidate saved = repo.save(c);
            if (!Objects.equals(ancienStatut, saved.getStatut())) {
                recruitmentNotif.notifierStatutCandidat(saved, ancienStatut);
            }
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Enregistre la note textuelle du recruteur sur un candidat.
     *
     * @param id   identifiant du candidat
     * @param body map JSON contenant la clé {@code noteRecruteur}
     * @return le candidat mis à jour (HTTP 200), ou HTTP 404 si introuvable
     */
    @PutMapping("/{id}/note")
    public ResponseEntity<Candidate> updateNote(@PathVariable Long id,
                                                @RequestBody Map<String, String> body) {
        return repo.findById(id).map(c -> {
            c.setNoteRecruteur(body.get("noteRecruteur"));
            return ResponseEntity.ok(repo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Met à jour le flag shortlist d'un candidat.
     *
     * @param id   identifiant du candidat
     * @param body map JSON contenant la clé {@code shortliste} (booléen ou chaîne)
     * @return le candidat mis à jour (HTTP 200), ou HTTP 404 si introuvable
     */
    @PutMapping("/{id}/shortlist")
    public ResponseEntity<Candidate> updateShortlist(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        return repo.findById(id).map(c -> {
            Object val = body.get("shortliste");
            if (val instanceof Boolean)  c.setShortliste((Boolean) val);
            if (val instanceof String)   c.setShortliste(Boolean.parseBoolean((String) val));
            return ResponseEntity.ok(repo.save(c));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lance le scoring IA (Groq LLM) pour un candidat individuel et sauvegarde le score.
     *
     * @param id identifiant du candidat
     * @return {@link CvScoreResponse} avec score et analyse (HTTP 200),
     *         HTTP 404 si introuvable, HTTP 500 si l'appel IA échoue
     */
    @PostMapping("/{id}/ai-score")
    public ResponseEntity<?> aiScore(@PathVariable Long id) {
        Candidate c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        try {
            String jobDescription = (c.getJobId() != null)
                ? jobRepo.findById(c.getJobId()).map(j -> j.getDescription()).orElse(null)
                : null;
            CvScoreResponse result = scoringService.score(c, jobDescription);
            if (result.getScore() != null && result.getScore() >= 0) {
                c.setScore(result.getScore());
                repo.save(c);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AI Score] Failed for candidate {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Exécute le scoring IA en lot sur tous les candidats sans score.
     * <p>
     * Les candidats avec un score ≥ 70 sont automatiquement promus en statut
     * {@code SHORTLISTE}, les autres passent en {@code EN_REVUE}.
     * </p>
     *
     * @return statistiques du traitement : scored, promoted, flagged, errors, total, updatedIds (HTTP 200)
     */
    @PostMapping("/ai-score-batch")
    public ResponseEntity<?> batchScore() {
        List<Candidate> unscored = repo.findAll().stream()
            .filter(c -> c.getScore() == null).toList();
        int scored = 0, errors = 0;
        List<Long> updatedIds = new ArrayList<>();
        int promoted = 0, flagged = 0;
        for (Candidate c : unscored) {
            try {
                String jobDesc = (c.getJobId() != null)
                    ? jobRepo.findById(c.getJobId()).map(j -> j.getDescription()).orElse(null)
                    : null;
                CvScoreResponse result = scoringService.score(c, jobDesc);
                if (result.getScore() == null || result.getScore() < 0) {
                    log.warn("[BatchScore] Candidate {} returned NON_SCORE: {}", c.getId(), result.getRecommandation());
                    errors++;
                    continue;
                }
                c.setScore(result.getScore());
                if ("NOUVEAU".equals(c.getStatut())) {
                    if (result.getScore() >= 70) { c.setStatut("SHORTLISTE"); promoted++; }
                    else                          { c.setStatut("EN_REVUE");  flagged++;  }
                }
                repo.save(c);
                updatedIds.add(c.getId());
                scored++;
            } catch (Exception e) {
                log.warn("[BatchScore] Candidate {} failed: {}", c.getId(), e.getMessage());
                errors++;
            }
        }
        return ResponseEntity.ok(Map.of(
            "scored", scored, "promoted", promoted, "flagged", flagged,
            "errors", errors, "total", unscored.size(), "updatedIds", updatedIds
        ));
    }

    /**
     * Shortliste automatiquement les candidats scorés selon un seuil configurable.
     *
     * @param threshold seuil de score (0-100) pour la promotion en shortlist (défaut : 70)
     * @return statistiques : promoted, flagged, threshold (HTTP 200)
     */
    @PostMapping("/auto-shortlist")
    public ResponseEntity<?> autoShortlist(@RequestParam(defaultValue = "70") int threshold) {
        List<Candidate> scored = repo.findAll().stream()
            .filter(c -> c.getScore() != null && c.getScore() >= 0
                      && ("NOUVEAU".equals(c.getStatut()) || "EN_REVUE".equals(c.getStatut())))
            .toList();
        int promoted = 0, flagged = 0;
        for (Candidate c : scored) {
            if (c.getScore() >= threshold) { c.setStatut("SHORTLISTE"); promoted++; }
            else                           { c.setStatut("EN_REVUE");   flagged++;  }
            repo.save(c);
        }
        return ResponseEntity.ok(Map.of("promoted", promoted, "flagged", flagged, "threshold", threshold));
    }

    /**
     * Génère par IA 8 questions d'entretien ciblées pour un candidat donné.
     * <p>
     * Les questions couvrent 4 catégories : Technique, Comportementale, Situationnelle, Motivation.
     * </p>
     *
     * @param id identifiant du candidat
     * @return map {@code questions} contenant la liste des questions par catégorie (HTTP 200),
     *         HTTP 404 si introuvable, HTTP 500 si l'appel IA échoue
     */
    @PostMapping("/{id}/interview-questions")
    public ResponseEntity<?> interviewQuestions(@PathVariable Long id) {
        Candidate c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        try {
            String jobDesc = (c.getJobId() != null)
                ? jobRepo.findById(c.getJobId()).map(j -> j.getDescription()).orElse(null)
                : null;
            List<Map<String, String>> questions = scoringService.generateInterviewQuestions(c, jobDesc);
            return ResponseEntity.ok(Map.of("questions", questions));
        } catch (Exception e) {
            log.error("[AI Questions] Failed for candidate {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Génère par IA un brouillon d'email de rejet ou d'offre d'emploi pour un candidat.
     *
     * @param id   identifiant du candidat
     * @param body map JSON optionnelle avec {@code type} (REJET|OFFRE), {@code salaire}, {@code dateDebut}
     * @return map {@code {objet, corps}} (HTTP 200), HTTP 404 si introuvable, HTTP 500 si IA échoue
     */
    @PostMapping("/{id}/email-draft")
    public ResponseEntity<?> emailDraft(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Candidate c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        try {
            String type      = body.getOrDefault("type", "REJET");
            String salaire   = body.get("salaire");
            String dateDebut = body.get("dateDebut");
            Map<String, String> draft = scoringService.generateEmailDraft(c, type, salaire, dateDebut);
            return ResponseEntity.ok(draft);
        } catch (Exception e) {
            log.error("[AI Email] Failed for candidate {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Envoie un email SMTP au candidat (rejet ou offre d'emploi) et met à jour son statut.
     * <p>
     * Si {@code objet} et {@code corps} sont absents du body, l'email est régénéré par IA.
     * </p>
     *
     * @param id   identifiant du candidat
     * @param body map JSON avec {@code type}, {@code objet}, {@code corps} (optionnels)
     * @return résultat de l'envoi : {@code sent}, {@code to}, {@code statut} (HTTP 200),
     *         HTTP 400 si email absent, HTTP 404 si candidat introuvable, HTTP 500 si échec SMTP
     */
    @PostMapping("/{id}/send-email")
    public ResponseEntity<?> sendEmail(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Candidate c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        if (c.getEmail() == null || c.getEmail().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Le candidat n'a pas d'adresse email."));
        try {
            String type  = body.getOrDefault("type", "REJET");
            String objet = body.get("objet");
            String corps = body.get("corps");

            // Use the draft already displayed to the admin; regenerate only as fallback
            if (objet == null || objet.isBlank() || corps == null || corps.isBlank()) {
                Map<String, String> draft = scoringService.generateEmailDraft(c, type);
                objet = draft.get("objet");
                corps = draft.get("corps");
            }

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(c.getEmail());
            msg.setSubject(objet);
            msg.setText(corps);
            mailSender.send(msg);

            // update status
            if ("OFFRE".equals(type) && !"EMBAUCHE".equals(c.getStatut()))
                c.setStatut("OFFRE_ENVOYEE");
            if ("REJET".equals(type))
                c.setStatut("REJETE");
            repo.save(c);

            log.info("[SendEmail] {} email sent to {} for candidate {}", type, c.getEmail(), id);
            return ResponseEntity.ok(Map.of("sent", true, "to", c.getEmail(), "statut", c.getStatut()));
        } catch (Exception e) {
            log.error("[SendEmail] Failed for candidate {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Supprime un candidat définitivement.
     *
     * @param id identifiant du candidat à supprimer
     * @return HTTP 204 si supprimé, HTTP 404 si introuvable
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
