package com.gerai.projetsservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerai.projetsservice.dto.CvScoreResponse;
import com.gerai.projetsservice.model.Candidate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service d'intelligence artificielle pour le scoring des CVs et la génération de contenu RH.
 * <p>
 * Utilise l'API Groq (modèle {@code llama-3.3-70b-versatile}) via HTTP REST pour :
 * <ul>
 *   <li>Scorer automatiquement les CV des candidats par rapport à une offre d'emploi (0-100).</li>
 *   <li>Générer des descriptions de postes professionnelles.</li>
 *   <li>Générer des questions d'entretien ciblées par profil.</li>
 *   <li>Générer des brouillons d'emails de rejet ou d'offre d'emploi.</li>
 * </ul>
 * </p>
 * <p>
 * {@code @Service} : composant Spring géré par le conteneur IoC.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Service
@Slf4j
public class CvScoringService {

    /** Clé API Groq (variable d'environnement {@code GROQ_API_KEY}). */
    @Value("${groq.api.key:}")
    private String apiKey;

    /** Identifiant du modèle Groq à utiliser (défaut : {@code llama-3.3-70b-versatile}). */
    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    /** URL de base de l'API Groq OpenAI-compatible. */
    @Value("${groq.api.base-url:https://api.groq.com/openai/v1}")
    private String apiBaseUrl;

    /** Sérialiseur/désérialiseur JSON Jackson. */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Client HTTP Spring WebFlux/RestClient pour les appels Groq. */
    private       RestClient   restClient;

    /**
     * Initialise le client HTTP après injection des dépendances.
     * Journalise un avertissement si la clé Groq n'est pas configurée.
     */
    @PostConstruct
    void init() {
        // Fallback: read GROQ_API_KEY directly from .env if Spring dotenv didn't load it
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = readKeyFromDotenv();
        }
        restClient = RestClient.create();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[CvScoringService] GROQ_API_KEY is not configured — AI features will be unavailable.");
        } else {
            log.info("[CvScoringService] Groq configured: model={}, key={}***", model, apiKey.substring(0, 8));
        }
    }

    /** Reads GROQ_API_KEY from .env candidates when Spring dotenv import misses the file. */
    private String readKeyFromDotenv() {
        String[] candidates = { ".env", "projets-service/.env", "../projets-service/.env" };
        for (String candidate : candidates) {
            try {
                Path p = Path.of(candidate).toAbsolutePath();
                if (Files.exists(p)) {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        if (line.startsWith("GROQ_API_KEY=")) {
                            String key = line.substring("GROQ_API_KEY=".length()).trim();
                            if (!key.isBlank()) {
                                log.info("[CvScoringService] GROQ_API_KEY chargée depuis {}", p);
                                return key;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[CvScoringService] Lecture .env échouée pour {}: {}", candidate, e.getMessage());
            }
        }
        log.error("[CvScoringService] GROQ_API_KEY introuvable dans tous les fichiers .env candidates !");
        return "";
    }

    /**
     * Soumet le profil d'un candidat à l'API Groq et retourne son score IA.
     * <p>
     * En cas d'erreur API ou de score hors plage [0-100], retourne un
     * {@link CvScoreResponse} avec {@code score = -1} et {@code niveau = NON_SCORE}
     * pour ne pas bloquer le pipeline de recrutement.
     * </p>
     *
     * @param candidate      le candidat à évaluer
     * @param jobDescription description textuelle du poste (peut être {@code null})
     * @return le résultat du scoring IA avec score, niveau, compétences et recommandation
     * @throws IllegalStateException si la clé API Groq n'est pas configurée
     */
    public CvScoreResponse score(Candidate candidate, String jobDescription) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[CvScoring] GROQ_API_KEY not configured — returning NON_SCORE for candidate {}", candidate.getId());
            return CvScoreResponse.builder()
                    .score(-1)
                    .niveau("NON_SCORE")
                    .recommandation("GROQ_API_KEY non configurée — scoring indisponible.")
                    .build();
        }

        String prompt = buildPrompt(candidate, jobDescription);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
            Map.of("role", "system",
                   "content", "Tu es un expert en recrutement RH. Réponds UNIQUEMENT en JSON valide, sans markdown, sans explication."),
            Map.of("role", "user", "content", prompt)
        ));
        body.put("temperature", 0.1);
        body.put("max_tokens",  900);

        try {
            String raw = restClient.post()
                .uri(apiBaseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode resp    = objectMapper.readTree(raw);
            String   content = resp.get("choices").get(0).get("message").get("content").asText().trim();

            // Strip markdown code fence if the model wraps the JSON
            if (content.startsWith("```")) {
                content = content.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("(?s)```\\s*$", "").trim();
            }
            // Extract first JSON object in case model added surrounding text
            int s = content.indexOf('{'), e2 = content.lastIndexOf('}');
            if (s >= 0 && e2 > s) content = content.substring(s, e2 + 1);

            log.info("[CvScoring] Groq response for candidate {}: {}", candidate.getId(), content);
            CvScoreResponse result = objectMapper.readValue(content, CvScoreResponse.class);

            // Validate score range [0-100]; signal anomaly with -1 without blocking the pipeline
            if (result.getScore() == null || result.getScore() < 0 || result.getScore() > 100) {
                log.warn("[CvScoring] Score out of range [0-100] for candidate {}: {}. Returning -1.",
                         candidate.getId(), result.getScore());
                return CvScoreResponse.builder()
                        .score(-1)
                        .niveau("NON_SCORE")
                        .recommandation("Score hors plage — vérification manuelle requise.")
                        .build();
            }
            return result;

        } catch (Exception e) {
            log.error("[CvScoring] Groq call failed for candidate {}: {}. Returning fallback score -1.",
                      candidate.getId(), e.getMessage());
            return CvScoreResponse.builder()
                    .score(-1)
                    .niveau("NON_SCORE")
                    .recommandation("Erreur de scoring IA (" + e.getClass().getSimpleName() + ") — vérification manuelle requise.")
                    .build();
        }
    }

    // ── Job Description Generator ─────────────────────────────────────────────

    /**
     * Génère par IA une description professionnelle de poste en français.
     *
     * @param titre       intitulé du poste (ex. "Développeur Java Senior")
     * @param departement département recruteur (ex. "Informatique & Développement")
     * @param role        niveau d'expérience requis (ex. "JUNIOR", "SENIOR")
     * @param typeContrat type de contrat (ex. "CDI", "CDD", "STAGE")
     * @return le texte de description généré (max 300 mots, format texte brut)
     * @throws RuntimeException si l'appel Groq échoue
     */
    public String generateJobDescription(String titre, String departement, String role, String typeContrat) {
        String prompt = """
            Tu es un expert RH. Rédige une description complète et professionnelle pour cette offre d'emploi.

            ## Offre
            - Titre du poste : %s
            - Département    : %s
            - Niveau         : %s
            - Type de contrat: %s

            Structure attendue (texte brut, pas de JSON, pas de balises # markdown) :
            Présentation du poste (2-3 lignes)

            Missions principales :
            • Mission 1
            • Mission 2

            Profil recherché :
            • Compétence/qualité 1
            • Compétence/qualité 2

            Ce que nous offrons :
            • Avantage 1

            Ton professionnel, en français, max 300 mots.
            """.formatted(titre, departement, role, typeContrat);
        return callGroqText(prompt);
    }

    // ── Interview Questions Generator ─────────────────────────────────────────

    /**
     * Génère par IA 8 questions d'entretien ciblées pour un profil de candidat.
     * <p>
     * Produit 2 questions par catégorie : Technique, Comportementale, Situationnelle, Motivation.
     * </p>
     *
     * @param candidate      le candidat pour lequel générer les questions
     * @param jobDescription description du poste (peut être {@code null})
     * @return liste de 8 maps {@code {categorie, question}}
     * @throws RuntimeException si l'appel Groq ou la désérialisation JSON échoue
     */
    public List<Map<String, String>> generateInterviewQuestions(Candidate candidate, String jobDescription) {
        String skills = (candidate.getCompetences() != null && !candidate.getCompetences().isEmpty())
            ? String.join(", ", candidate.getCompetences()) : "non spécifiées";
        int exp = candidate.getExperience() != null ? candidate.getExperience() : 0;
        String jobCtx = (jobDescription != null && !jobDescription.isBlank())
            ? "Description du poste: " + jobDescription
            : "Déduire les exigences du titre et du département.";

        String prompt = """
            Tu es un expert RH. Génère 8 questions d'entretien ciblées pour ce profil.

            ## Profil candidat
            - Poste visé    : %s
            - Département   : %s
            - Expérience    : %d an(s)
            - Compétences   : %s
            - %s

            2 questions par catégorie : Technique, Comportementale, Situationnelle, Motivation.

            Retourne UNIQUEMENT ce JSON (sans markdown) :
            {
              "questions": [
                { "categorie": "<Technique|Comportementale|Situationnelle|Motivation>", "question": "<texte>" }
              ]
            }
            """.formatted(candidate.getJobTitre(), candidate.getDepartement(), exp, skills, jobCtx);

        try {
            String content = callGroqRaw(prompt);
            JsonNode root = objectMapper.readTree(content);
            List<Map<String, String>> result = new ArrayList<>();
            root.get("questions").forEach(q -> {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("categorie", q.path("categorie").asText());
                item.put("question",  q.path("question").asText());
                result.add(item);
            });
            return result;
        } catch (Exception e) {
            log.error("[CvScoring] generateInterviewQuestions failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI interview questions failed: " + e.getMessage(), e);
        }
    }

    // ── Email Draft Generator ─────────────────────────────────────────────────

    /**
     * Génère un brouillon d'email de recrutement (rejet ou offre) sans détails de salaire/date.
     *
     * @param candidate le candidat destinataire
     * @param type      type d'email ({@code REJET} ou {@code OFFRE})
     * @return map {@code {objet, corps}} contenant le brouillon généré par l'IA
     * @throws RuntimeException si l'appel Groq échoue
     */
    public Map<String, String> generateEmailDraft(Candidate candidate, String type) {
        return generateEmailDraft(candidate, type, null, null);
    }

    /**
     * Génère un brouillon d'email de recrutement (rejet ou offre) avec détails optionnels.
     * <p>
     * Pour les offres, intègre le salaire et la date de début dans le corps de l'email
     * si ces informations sont disponibles.
     * </p>
     *
     * @param candidate  le candidat destinataire
     * @param type       type d'email ({@code REJET} ou {@code OFFRE})
     * @param salaire    salaire proposé (optionnel, mentionné dans l'email d'offre)
     * @param dateDebut  date de prise de poste prévue (optionnel)
     * @return map {@code {objet, corps}} contenant le brouillon généré par l'IA
     * @throws RuntimeException si l'appel Groq ou la désérialisation JSON échoue
     */
    public Map<String, String> generateEmailDraft(Candidate candidate, String type,
                                                   String salaire, String dateDebut) {
        String typeLabel = "REJET".equals(type) ? "refus de candidature" : "offre d'emploi";
        String offerDetails = "";
        if ("OFFRE".equals(type) && dateDebut != null && !dateDebut.isBlank()) {
            offerDetails = "\n- Date de début : " + dateDebut;
        }
        String prompt = """
            Tu es le Directeur des Ressources Humaines de la société Arabsoft.
            IMPORTANT : la société s'appelle Arabsoft. N'utilise aucun autre nom d'entreprise.
            Rédige un email professionnel en français pour ce candidat.

            ## Contexte
            - Société   : Arabsoft
            - Candidat  : %s %s
            - Poste     : %s
            - Type email: %s%s

            Pour un refus : empathique, valorise le candidat, laisse la porte ouverte pour de futures opportunités.
            Pour une offre : l'email est une CONFIRMATION POST-APPEL (le candidat a déjà été informé verbalement).
              - Faire référence à l'échange téléphonique ("Suite à notre entretien téléphonique").
              - Mentionner qu'une lettre d'offre formelle (promesse d'embauche) est jointe en pièce jointe.
              - Si salaire ou date de début fournis, les mentionner comme rappel des éléments discutés ("comme convenu lors de notre échange").
              - Ton enthousiaste et professionnel, inviter le candidat à signer et retourner la lettre jointe.
              - NE PAS présenter l'email lui-même comme le document contractuel.

            Retourne UNIQUEMENT ce JSON (sans markdown) :
            {
              "objet": "<sujet de l'email>",
              "corps": "<corps complet avec salutation, contenu, formule de politesse et signature\\nCordialement,\\nL'Équipe RH Arabsoft>"
            }
            """.formatted(candidate.getPrenom(), candidate.getNom(), candidate.getJobTitre(),
                         typeLabel, offerDetails);

        try {
            String content = callGroqRaw(prompt);
            JsonNode root = objectMapper.readTree(content);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("objet", root.path("objet").asText());
            result.put("corps", root.path("corps").asText());
            return result;
        } catch (Exception e) {
            log.error("[CvScoring] generateEmailDraft failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI email draft failed: " + e.getMessage(), e);
        }
    }

    // ── Shared Groq helpers ───────────────────────────────────────────────────

    /**
     * Envoie un prompt à l'API Groq et retourne la réponse en texte brut.
     * <p>
     * Utilise une température de 0.5 adaptée à la génération de descriptions textuelles.
     * </p>
     *
     * @param userPrompt le prompt utilisateur à envoyer
     * @return le contenu textuel de la réponse Groq
     * @throws RuntimeException si l'appel API échoue
     */
    private String callGroqText(String userPrompt) {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("Groq API key not configured (GROQ_API_KEY)");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
            Map.of("role", "system", "content", "Tu es un expert RH SYNAPSE. Réponds en français."),
            Map.of("role", "user",   "content", userPrompt)
        ));
        body.put("temperature", 0.5);
        body.put("max_tokens",  700);
        try {
            String raw = restClient.post()
                .uri(apiBaseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(String.class);
            return objectMapper.readTree(raw)
                .get("choices").get(0).get("message").get("content").asText().trim();
        } catch (Exception e) {
            log.error("[CvScoring] callGroqText failed: {}", e.getMessage(), e);
            throw new RuntimeException("Groq call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Envoie un prompt à l'API Groq et retourne la réponse JSON nettoyée.
     * <p>
     * Supprime les délimiteurs Markdown ({@code ```json...```}) et extrait
     * le premier objet JSON si le modèle ajoute du texte avant ou après.
     * Utilise une température de 0.2 adaptée à la génération JSON structurée.
     * </p>
     *
     * @param userPrompt le prompt utilisateur à envoyer
     * @return chaîne JSON propre (sans markdown)
     * @throws RuntimeException si l'appel API ou l'extraction JSON échoue
     */
    private String callGroqRaw(String userPrompt) {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("Groq API key not configured (GROQ_API_KEY)");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
            Map.of("role", "system", "content", "Tu es un expert RH. Réponds UNIQUEMENT en JSON valide, sans markdown, sans texte avant ou après."),
            Map.of("role", "user",   "content", userPrompt)
        ));
        body.put("temperature", 0.2);
        body.put("max_tokens",  1200);
        try {
            String raw = restClient.post()
                .uri(apiBaseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(String.class);
            String content = objectMapper.readTree(raw)
                .get("choices").get(0).get("message").get("content").asText().trim();
            // Strip markdown code fences
            if (content.startsWith("```")) {
                content = content.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("(?s)```\\s*$", "").trim();
            }
            // Extract first JSON object in case model added surrounding text
            int start = content.indexOf('{');
            int end   = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            }
            return content;
        } catch (Exception e) {
            log.error("[CvScoring] callGroqRaw failed: {}", e.getMessage(), e);
            throw new RuntimeException("Groq call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Construit le prompt de scoring CV à envoyer à l'API Groq.
     * <p>
     * Le prompt contient le profil complet du candidat et les instructions de scoring
     * pour que le LLM retourne un JSON structuré.
     * </p>
     *
     * @param c              le candidat à scorer
     * @param jobDescription description du poste (peut être {@code null})
     * @return le prompt complet formaté pour l'API Groq
     */
    private String buildPrompt(Candidate c, String jobDescription) {
        String skills = (c.getCompetences() != null && !c.getCompetences().isEmpty())
            ? String.join(", ", c.getCompetences())
            : "non spécifiées";
        int exp = c.getExperience() != null ? c.getExperience() : 0;

        String jobDesc = (jobDescription != null && !jobDescription.isBlank())
            ? "Description du poste: " + jobDescription
            : "Infer les compétences requises à partir du titre et du département.";

        return """
            Analyse ce profil de candidat et évalue-le pour le poste indiqué.

            ## Profil candidat
            - Nom: %s %s
            - Poste visé: %s
            - Département: %s
            - Expérience: %d an(s)
            - Compétences déclarées: %s
            - %s

            ## Instructions de scoring
            1. Score de 0 à 100 (100 = profil parfait pour le poste).
            2. Niveau: EXCELLENT (≥80), BON (60-79), PASSABLE (40-59), INSUFFISANT (<40).
            3. Liste les compétences déclarées qui correspondent au poste (max 5).
            4. Liste les compétences clés manquantes pour ce poste (max 5).
            5. Liste 2-3 points forts du profil.
            6. Une phrase de recommandation synthétique (max 150 caractères).

            Retourne UNIQUEMENT ce JSON (sans markdown, sans commentaire):
            {
              "score": <entier 0-100>,
              "niveau": "<EXCELLENT|BON|PASSABLE|INSUFFISANT>",
              "competencesMatchees": ["<comp>"],
              "competencesManquantes": ["<comp>"],
              "pointsForts": ["<point>"],
              "recommandation": "<phrase>"
            }
            """.formatted(
                c.getPrenom(), c.getNom(),
                c.getJobTitre(), c.getDepartement(),
                exp, skills, jobDesc
            );
    }
}
