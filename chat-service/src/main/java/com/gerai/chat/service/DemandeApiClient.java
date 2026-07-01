package com.gerai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Client REST du microservice chat-service vers les services de demandes RH.
 * <p>
 * {@code @Service} : déclare ce bean comme service Spring géré par le conteneur IoC.
 * <br>
 * {@code @Slf4j} (Lombok) : injecte un logger SLF4J pour la traçabilité des appels externes.
 * <p>
 * Ce client est utilisé exclusivement par {@link AiChatService} pour fournir au chatbot IA
 * la capacité d'exécuter des actions RH concrètes via le mécanisme de tool use (function calling) :
 * <ul>
 *   <li>Consulter le solde de congés annuels via {@code employe-service}.</li>
 *   <li>Lister les dernières demandes de l'employé via {@code demandes-service}.</li>
 *   <li>Soumettre une demande de congé, d'autorisation d'absence ou de crédit.</li>
 * </ul>
 * <p>
 * Toutes les requêtes sont effectuées avec le JWT Bearer de l'employé connecté
 * afin d'être exécutées dans son contexte de sécurité.
 *
 * @since 1.0
 */
@Service
@Slf4j
public class DemandeApiClient {

    /**
     * Correspondance entre les noms de types de congé du chatbot et les types attendus par le backend.
     * Clé : nom métier chatbot (ex. {@code ANNUEL}), valeur : type technique backend (ex. {@code CONGE}).
     */
    private static final Map<String, String>  BACKEND_TYPE = Map.ofEntries(
            Map.entry("ANNUEL",              "CONGE"),
            Map.entry("MALADIE",             "MALADIE"),
            Map.entry("FAMILIAL",            "FAMILIAL"),
            Map.entry("HAJJ",                "HAJJ"),
            Map.entry("MATERNITE",           "MATERNITE"),
            Map.entry("POSTNATAL",           "POSTNATAL"),
            Map.entry("ALLAITEMENT",         "ALLAITEMENT"),
            Map.entry("SANS_SOLDE",          "SANS_SOLDE"),
            Map.entry("LONGUE_MALADIE",      "LONGUE_MALADIE"),
            Map.entry("NAISSANCE_PERE",      "NAISSANCE_PERE"),
            Map.entry("CREATION_ENTREPRISE", "CREATION_ENTREPRISE"),
            Map.entry("OBLIGATIONS_LEGALES", "OBLIGATIONS_LEGALES"),
            Map.entry("RTT",                 "RTT")
    );

    /**
     * Correspondance entre les noms de types de congé et les identifiants numériques
     * de la table {@code TYPES_DEMANDE_CONFIG} en base Oracle.
     * Utilisé pour renseigner le champ {@code leaveTypeId} lors de la soumission.
     */
    private static final Map<String, Integer> LEAVE_TYPE_ID = Map.ofEntries(
            Map.entry("ANNUEL",              1),
            Map.entry("MALADIE",             2),
            Map.entry("SANS_SOLDE",          4),
            Map.entry("MATERNITE",           5),
            Map.entry("NAISSANCE_PERE",      6),
            Map.entry("FAMILIAL",            8),
            Map.entry("HAJJ",                9),
            Map.entry("POSTNATAL",           10),
            Map.entry("ALLAITEMENT",         11),
            Map.entry("LONGUE_MALADIE",      12),
            Map.entry("CREATION_ENTREPRISE", 14),
            Map.entry("OBLIGATIONS_LEGALES", 15)
    );

    /** URL de base du microservice {@code demandes-service} (configurée via {@code services.demandes.url}). */
    @Value("${services.demandes.url}")
    private String demandesUrl;

    /** URL de base du microservice {@code employe-service} (configurée via {@code services.employe.url}). */
    @Value("${services.employe.url}")
    private String employeUrl;

    /** Client HTTP Spring 6 pour les appels REST vers les microservices. */
    private final RestClient    restClient   = RestClient.create();

    /** Sérialiseur/désérialiseur JSON pour le traitement des réponses. */
    private final ObjectMapper  objectMapper = new ObjectMapper();

    // ── Solde congés ─────────────────────────────────────────────────────────

    /**
     * Récupère le solde de congés annuels restants de l'employé connecté.
     * Interroge l'endpoint {@code GET /api/employe/statistiques} du microservice {@code employe-service}.
     *
     * @param bearerToken le JWT Bearer de l'employé (utilisé pour l'authentification)
     * @return une chaîne lisible décrivant le solde (ex. "Vous avez 12 jour(s) restant(s) sur 30 jours.")
     *         ou un message d'erreur si l'appel échoue
     */
    public String getSoldeConges(String bearerToken) {
        try {
            String raw = restClient.get()
                    .uri(employeUrl + "/api/employe/statistiques")
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(String.class);

            JsonNode node      = objectMapper.readTree(raw);
            int      restants  = node.has("congesRestants") ? node.get("congesRestants").asInt() : -1;
            int      total     = node.has("congesTotal")    ? node.get("congesTotal").asInt()    : 30;

            if (restants < 0) return "Impossible de récupérer le solde de congés.";
            return "Vous avez " + restants + " jour(s) de congé annuel restant(s) sur " + total + " jours.";

        } catch (Exception e) {
            log.warn("[AI-Client] getSoldeConges : {}", e.getMessage());
            return "Impossible de récupérer votre solde de congés pour le moment.";
        }
    }

    // ── Mes demandes ──────────────────────────────────────────────────────────

    /**
     * Récupère les 5 dernières demandes (congé, crédit, autorisation) de l'employé connecté.
     * Interroge l'endpoint {@code GET /api/demandes/mes-demandes} du microservice {@code demandes-service}.
     *
     * @param bearerToken le JWT Bearer de l'employé (utilisé pour l'authentification)
     * @return une chaîne formatée listant les demandes récentes,
     *         ou un message indiquant l'absence de demandes ou une erreur
     */
    public String getMesDemandes(String bearerToken) {
        try {
            String raw = restClient.get()
                    .uri(demandesUrl + "/api/demandes/mes-demandes")
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(String.class);

            JsonNode array = objectMapper.readTree(raw);
            if (!array.isArray() || array.isEmpty())
                return "Vous n'avez pas encore de demandes enregistrées.";

            StringBuilder sb    = new StringBuilder("Vos dernières demandes :\n");
            int           limit = Math.min(5, array.size());
            for (int i = 0; i < limit; i++) {
                JsonNode d      = array.get(i);
                String   type   = d.has("typeLabel")   ? d.get("typeLabel").asText()
                                                       : d.get("type").asText("?");
                String   statut = d.has("statutLabel") ? d.get("statutLabel").asText()
                                                       : d.get("statut").asText("?");
                String   date   = d.has("dateCreation")
                        ? d.get("dateCreation").asText().substring(0, 10) : "";
                sb.append("• ").append(type).append(" (").append(statut).append(")")
                  .append(date.isEmpty() ? "" : " — " + date).append("\n");
            }
            return sb.toString().trim();

        } catch (Exception e) {
            log.warn("[AI-Client] getMesDemandes : {}", e.getMessage());
            return "Impossible de récupérer vos demandes pour le moment.";
        }
    }

    // ── Soumettre demande de congé ────────────────────────────────────────────

    /**
     * Soumet une demande de congé au nom de l'employé connecté.
     * <p>
     * Effectue les conversions nécessaires entre le type chatbot (ex. {@code ANNUEL})
     * et le type backend attendu par {@code demandes-service} (ex. {@code CONGE}),
     * et calcule automatiquement le nombre de jours calendaires.
     * <p>
     * Pour les congés maladie, ajoute une instruction de rappel sur la pièce jointe.
     *
     * @param params      les paramètres extraits par le modèle Groq :
     *                    {@code type_conge}, {@code date_debut}, {@code date_fin}, {@code commentaire}
     * @param bearerToken le JWT Bearer de l'employé (utilisé pour l'authentification)
     * @return un message de confirmation avec le numéro de référence,
     *         ou un message d'erreur en cas d'échec
     */
    public String soumettreDemandeConge(Map<String, Object> params, String bearerToken) {
        try {
            String chatbotType  = (String) params.get("type_conge");
            String backendType  = BACKEND_TYPE.getOrDefault(chatbotType, chatbotType);
            Integer leaveTypeId = LEAVE_TYPE_ID.get(chatbotType);

            Map<String, Object> body = new HashMap<>();
            body.put("type",   backendType);
            body.put("reason", params.getOrDefault("commentaire", "Demande soumise via assistant RH"));
            if (leaveTypeId != null) body.put("leaveTypeId", leaveTypeId);

            String debut = (String) params.get("date_debut");
            String fin   = (String) params.get("date_fin");
            body.put("startDate", debut);
            body.put("endDate",   fin);

            if (debut != null && fin != null) {
                long days = ChronoUnit.DAYS.between(LocalDate.parse(debut), LocalDate.parse(fin)) + 1;
                body.put("daysCount", days);
            }

            String type = (String) params.get("type_conge");
            String suffix = "MALADIE".equals(type)
                    ? " N'oubliez pas de joindre votre certificat médical via la plateforme dans les 48 heures."
                    : "";
            return postDemande(body, bearerToken, "Demande de congé soumise avec succès." + suffix);

        } catch (Exception e) {
            log.warn("[AI-Client] soumettreDemandeConge : {}", e.getMessage());
            return "Erreur lors de la soumission de la demande de congé : " + e.getMessage();
        }
    }

    // ── Soumettre autorisation d'absence ──────────────────────────────────────

    /**
     * Soumet une demande d'autorisation d'absence au nom de l'employé connecté.
     * <p>
     * Si la date de fin n'est pas fournie, elle est identique à la date de début (absence d'une journée).
     * L'horaire est fixé par défaut à 08h00-18h00.
     *
     * @param params      les paramètres extraits par le modèle Groq :
     *                    {@code motif}, {@code date_debut}, {@code date_fin} (optionnel)
     * @param bearerToken le JWT Bearer de l'employé (utilisé pour l'authentification)
     * @return un message de confirmation avec le numéro de référence,
     *         ou un message d'erreur en cas d'échec
     */
    public String soumettreDemandeAutorisation(Map<String, Object> params, String bearerToken) {
        try {
            String debut = (String) params.get("date_debut");
            String fin   = (String) params.getOrDefault("date_fin", debut);

            Map<String, Object> body = new HashMap<>();
            body.put("type",          "AUTRE");
            body.put("reason",        params.getOrDefault("motif", "Autorisation d'absence"));
            body.put("startDatetime", debut + "T08:00:00");
            body.put("endDatetime",   fin   + "T18:00:00");

            return postDemande(body, bearerToken, "Demande d'autorisation soumise avec succès");

        } catch (Exception e) {
            log.warn("[AI-Client] soumettreDemandeAutorisation : {}", e.getMessage());
            return "Erreur lors de la soumission de l'autorisation : " + e.getMessage();
        }
    }

    // ── Soumettre demande de crédit ────────────────────────────────────────────

    /**
     * Soumet une demande de crédit (prêt sur salaire) au nom de l'employé connecté.
     *
     * @param params      les paramètres extraits par le modèle Groq :
     *                    {@code montant} (en TND), {@code duree_mois}, {@code motif}
     * @param bearerToken le JWT Bearer de l'employé (utilisé pour l'authentification)
     * @return un message de confirmation avec le numéro de référence,
     *         ou un message d'erreur en cas d'échec
     */
    public String soumettreDemandeCredit(Map<String, Object> params, String bearerToken) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type",           "PRET");
            body.put("reason",         params.getOrDefault("motif", "Demande de crédit"));
            body.put("amount",         params.get("montant"));
            body.put("durationMonths", params.getOrDefault("duree_mois", 12));
            body.put("currency",       "TND");

            return postDemande(body, bearerToken, "Demande de crédit soumise avec succès");

        } catch (Exception e) {
            log.warn("[AI-Client] soumettreDemandeCredit : {}", e.getMessage());
            return "Erreur lors de la soumission du crédit : " + e.getMessage();
        }
    }

    // ── Soumettre demande de formation ────────────────────────────────────────

    public String soumettreDemandeFormation(Map<String, Object> params, String bearerToken) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type",          "FORMATION");
            body.put("trainingTitle", params.getOrDefault("intitule", "Formation professionnelle"));
            body.put("provider",      params.getOrDefault("organisme", ""));
            body.put("plannedDate",   params.get("date_prevue"));
            body.put("reason",        params.getOrDefault("motif", "Demande de formation via assistant RH"));

            Object duree = params.get("duree_jours");
            body.put("durationDays", duree != null ? ((Number) duree).intValue() : 1);

            Object cout = params.get("cout_estime");
            body.put("estimatedCost", cout != null ? cout : 0);

            body.put("modeFormation", params.getOrDefault("mode", "PRESENTIEL"));

            return postDemande(body, bearerToken, "Demande de formation soumise avec succès");

        } catch (Exception e) {
            log.warn("[AI-Client] soumettreDemandeFormation : {}", e.getMessage());
            return "Erreur lors de la soumission de la formation : " + e.getMessage();
        }
    }

    // ── Soumettre demande de document ─────────────────────────────────────────

    /** Correspondance entre le nom chatbot du type de document et son ID HR_PARAMETERS. */
    private static final Map<String, Long> DOC_TYPE_ID = Map.of(
            "ATTESTATION_TRAVAIL",   7L,
            "ATTESTATION_SALAIRE",   8L,
            "BULLETIN_PAIE",         9L,
            "ORDRE_MISSION",        10L,
            "ATTESTATION_CONGE",    11L,
            "LETTRE_RECOMMANDATION",12L
    );

    public String soumettreDemandeDocument(Map<String, Object> params, String bearerToken) {
        try {
            String typeDoc  = (String) params.getOrDefault("type_document", "ATTESTATION_TRAVAIL");
            Long   docTypeId = DOC_TYPE_ID.getOrDefault(typeDoc, 7L);

            Map<String, Object> body = new HashMap<>();
            body.put("type",        "DOCUMENT");
            body.put("docTypeId",   docTypeId);
            body.put("reason",      params.getOrDefault("motif", "Demande de document via assistant RH"));
            body.put("copiesCount", params.getOrDefault("exemplaires", 1));
            body.put("language",    params.getOrDefault("langue", "FR"));

            return postDemande(body, bearerToken, "Demande de document soumise avec succès");

        } catch (Exception e) {
            log.warn("[AI-Client] soumettreDemandeDocument : {}", e.getMessage());
            return "Erreur lors de la soumission du document : " + e.getMessage();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Effectue une requête POST vers l'endpoint {@code /api/demandes} du microservice {@code demandes-service}.
     * Centralise la logique d'appel HTTP et de récupération du numéro de référence.
     *
     * @param body          le corps JSON de la demande à envoyer
     * @param bearerToken   le JWT Bearer de l'employé (utilisé pour l'authentification)
     * @param successPrefix le préfixe du message de succès (ex. "Demande de congé soumise avec succès")
     * @return le message de succès avec le numéro de référence (ex. "... Référence : #42"),
     *         ou un message d'erreur en cas d'échec HTTP
     */
    private String postDemande(Map<String, Object> body, String bearerToken, String successPrefix) {
        try {
            String raw = restClient.post()
                    .uri(demandesUrl + "/api/demandes")
                    .header("Authorization", "Bearer " + bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(raw);
            Long     id   = node.has("id") ? node.get("id").asLong() : null;
            return successPrefix + (id != null ? ". Référence : #" + id : ".");

        } catch (Exception e) {
            log.warn("[AI-Client] postDemande : {}", e.getMessage());
            return "Erreur lors de la soumission : " + e.getMessage();
        }
    }
}
