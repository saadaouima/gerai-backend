package com.gerai.projetsservice.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerai.projetsservice.model.Candidate;
import com.gerai.projetsservice.model.Job;
import com.gerai.projetsservice.repository.CandidateRepository;
import com.gerai.projetsservice.repository.JobRepository;
import com.gerai.projetsservice.service.RecruitmentNotificationService;
import com.gerai.projetsservice.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrôleur REST public pour le portail de candidatures (sans authentification).
 * <p>
 * Expose les endpoints {@code /api/public} accessibles sans token JWT :
 * <ul>
 *   <li>Consultation des offres d'emploi ouvertes ({@code GET /api/public/jobs}).</li>
 *   <li>Soumission d'une candidature avec CV ({@code POST /api/public/apply}).</li>
 *   <li>Téléchargement d'un CV soumis ({@code GET /api/public/cv/{filename}}).</li>
 * </ul>
 * </p>
 * <p>
 * Le débit sur {@code POST /api/public/apply} est limité par {@link com.gerai.projetsservice.config.PublicRateLimitFilter}
 * (10 requêtes/heure par IP). Le service de screening {@link com.gerai.projetsservice.service.ScreeningService}
 * valide les killer questions côté serveur avant d'enregistrer la candidature.
 * </p>
 * <p>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class PublicJobController {

    private final JobRepository                   jobRepo;
    private final CandidateRepository             candidateRepo;
    private final RecruitmentNotificationService  recruitmentNotif;
    private final ScreeningService                screeningService;
    private final ObjectMapper                    objectMapper;

    @Value("${cv.upload-dir:C:/gerai/uploads/cv}")
    private String uploadDir;

    /**
     * Retourne la liste des offres d'emploi avec le statut {@code OUVERT}.
     *
     * @return liste des offres disponibles (HTTP 200)
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<Job>> getOpenJobs() {
        return ResponseEntity.ok(
            jobRepo.findAll().stream()
                .filter(j -> "OUVERT".equals(j.getStatut()))
                .toList()
        );
    }

    /**
     * Soumet une candidature publique avec upload optionnel du CV.
     * <p>
     * Vérifie l'absence de candidature dupliquée (même email + même offre), valide les
     * killer questions côté serveur, calcule un score de screening, puis crée le candidat.
     * Une notification Kafka est envoyée après enregistrement.
     * </p>
     *
     * @param nom             nom du candidat
     * @param prenom          prénom du candidat
     * @param email           adresse email du candidat
     * @param telephone       numéro de téléphone (optionnel)
     * @param jobId           identifiant de l'offre d'emploi ciblée
     * @param experience      années d'expérience (optionnel)
     * @param competences     compétences séparées par virgule (optionnel)
     * @param linkedin        URL du profil LinkedIn (optionnel)
     * @param localisation    ville ou région du candidat (optionnel)
     * @param cv              fichier CV multipart (optionnel)
     * @param killerAnswers   JSON des réponses aux killer questions (format {@code {id: réponse}})
     * @param screeningAnswers JSON des réponses aux questions de screening (format {@code {id: réponse}})
     * @return le candidat créé (HTTP 201), HTTP 400 si offre introuvable,
     *         HTTP 409 si candidature dupliquée, HTTP 422 si killer question non satisfaite
     */
    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> apply(
            @RequestParam String              nom,
            @RequestParam String              prenom,
            @RequestParam String              email,
            @RequestParam(required = false)   String        telephone,
            @RequestParam                     Long          jobId,
            @RequestParam(required = false)   Integer       experience,
            @RequestParam(required = false)   String        competences,
            @RequestParam(required = false)   String        linkedin,
            @RequestParam(required = false)   String        localisation,
            @RequestParam(required = false)   MultipartFile cv,
            @RequestParam(required = false)   String        killerAnswers,
            @RequestParam(required = false)   String        screeningAnswers) {

        Job job = jobRepo.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.badRequest().body("Offre introuvable.");

        // Duplicate check
        boolean duplicate = candidateRepo.findAll().stream()
            .anyMatch(c -> email.equalsIgnoreCase(c.getEmail()) && jobId.equals(c.getJobId()));
        if (duplicate) return ResponseEntity.status(HttpStatus.CONFLICT)
            .body("Vous avez déjà postulé à cette offre.");

        // Killer question validation (server-side safety check)
        Map<Long, String> killerMap = parseAnswers(killerAnswers);
        String failedKiller = screeningService.validateKillers(jobId, killerMap);
        if (failedKiller != null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("Candidature rejetée : critère obligatoire non rempli.");
        }

        // Screening score
        Map<Long, String> screeningMap = parseAnswers(screeningAnswers);
        int screeningScore = screeningService.computeScore(jobId, screeningMap);

        String cvUrl = (cv != null && !cv.isEmpty()) ? saveFile(cv) : null;

        List<String> compList = (competences != null && !competences.isBlank())
            ? Arrays.stream(competences.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
            : List.of();

        Candidate candidate = Candidate.builder()
            .nom(nom).prenom(prenom).email(email).telephone(telephone)
            .jobId(jobId).jobTitre(job.getTitre()).departement(job.getDepartement())
            .statut("NOUVEAU")
            .datePostulation(LocalDate.now())
            .experience(experience != null ? experience : 0)
            .competences(compList)
            .linkedin(linkedin).localisation(localisation)
            .cvUrl(cvUrl).shortliste(false)
            .screeningScore(screeningScore)
            .build();

        Candidate saved = candidateRepo.save(candidate);
        log.info("[Public] Application: {} {} → job '{}' | screeningScore={}",
            prenom, nom, job.getTitre(), screeningScore);
        recruitmentNotif.notifierNouveauCandidat(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Désérialise un JSON de réponses {@code {id: réponse}} en map typée {@code Long → String}.
     *
     * @param json chaîne JSON des réponses (peut être nulle ou vide)
     * @return map des réponses désérialisées, vide si JSON invalide ou absent
     */
    @SuppressWarnings("unchecked")
    private Map<Long, String> parseAnswers(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<Long, String> result = new java.util.HashMap<>();
            raw.forEach((k, v) -> { try { result.put(Long.parseLong(k), v); } catch (NumberFormatException ignored) {} });
            return result;
        } catch (Exception e) {
            log.warn("[Public] Could not parse answers JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Sauvegarde le fichier CV dans le répertoire de téléversement et retourne son URL relative.
     *
     * @param file le fichier multipart à sauvegarder
     * @return URL relative d'accès au CV (ex. {@code /api/public/cv/abc123.pdf}),
     *         ou {@code null} en cas d'erreur d'écriture
     */
    private String saveFile(MultipartFile file) {
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String ext = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains("."))
                ext = original.substring(original.lastIndexOf("."));
            String filename = UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename),
                StandardCopyOption.REPLACE_EXISTING);
            return "/api/public/cv/" + filename;
        } catch (IOException e) {
            log.error("[Public] CV upload failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Sert le fichier CV identifié par son nom depuis le répertoire de téléversement.
     * <p>
     * Effectue une validation du chemin pour prévenir les attaques de traversal de répertoire.
     * </p>
     *
     * @param filename nom du fichier CV à télécharger
     * @return contenu binaire du CV avec le Content-Type approprié (HTTP 200),
     *         HTTP 400 si le chemin est invalide, HTTP 404 si le fichier est introuvable
     */
    @GetMapping("/cv/{filename}")
    public ResponseEntity<byte[]> serveCv(@PathVariable String filename) {
        try {
            Path file = Paths.get(uploadDir).resolve(filename).normalize();
            if (!file.startsWith(Paths.get(uploadDir))) return ResponseEntity.badRequest().build();
            byte[] bytes = Files.readAllBytes(file);
            String ct = filename.endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
            return ResponseEntity.ok().header("Content-Type", ct).body(bytes);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
