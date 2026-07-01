package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.model.Interview;
import com.gerai.projetsservice.repository.CandidateRepository;
import com.gerai.projetsservice.repository.InterviewRepository;
import com.gerai.projetsservice.service.CvScoringService;
import com.gerai.projetsservice.service.RecruitmentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion des entretiens de recrutement.
 * <p>
 * Expose les endpoints {@code /api/admin/interviews} permettant :
 * <ul>
 *   <li>La consultation de tous les entretiens ou par candidat.</li>
 *   <li>La planification d'un entretien avec notification automatique au candidat.</li>
 *   <li>L'envoi d'une convocation d'entretien par email SMTP.</li>
 *   <li>L'enregistrement de la décision finale (RETENU/REJETE) avec email d'offre automatique.</li>
 *   <li>La mise à jour du statut d'un entretien.</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/interviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class AdminInterviewController {

    private final InterviewRepository            repo;
    private final CandidateRepository            candidateRepo;
    private final JavaMailSender                 mailSender;
    private final CvScoringService               scoringService;
    private final RecruitmentNotificationService recruitmentNotif;

    @Value("${app.mail.from:noreply@synapse.ma}")
    private String mailFrom;

    /**
     * Retourne la liste de tous les entretiens planifiés.
     *
     * @return liste complète des entretiens (HTTP 200)
     */
    @GetMapping
    public ResponseEntity<List<Interview>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * Retourne les entretiens associés à un candidat spécifique.
     *
     * @param candidatId identifiant du candidat
     * @return liste des entretiens du candidat (HTTP 200)
     */
    @GetMapping("/by-candidate/{candidatId}")
    public ResponseEntity<List<Interview>> getByCandidateId(@PathVariable Long candidatId) {
        return ResponseEntity.ok(repo.findByCandidatId(candidatId));
    }

    /**
     * Crée un entretien, met le candidat en statut {@code INTERVIEWE}
     * et envoie une notification Kafka.
     *
     * @param body données de l'entretien (l'identifiant est ignoré)
     * @return l'entretien créé (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<Interview> create(@RequestBody Interview body) {
        body.setId(null);
        if (body.getStatut()   == null) body.setStatut("PLANIFIE");
        if (body.getDecision() == null) body.setDecision("EN_ATTENTE");
        Interview saved = repo.save(body);
        if (saved.getCandidatId() != null) {
            candidateRepo.findById(saved.getCandidatId()).ifPresent(c -> {
                c.setStatut("INTERVIEWE");
                candidateRepo.save(c);
            });
        }
        recruitmentNotif.notifierEntretienPlanifie(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Envoie une convocation d'entretien par email MIME au candidat associé.
     * <p>
     * L'email contient les détails de l'entretien (date, heure, type, lieu ou lien visio,
     * intervieweur) et est formaté de manière professionnelle.
     * </p>
     *
     * @param id identifiant de l'entretien
     * @return map {@code {sent, to}} (HTTP 200), ou HTTP 400 si le candidat est absent/sans email,
     *         HTTP 404 si l'entretien est introuvable
     */
    @PostMapping("/{id}/send-invitation")
    public ResponseEntity<Map<String, Object>> sendInvitation(@PathVariable Long id) {
        return repo.findById(id).map(interview -> {
            if (interview.getCandidatId() == null) {
                return ResponseEntity.badRequest()
                    .<Map<String, Object>>body(Map.of("error", "Aucun candidat associé à cet entretien."));
            }
            var candidateOpt = candidateRepo.findById(interview.getCandidatId());
            if (candidateOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                    .<Map<String, Object>>body(Map.of("error", "Candidat introuvable."));
            }
            var candidate = candidateOpt.get();
            String email = candidate.getEmail();
            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest()
                    .<Map<String, Object>>body(Map.of("error", "Le candidat n'a pas d'adresse email."));
            }

            String typeLabel = switch (interview.getType()) {
                case "TELEPHONIQUE" -> "Téléphonique";
                case "VISIO"        -> "Visioconférence";
                case "PRESENTIEL"   -> "Présentiel";
                case "TECHNIQUE"    -> "Technique";
                case "RH"           -> "Ressources humaines";
                default             -> interview.getType();
            };

            String dateFormatted = interview.getDate();
            try {
                LocalDate parsed = LocalDate.parse(interview.getDate());
                dateFormatted = parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (Exception ignored) {}

            String locationLine;
            if (interview.getLienVisio() != null && !interview.getLienVisio().isBlank()) {
                locationLine = "  Lien    : " + interview.getLienVisio();
            } else if (interview.getLieu() != null && !interview.getLieu().isBlank()) {
                locationLine = "  Lieu    : " + interview.getLieu();
            } else {
                locationLine = "";
            }

            String body = String.format(
                "Madame/Monsieur %s %s,%n%n" +
                "Suite à l'étude de votre candidature pour le poste de %s au sein du département %s, " +
                "nous avons le plaisir de vous convier à un entretien.%n%n" +
                "Détails de l'entretien :%n" +
                "  Date     : %s%n" +
                "  Horaire  : %s – %s%n" +
                "  Type     : %s%n" +
                "%s%n" +
                "  Avec     : %s%n%n" +
                "Merci de confirmer votre disponibilité en répondant à cet email. " +
                "En cas d'empêchement, n'hésitez pas à nous contacter au plus tôt.%n%n" +
                "Nous vous souhaitons bonne préparation et restons disponibles pour toute question.%n%n" +
                "Cordialement,%n" +
                "L'Équipe RH Arabsoft",
                candidate.getPrenom(),
                candidate.getNom(),
                interview.getJobTitre(),
                interview.getDepartement(),
                dateFormatted,
                interview.getHeureDebut(),
                interview.getHeureFin(),
                typeLabel,
                locationLine,
                interview.getIntervieweur()
            );

            String subject = "Convocation à un entretien — " + interview.getJobTitre() + " | Arabsoft";

            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(mailFrom);
                helper.setTo(email);
                helper.setSubject(subject);
                helper.setText(body, false);
                mailSender.send(message);
                return ResponseEntity.ok(Map.<String, Object>of("sent", true, "to", email));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.<String, Object>of(
                    "sent", false,
                    "to", email,
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
                ));
            }
        }).orElse(ResponseEntity.notFound().<Map<String, Object>>build());
    }

    /**
     * Enregistre la décision finale d'un entretien (RETENU ou REJETE).
     * <p>
     * Si la décision est RETENU, met à jour le statut du candidat en {@code OFFRE_ENVOYEE}
     * et envoie automatiquement un email d'offre d'emploi généré par IA.
     * </p>
     *
     * @param id   identifiant de l'entretien
     * @param body map JSON avec {@code decision}, {@code commentaire}, {@code noteGlobale},
     *             {@code salaire} et {@code dateDebut} (optionnels)
     * @return l'entretien mis à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}/decision")
    public ResponseEntity<Interview> saveDecision(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        return repo.findById(id).map(i -> {
            if (body.containsKey("decision"))    i.setDecision((String) body.get("decision"));
            if (body.containsKey("commentaire")) i.setCommentaire((String) body.get("commentaire"));
            if (body.containsKey("noteGlobale")) {
                Object val = body.get("noteGlobale");
                if (val instanceof Integer) i.setNoteGlobale((Integer) val);
                if (val instanceof Number)  i.setNoteGlobale(((Number) val).intValue());
            }
            boolean finalDecision = "RETENU".equals(i.getDecision()) || "REJETE".equals(i.getDecision());
            if (finalDecision) {
                i.setStatut("TERMINE");
                if (i.getCandidatId() != null) {
                    String salaire   = body.containsKey("salaire")   ? (String) body.get("salaire")   : null;
                    String dateDebut = body.containsKey("dateDebut") ? (String) body.get("dateDebut") : null;
                    candidateRepo.findById(i.getCandidatId()).ifPresent(c -> {
                        String newStatut = "RETENU".equals(i.getDecision()) ? "OFFRE_ENVOYEE" : "REJETE";
                        c.setStatut(newStatut);
                        candidateRepo.save(c);
                        if ("RETENU".equals(i.getDecision()) && c.getEmail() != null && !c.getEmail().isBlank()) {
                            try {
                                java.util.Map<String, String> draft = scoringService.generateEmailDraft(c, "OFFRE", salaire, dateDebut);
                                MimeMessage message = mailSender.createMimeMessage();
                                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                                helper.setFrom(mailFrom);
                                helper.setTo(c.getEmail());
                                helper.setSubject(draft.get("objet"));
                                helper.setText(draft.get("corps"), false);
                                mailSender.send(message);
                                log.info("[saveDecision] Offer email sent to {} for candidate {}", c.getEmail(), c.getId());
                            } catch (Exception e) {
                                log.warn("[saveDecision] Could not send offer email to candidate {}: {}", c.getId(), e.getMessage());
                            }
                        }
                    });
                }
            }
            Interview saved = repo.save(i);
            if (finalDecision) recruitmentNotif.notifierDecisionEntretien(saved);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Met à jour le statut administratif d'un entretien (PLANIFIE, EN_COURS, TERMINE...).
     *
     * @param id   identifiant de l'entretien
     * @param body map JSON avec la clé {@code statut}
     * @return l'entretien mis à jour (HTTP 200), HTTP 404 si introuvable
     */
    @PutMapping("/{id}/statut")
    public ResponseEntity<Interview> updateStatut(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body) {
        return repo.findById(id).map(i -> {
            i.setStatut(body.get("statut"));
            return ResponseEntity.ok(repo.save(i));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Supprime un entretien.
     *
     * @param id identifiant de l'entretien à supprimer
     * @return HTTP 204 si supprimé, HTTP 404 si introuvable
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
