package com.gerai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gerai.chat.dto.ai.AiChatResponse;
import com.gerai.chat.dto.ai.AiMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Service implémentant le chatbot IA RH de la plateforme SYNAPSE.
 * <p>
 * Utilise l'API Groq (compatible OpenAI) avec le modèle {@code llama-3.3-70b-versatile}
 * pour répondre aux questions RH des employés en français.
 * <p>
 * {@code @Service} : déclare ce bean comme service Spring géré par le conteneur IoC.
 * <br>
 * {@code @RequiredArgsConstructor} (Lombok) : génère l'injection du {@link DemandeApiClient} par constructeur.
 * <p>
 * Fonctionnalités :
 * <ul>
 *   <li>Réponses contextualisées basées sur une base de connaissance RH ({@code hr-knowledge.txt}).</li>
 *   <li>Tool use (function calling) pour consulter le solde de congés et soumettre des demandes.</li>
 *   <li>Mécanisme de confirmation obligatoire avant toute soumission de demande.</li>
 *   <li>Historique limité aux 6 derniers échanges pour respecter le contexte du modèle.</li>
 * </ul>
 * <p>
 * Format API Groq : {@code messages[{role, content}]}, {@code tools[{type, function}]},
 * réponse dans {@code choices[0].message}.
 *
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    /**
     * Client d'appel aux APIs de demandes RH (congés, crédits, autorisations).
     */
    private final DemandeApiClient demandeApiClient;

    /**
     * Sérialiseur/désérialiseur JSON pour les échanges avec l'API Groq.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Client HTTP Spring 6 pour les appels à l'API Groq.
     */
    private final RestClient restClient = RestClient.create();

    /**
     * Clé d'API Groq pour l'authentification (configurée via {@code groq.api.key}).
     */
    @Value("${groq.api.key}")
    private String apiKey;

    /**
     * Identifiant du modèle Groq à utiliser (ex. {@code llama-3.3-70b-versatile}).
     */
    @Value("${groq.model}")
    private String model;

    /**
     * URL de base de l'API Groq (ex. {@code https://api.groq.com/openai/v1}).
     */
    @Value("${groq.api.base-url}")
    private String apiBaseUrl;

    /**
     * Ressource classpath contenant la base de connaissance RH utilisée dans le prompt système.
     */
    @Value("classpath:hr-knowledge.txt")
    private Resource hrKnowledgeResource;

    /**
     * Contenu de la base de connaissance RH, chargé au démarrage via {@link #loadKnowledge()}.
     */
    private String hrKnowledge = "";

    /**
     * Charge la base de connaissance RH depuis le fichier classpath au démarrage de l'application.
     * Cette méthode est exécutée automatiquement après l'injection des dépendances.
     *
     * @throws IOException si le fichier {@code hr-knowledge.txt} est introuvable ou illisible
     */
    @PostConstruct
    private void loadKnowledge() throws IOException {
        hrKnowledge = hrKnowledgeResource.getContentAsString(StandardCharsets.UTF_8);
        log.info("[AI Chat] Base de connaissance RH chargée ({} caractères)", hrKnowledge.length());

        // Fallback: read GROQ_API_KEY directly from .env if Spring dotenv didn't load it
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = readKeyFromDotenv();
        }
        log.info("[AI Chat] Groq key: {} chars, prefix={}", apiKey.length(),
                apiKey.length() >= 8 ? apiKey.substring(0, 8) : (apiKey.isEmpty() ? "VIDE" : apiKey));
    }

    /**
     * Reads GROQ_API_KEY from .env candidates when Spring dotenv import misses the file.
     */
    private String readKeyFromDotenv() {
        String[] candidates = {".env", "chat-service/.env", "../chat-service/.env"};
        for (String candidate : candidates) {
            try {
                Path p = Path.of(candidate).toAbsolutePath();
                if (Files.exists(p)) {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        if (line.startsWith("GROQ_API_KEY=")) {
                            String key = line.substring("GROQ_API_KEY=".length()).trim();
                            if (!key.isBlank()) {
                                log.info("[AI Chat] GROQ_API_KEY chargée depuis {}", p);
                                return key;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[AI Chat] Lecture .env échouée pour {}: {}", candidate, e.getMessage());
            }
        }
        log.error("[AI Chat] GROQ_API_KEY introuvable dans tous les fichiers .env candidates !");
        return "";
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Mots-clés reconnus comme confirmation explicite de l'utilisateur.
     * Leur présence dans le message active les outils de soumission de demandes.
     */
    private static final Set<String> CONFIRMATION_WORDS = Set.of(
            "oui", "confirme", "confirmation", "d'accord", "ok", "yes",
            "valide", "validé", "procède", "soumettre", "envoie", "accepte", "correct"
    );

    /**
     * Détermine si le message de l'utilisateur constitue une confirmation explicite.
     * Si vrai, les outils de soumission de demandes seront inclus dans l'appel Groq.
     *
     * @param message le message textuel de l'utilisateur
     * @return {@code true} si le message contient un mot de confirmation
     */
    private boolean isConfirmation(String message) {
        String lower = message.toLowerCase();
        return CONFIRMATION_WORDS.stream().anyMatch(lower::contains);
    }

    /**
     * Point d'entrée principal du chatbot IA RH.
     * <p>
     * Construit les messages, appelle l'API Groq, gère le tool use si nécessaire
     * et retourne la réponse textuelle au frontend.
     *
     * @param message      le message courant de l'employé
     * @param history      l'historique de conversation (limité aux 6 derniers échanges)
     * @param employeeName le nom de l'employé connecté (pour personnaliser le prompt)
     * @param token        le JWT Bearer de l'employé (pour les appels aux APIs de demandes)
     * @return la réponse du chatbot encapsulée dans un {@link AiChatResponse}
     */
    public AiChatResponse chat(String message,
                               List<AiMessage> history,
                               String employeeName,
                               String token,
                               boolean isEmploye) {
        try {
            List<Map<String, Object>> messages = buildMessages(history, message, employeeName, isEmploye);

            if (!isEmploye) {
                // Admin / chef: knowledge-base only, no tools
                JsonNode response = callGroqNoTools(messages);
                return AiChatResponse.success(extractText(response));
            }

            boolean allowSubmit = isConfirmation(message);
            JsonNode response = callGroq(messages, allowSubmit);

            if (hasToolCall(response)) {
                return handleToolUse(response, messages, token);
            }

            return AiChatResponse.success(extractText(response));

        } catch (Exception e) {
            log.error("[AI Chat] Erreur inattendue : {}", e.getMessage(), e);
            String userMsg = e.getMessage() != null
                    ? e.getMessage()
                    : "Une erreur est survenue. Veuillez réessayer dans quelques instants.";
            return AiChatResponse.error(userMsg);
        }
    }

    // ── Tool use handler ──────────────────────────────────────────────────────

    /**
     * Gère l'exécution d'un outil (function calling) demandé par le modèle Groq.
     * <p>
     * Exécute uniquement le premier outil demandé pour éviter les soumissions doubles.
     * Ajoute le résultat à l'historique et effectue un second appel Groq pour obtenir
     * la réponse finale en langage naturel.
     *
     * @param response le nœud JSON de la réponse Groq contenant les appels d'outils
     * @param messages l'historique des messages jusqu'à cet appel
     * @param token    le JWT Bearer pour les appels aux APIs de demandes
     * @return la réponse finale du chatbot après exécution de l'outil
     * @throws Exception en cas d'erreur lors du parsing JSON ou de l'appel Groq
     */
    private AiChatResponse handleToolUse(JsonNode response,
                                         List<Map<String, Object>> messages,
                                         String token) throws Exception {

        JsonNode choice = response.get("choices").get(0);
        JsonNode toolCalls = choice.get("message").get("tool_calls");

        // Add assistant message (with tool_calls) to history
        List<Map<String, Object>> continued = new ArrayList<>(messages);
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", choice.get("message").path("content").asText(""));
        assistantMsg.put("tool_calls", objectMapper.convertValue(toolCalls, List.class));
        continued.add(assistantMsg);

        // Execute only the first tool call to prevent duplicate submissions
        JsonNode tc = toolCalls.get(0);
        String toolCallId = tc.get("id").asText();
        String toolName = tc.get("function").get("name").asText();
        String argsJson = tc.get("function").get("arguments").asText();

        Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
        log.info("[AI Chat] Outil appelé : {}", toolName);
        String result = executeTool(toolName, args, token);
        log.info("[AI Chat] Résultat outil : {}", result);

        Map<String, Object> toolMsg = new LinkedHashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", toolCallId);
        toolMsg.put("content", result);
        continued.add(toolMsg);

        JsonNode finalResponse = callGroq(continued);

        // If model attempts another tool call, extract the tool result directly
        if (hasToolCall(finalResponse)) {
            log.warn("[AI Chat] Second tool call ignoré, retour du résultat direct");
            return AiChatResponse.success(result);
        }

        return AiChatResponse.success(extractText(finalResponse));
    }

    // ── Groq API call ─────────────────────────────────────────────────────────

    /**
     * Appel à l'API Groq sans outils de soumission (lecture seule uniquement).
     * Délègue à {@link #callGroq(List, boolean)} avec {@code allowSubmit = false}.
     *
     * @param messages la liste des messages à envoyer au modèle
     * @return le nœud JSON de la réponse complète de l'API Groq
     * @throws Exception en cas d'erreur HTTP ou de parsing JSON
     */
    private JsonNode callGroq(List<Map<String, Object>> messages) throws Exception {
        return callGroq(messages, false);
    }

    /**
     * Admin/chef: straight completion with no tools at all.
     */
    private JsonNode callGroqNoTools(List<Map<String, Object>> messages) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 1024);

        String raw;
        try {
            raw = restClient.post()
                    .uri(apiBaseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            String errBody = ex.getResponseBodyAsString();
            log.error("[AI Chat] Groq HTTP {} — body: {}", ex.getStatusCode(), errBody);
            throw new RuntimeException("Groq HTTP " + ex.getStatusCode() + ": " + errBody, ex);
        }

        JsonNode node = objectMapper.readTree(raw);
        if (node.has("error")) {
            String msg = node.get("error").path("message").asText("unknown error");
            log.error("[AI Chat] Groq error: {}", msg);
            throw new RuntimeException("Groq error: " + msg);
        }
        return node;
    }

    /**
     * Effectue un appel à l'API Groq (compatible OpenAI) avec le modèle configuré.
     * <p>
     * Construit le corps de la requête avec les outils appropriés selon {@code allowSubmit}.
     * En cas d'erreur HTTP (4xx/5xx), relance l'exception avec le corps de réponse Groq.
     *
     * @param messages    la liste des messages (système + historique + message courant)
     * @param allowSubmit si {@code true}, inclut les outils de soumission de demandes
     *                    (uniquement après confirmation explicite de l'utilisateur)
     * @return le nœud JSON de la réponse complète de l'API Groq
     * @throws RuntimeException en cas d'erreur HTTP Groq ou d'erreur dans le champ {@code error} de la réponse
     * @throws Exception        en cas d'erreur de parsing JSON
     */
    private JsonNode callGroq(List<Map<String, Object>> messages, boolean allowSubmit) throws Exception {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", buildTools(allowSubmit));
        body.put("tool_choice", "auto");
        body.put("temperature", 0.3);
        body.put("max_tokens", 1024);

        String raw;
        try {
            raw = restClient.post()
                    .uri(apiBaseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            String errBody = ex.getResponseBodyAsString();
            log.error("[AI Chat] Groq HTTP {} — body: {}", ex.getStatusCode(), errBody);
            throw new RuntimeException("Groq HTTP " + ex.getStatusCode() + ": " + errBody, ex);
        }

        JsonNode node = objectMapper.readTree(raw);
        if (node.has("error")) {
            String msg = node.get("error").path("message").asText("unknown error");
            log.error("[AI Chat] Groq error: {}", msg);
            throw new RuntimeException("Groq error: " + msg);
        }
        return node;
    }

    // ── Tool dispatcher ────────────────────────────────────────────────────────

    /**
     * Dispatche l'exécution d'un outil (function calling) vers la méthode correspondante
     * du {@link DemandeApiClient}.
     *
     * @param toolName le nom de l'outil demandé par le modèle Groq
     * @param input    les paramètres de l'outil désérialisés depuis le JSON Groq
     * @param token    le JWT Bearer de l'employé pour les appels aux APIs de demandes
     * @return le résultat textuel de l'exécution de l'outil, ou un message d'erreur si l'outil est inconnu
     */
    private String executeTool(String toolName, Map<String, Object> input, String token) {
        return switch (toolName) {
            case "verifier_solde_conges" -> demandeApiClient.getSoldeConges(token);
            case "get_mes_demandes" -> demandeApiClient.getMesDemandes(token);
            case "soumettre_demande_conge" -> demandeApiClient.soumettreDemandeConge(input, token);
            case "soumettre_demande_autorisation" -> demandeApiClient.soumettreDemandeAutorisation(input, token);
            case "soumettre_demande_credit" -> demandeApiClient.soumettreDemandeCredit(input, token);
            case "soumettre_demande_formation" -> demandeApiClient.soumettreDemandeFormation(input, token);
            case "soumettre_demande_document" -> demandeApiClient.soumettreDemandeDocument(input, token);
            default -> {
                log.warn("[AI Chat] Outil inconnu : {}", toolName);
                yield "Outil non reconnu : " + toolName;
            }
        };
    }

    // ── Messages builder ──────────────────────────────────────────────────────

    /**
     * Construit la liste de messages à envoyer à l'API Groq.
     * <p>
     * Inclut dans l'ordre :
     * <ol>
     *   <li>Le message système (prompt de rôle + base de connaissance RH + date).</li>
     *   <li>Les 6 derniers messages de l'historique (pour respecter la fenêtre de contexte).</li>
     *   <li>Le message courant de l'utilisateur.</li>
     * </ol>
     *
     * @param history    l'historique de conversation (peut être null ou vide)
     * @param newMessage le message courant de l'utilisateur
     * @param nom        le nom de l'employé connecté (pour la personnalisation du prompt système)
     * @return la liste de maps {@code {role, content}} prête pour l'API Groq
     */
    private List<Map<String, Object>> buildMessages(List<AiMessage> history,
                                                    String newMessage,
                                                    String nom,
                                                    boolean isEmploye) {
        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of("role", "system", "content", buildSystemPrompt(nom, isEmploye)));

        if (history != null) {
            List<AiMessage> trimmed = history.size() > 6
                    ? history.subList(history.size() - 6, history.size())
                    : history;
            for (AiMessage h : trimmed) {
                messages.add(Map.of("role", h.role(), "content", h.content()));
            }
        }

        messages.add(Map.of("role", "user", "content", newMessage));
        return messages;
    }

    // ── System prompt ──────────────────────────────────────────────────────────

    /**
     * Génère le prompt système personnalisé pour le chatbot IA RH SYNAPSE.
     * <p>
     * Le prompt intègre :
     * <ul>
     *   <li>Le nom de l'employé connecté pour la personnalisation des réponses.</li>
     *   <li>La date du jour pour la conversion des dates relatives ("lundi prochain").</li>
     *   <li>La base de connaissance RH chargée depuis {@code hr-knowledge.txt}.</li>
     *   <li>Les règles de comportement du chatbot (confirmation, limites, format).</li>
     * </ul>
     *
     * @param nom le nom de l'employé connecté (extrait du JWT par {@link AiChatController#resolveName})
     * @return la chaîne de prompt système prête à être passée comme message de rôle {@code system}
     */
    private String buildSystemPrompt(String nom, boolean isEmploye) {
        if (!isEmploye) {
            return """
                    Tu es l'assistant RH de SYNAPSE. Tu réponds aux questions RH en français.
                    Tu t'adresses à un responsable (admin RH, chef de projet ou membre du comité).
                    
                    Utilisateur connecté : %s
                    Date du jour         : %s
                    
                    %s
                    
                    === RÈGLES DE COMPORTEMENT ===
                    1. Réponds uniquement à partir de la base de connaissance ci-dessus.
                    2. Si une information n'est PAS dans la base de connaissance, réponds :
                       "Je n'ai pas cette information. Veuillez consulter la politique RH interne."
                    3. Tu ne soumet aucune demande et n'exécutes aucune action en nom d'un utilisateur.
                    4. Réponds toujours en français, de manière concise et professionnelle.
                    """.formatted(nom, LocalDate.now(), hrKnowledge);
        }
        return """
                Tu es l'assistant RH de SYNAPSE. Tu aides les employés en français.
                Tu peux répondre aux questions RH et soumettre des demandes en leur nom.
                
                Employé connecté : %s
                Date du jour     : %s
                
                %s
                
                === TYPES DE DEMANDES QUE TU PEUX SOUMETTRE ===
                - Congé (annuel, maladie, maternité, sans solde, etc.) → soumettre_demande_conge
                - Autorisation d'absence → soumettre_demande_autorisation
                - Crédit / prêt salarial → soumettre_demande_credit
                - Formation professionnelle → soumettre_demande_formation
                - Document administratif (attestation, bulletin, etc.) → soumettre_demande_document
                
                === RÈGLES DE COMPORTEMENT ===
                1. Demande TOUJOURS une confirmation explicite avant de soumettre une demande.
                2. Recueille TOUS les paramètres requis avant de proposer la soumission.
                3. Pour les congés : demande type, date début, date fin. Calcule les jours : (fin - début + 1).
                4. Pour une formation : demande intitulé, organisme, date prévue, durée, coût estimé.
                5. Pour un document : demande le type (attestation de travail, bulletin de paie, etc.).
                6. Si l'employé demande son solde de congés, utilise verifier_solde_conges.
                7. Convertis les dates françaises ("10 juin", "lundi prochain") en YYYY-MM-DD.
                8. Si une règle n'est PAS dans la base de connaissance, réponds :
                   "Je n'ai pas cette information. Veuillez contacter le service RH."
                9. Quand un outil retourne des données, inclus TOUJOURS leur contenu complet
                   dans ta réponse — l'utilisateur ne voit QUE ton message texte.
                """.formatted(nom, LocalDate.now(), hrKnowledge);
    }

    // ── Tools (OpenAI format) ─────────────────────────────────────────────────

    /**
     * Construit la liste des outils (function calling) au format OpenAI/Groq.
     * <p>
     * Inclut toujours les outils de lecture ({@code verifier_solde_conges}, {@code get_mes_demandes}).
     * N'inclut les outils de soumission ({@code soumettre_demande_*}) que si {@code allowSubmit = true},
     * c'est-à-dire si l'utilisateur a fourni une confirmation explicite dans son message.
     *
     * @param allowSubmit si {@code true}, inclut les outils de soumission de demandes RH
     * @return la liste de définitions d'outils au format attendu par l'API Groq
     */
    private List<Map<String, Object>> buildTools(boolean allowSubmit) {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("verifier_solde_conges",
                "Vérifier le nombre de jours de congé annuel restants de l'employé.",
                params()));
        tools.add(tool("get_mes_demandes",
                "Récupérer les dernières demandes (congé, crédit, autorisation) de l'employé.",
                params()));
        if (!allowSubmit) return tools;

        tools.add(tool("soumettre_demande_conge",
                "Soumettre une demande de congé. Appeler UNIQUEMENT après confirmation explicite.",
                params(
                        "type_conge", Map.of("type", "string",
                                "enum", List.of("ANNUEL", "MALADIE", "MATERNITE", "POSTNATAL",
                                        "ALLAITEMENT", "FAMILIAL", "NAISSANCE_PERE", "HAJJ",
                                        "SANS_SOLDE", "LONGUE_MALADIE", "RTT",
                                        "CREATION_ENTREPRISE", "OBLIGATIONS_LEGALES"),
                                "description", "Type de congé"),
                        "date_debut", Map.of("type", "string", "description", "Date de début (YYYY-MM-DD)"),
                        "date_fin", Map.of("type", "string", "description", "Date de fin (YYYY-MM-DD)"),
                        "commentaire", Map.of("type", "string", "description", "Commentaire optionnel"),
                        "__required__", List.of("type_conge", "date_debut", "date_fin")
                )));
        tools.add(tool("soumettre_demande_autorisation",
                "Soumettre une demande d'autorisation d'absence. Appeler UNIQUEMENT après confirmation.",
                params(
                        "motif", Map.of("type", "string", "description", "Motif de l'absence"),
                        "date_debut", Map.of("type", "string", "description", "Date de début (YYYY-MM-DD)"),
                        "date_fin", Map.of("type", "string",
                                "description", "Date de fin (YYYY-MM-DD), identique à date_debut si absence d'une journée"),
                        "__required__", List.of("motif", "date_debut")
                )));
        tools.add(tool("soumettre_demande_credit",
                "Soumettre une demande de crédit (prêt sur salaire). Appeler UNIQUEMENT après confirmation.",
                params(
                        "montant", Map.of("type", "number", "description", "Montant en TND"),
                        "duree_mois", Map.of("type", "integer", "description", "Durée de remboursement en mois"),
                        "motif", Map.of("type", "string", "description", "Motif de la demande"),
                        "__required__", List.of("montant", "duree_mois")
                )));
        tools.add(tool("soumettre_demande_formation",
                "Soumettre une demande de formation professionnelle. Appeler UNIQUEMENT après confirmation.",
                params(
                        "intitule", Map.of("type", "string", "description", "Intitulé de la formation"),
                        "organisme", Map.of("type", "string", "description", "Nom de l'organisme ou prestataire"),
                        "date_prevue", Map.of("type", "string", "description", "Date de début prévue (YYYY-MM-DD)"),
                        "duree_jours", Map.of("type", "integer", "description", "Durée en jours"),
                        "cout_estime", Map.of("type", "number", "description", "Coût estimé en TND (0 si non connu)"),
                        "mode", Map.of("type", "string",
                                "enum", List.of("PRESENTIEL", "DISTANCIEL", "HYBRIDE"),
                                "description", "Mode de la formation"),
                        "motif", Map.of("type", "string", "description", "Justification ou objectif"),
                        "__required__", List.of("intitule", "date_prevue")
                )));
        tools.add(tool("soumettre_demande_document",
                "Soumettre une demande de document administratif. Appeler UNIQUEMENT après confirmation.",
                params(
                        "type_document", Map.of("type", "string",
                                "enum", List.of("ATTESTATION_TRAVAIL", "ATTESTATION_SALAIRE",
                                        "BULLETIN_PAIE", "ORDRE_MISSION",
                                        "ATTESTATION_CONGE", "LETTRE_RECOMMANDATION"),
                                "description", "Type de document souhaité"),
                        "exemplaires", Map.of("type", "integer", "description", "Nombre d'exemplaires (défaut 1)"),
                        "langue", Map.of("type", "string",
                                "enum", List.of("FR", "AR"),
                                "description", "Langue du document"),
                        "motif", Map.of("type", "string", "description", "Motif ou précision"),
                        "__required__", List.of("type_document")
                )));
        return tools;
    }

    /**
     * Construit la définition d'un outil au format OpenAI/Groq.
     *
     * @param name        le nom de la fonction (ex. {@code verifier_solde_conges})
     * @param description la description en langage naturel de ce que fait l'outil
     * @param parameters  le schéma JSON des paramètres de la fonction
     * @return une map représentant la définition complète de l'outil au format {@code {type, function}}
     */
    private Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    /**
     * Construit un schéma JSON de paramètres vide (pour les outils sans paramètre).
     *
     * @return une map JSON représentant un objet sans propriétés : {@code {type: object, properties: {}}}
     */
    private Map<String, Object> params() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        p.put("properties", Map.of());
        return p;
    }

    /**
     * Construit un schéma JSON de paramètres avec les propriétés spécifiées.
     * <p>
     * Accepte une liste variadique de paires clé-valeur. La clé spéciale {@code __required__}
     * est utilisée pour définir la liste des paramètres obligatoires de la fonction.
     *
     * @param kvPairs les paires alternées nom/définition de propriétés (ex. {@code "date_debut", Map.of(...)})
     *                et optionnellement {@code "__required__", List.of(...)} pour les paramètres obligatoires
     * @return une map JSON représentant le schéma de paramètres complet au format OpenAI/Groq
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> params(Object... kvPairs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (int i = 0; i < kvPairs.length - 1; i += 2) {
            String key = (String) kvPairs[i];
            Object val = kvPairs[i + 1];
            if ("__required__".equals(key)) {
                required = (List<String>) val;
            } else {
                properties.put(key, val);
            }
        }

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "object");
        p.put("properties", properties);
        if (!required.isEmpty()) p.put("required", required);
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Vérifie si la réponse Groq contient un ou plusieurs appels d'outils (tool use).
     * Inspecte {@code choices[0].message.tool_calls} dans le JSON de réponse.
     *
     * @param response le nœud JSON de la réponse complète de l'API Groq
     * @return {@code true} si la réponse contient au moins un appel d'outil non nul
     */
    private boolean hasToolCall(JsonNode response) {
        try {
            JsonNode msg = response.get("choices").get(0).get("message");
            return msg.has("tool_calls") && !msg.get("tool_calls").isEmpty()
                    && !msg.get("tool_calls").isNull();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extrait le texte de la réponse principale du modèle Groq.
     * Lit {@code choices[0].message.content} et gère les cas nuls ou vides
     * en retournant un message d'erreur utilisateur compréhensible.
     *
     * @param response le nœud JSON de la réponse complète de l'API Groq
     * @return le texte de la réponse du chatbot, ou un message de repli en cas d'erreur
     */
    private String extractText(JsonNode response) {
        try {
            JsonNode content = response.get("choices").get(0).get("message").get("content");
            if (content == null || content.isNull()) {
                log.warn("[AI Chat] content null — réponse complète : {}", response);
                return "Je n'ai pas pu générer une réponse. Veuillez réessayer.";
            }
            String text = content.asText().trim();
            return text.isEmpty() ? "Je n'ai pas pu générer une réponse. Veuillez réessayer." : text;
        } catch (Exception e) {
            log.warn("[AI Chat] extractText échoué : {} — réponse : {}", e.getMessage(), response);
            return "Je n'ai pas pu générer une réponse. Veuillez réessayer.";
        }
    }
}
