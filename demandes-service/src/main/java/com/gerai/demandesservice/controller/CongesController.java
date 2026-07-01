package com.gerai.demandesservice.controller;

import com.gerai.demandesservice.dto.DemandeRequest;
import com.gerai.demandesservice.dto.DemandeResponse;
import com.gerai.demandesservice.dto.MedicalDecisionRequest;
import com.gerai.demandesservice.model.TypeDemande;
import com.gerai.demandesservice.service.DemandeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST dédié à la gestion des congés — préfixe {@code /api/conges}.
 * <p>
 * {@code @RestController} : toutes les méthodes retournent du JSON directement.
 * <p>
 * {@code @RequestMapping("/api/conges")} : regroupe les endpoints spécifiques
 * aux congés séparément des demandes génériques de {@code DemandeController}.
 * <p>
 * Délègue la logique métier à {@link DemandeService} et utilise directement
 * {@link JdbcTemplate} pour les requêtes admin multi-tables.
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/conges")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class CongesController {

    /** Service principal gérant le cycle de vie des demandes RH. */
    private final DemandeService demandeService;
    /** Accès direct JDBC pour les requêtes admin multi-tables (vue admin congés). */
    private final JdbcTemplate   jdbcTemplate;

    /**
     * Retourne le solde de congés annuels de l'employé connecté pour l'année en cours.
     * <p>
     * Contient : soldeTotal (30 jours), soldeUtilise, soldeRestant, demandesEnAttente.
     *
     * @param auth contexte d'authentification Spring Security de l'employé connecté
     * @return map avec les données de solde, ou valeurs par défaut en cas d'erreur
     */
    @GetMapping("/solde")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> getSolde(Authentication auth) {
        try {
            return ResponseEntity.ok(demandeService.getCongesSolde(auth));
        } catch (Exception e) {
            log.error("getSolde failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "soldeTotal", 30, "soldeUtilise", 0,
                    "soldeRestant", 30, "demandesEnAttente", 0));
        }
    }

    /**
     * Retourne l'historique des demandes de congé de l'employé connecté.
     *
     * @param auth contexte d'authentification de l'employé
     * @return liste des congés de l'employé, liste vide en cas d'erreur
     */
    @GetMapping("/mes-demandes")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getMesConges(Authentication auth) {
        try {
            return ResponseEntity.ok(demandeService.getMesConges(auth));
        } catch (Exception e) {
            log.error("getMesConges failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Retourne les congés de l'équipe du chef (ou de l'employé connecté) qui chevauchent
     * la plage de dates [{@code dateDebut}, {@code dateFin}].
     * Les congés refusés et annulés sont exclus.
     * GET /api/conges/equipe?dateDebut=2026-05-01&amp;dateFin=2026-05-31
     *
     * @param auth      contexte d'authentification du chef ou de l'employé
     * @param dateDebut date de début de la plage (défaut : 1er jour du mois courant)
     * @param dateFin   date de fin de la plage (défaut : dernier jour du mois courant)
     * @return liste des congés de l'équipe chevauchant la plage, liste vide en cas d'erreur
     */
    @GetMapping("/equipe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DemandeResponse>> getCongesEquipe(
            Authentication auth,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        try {
            LocalDate debut = dateDebut != null ? dateDebut : LocalDate.now().withDayOfMonth(1);
            LocalDate fin   = dateFin   != null ? dateFin   : LocalDate.now().withDayOfMonth(
                    LocalDate.now().lengthOfMonth());
            return ResponseEntity.ok(demandeService.getCongesEquipe(auth, debut, fin));
        } catch (Exception e) {
            log.error("getCongesEquipe failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Retourne un agrégat de statistiques de congés pour l'employé connecté :
     * solde restant, jours utilisés, nombre total de demandes et demandes en attente.
     *
     * @param auth contexte d'authentification de l'employé
     * @return map de statistiques, ou valeurs par défaut en cas d'erreur
     */
    @GetMapping("/statistiques")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> getStatistiques(Authentication auth) {
        try {
            Map<String, Object> solde = demandeService.getCongesSolde(auth);
            return ResponseEntity.ok(Map.of(
                    "soldeRestant",    solde.get("soldeRestant"),
                    "joursUtilises",   solde.get("soldeUtilise"),
                    "demandesTotal",   demandeService.getMesConges(auth).size(),
                    "demandesEnAttente", solde.get("demandesEnAttente")));
        } catch (Exception e) {
            log.error("getStatistiques failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "soldeRestant", 30, "joursUtilises", 0,
                    "demandesTotal", 0, "demandesEnAttente", 0));
        }
    }

    /**
     * Crée une demande de congé (raccourci — le type {@code CONGE} est imposé automatiquement).
     * Corps attendu : {@code { leaveTypeId, startDate, endDate, daysCount, reason }}.
     * Vérifie le quota annuel et délègue la création à {@link com.gerai.demandesservice.service.DemandeService}.
     *
     * @param request données de la demande de congé (type forcé à CONGE)
     * @param auth    contexte d'authentification de l'employé connecté
     * @return la demande de congé créée avec statut HTTP 201 Created, ou 500 en cas d'erreur
     */
    @PostMapping("/demander")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> demanderConge(
            @RequestBody DemandeRequest request,
            Authentication auth) {
        request.setType(TypeDemande.CONGE);
        log.info("[Conges] POST /demander");
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(demandeService.creerDemande(request, auth));
        } catch (Exception e) {
            log.error("demanderConge failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Agrège toutes les données de congé de l'employé connecté en une seule requête :
     * solde annuel, liste des demandes et statistiques.
     * Retourne : {@code { solde, demandes, statistiques }}.
     *
     * @param auth contexte d'authentification de l'employé connecté
     * @return map contenant les trois blocs de données, ou valeurs par défaut en cas d'erreur
     */
    @GetMapping("/complet")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> getComplet(Authentication auth) {
        try {
            Map<String, Object> solde    = demandeService.getCongesSolde(auth);
            List<DemandeResponse> demandes = demandeService.getMesConges(auth);
            Map<String, Object> stats = Map.of(
                    "soldeRestant",       solde.getOrDefault("soldeRestant",       30),
                    "joursUtilises",      solde.getOrDefault("soldeUtilise",       0),
                    "demandesTotal",      demandes.size(),
                    "demandesEnAttente",  solde.getOrDefault("demandesEnAttente",  0));
            return ResponseEntity.ok(Map.of(
                    "solde",         solde,
                    "demandes",      demandes,
                    "statistiques",  stats));
        } catch (Exception e) {
            log.error("getComplet failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "solde",        Map.of("soldeTotal", 30, "soldeRestant", 30, "soldeUtilise", 0, "demandesEnAttente", 0),
                    "demandes",     List.of(),
                    "statistiques", Map.of("soldeRestant", 30, "joursUtilises", 0, "demandesTotal", 0, "demandesEnAttente", 0)));
        }
    }

    /**
     * Enregistre la décision du comité médical pour un congé de longue maladie
     * (type {@code LONGUE_MALADIE} — identifiant 12).
     * <p>
     * Si approuvé, la demande passe au workflow normal (EN_ATTENTE → chef).
     * Si refusé, la demande est clôturée avec le statut {@code REFUSE}.
     *
     * @param id       identifiant de la demande de congé longue maladie
     * @param decision décision médicale (approuvé/refusé + commentaire)
     * @param auth     contexte d'authentification du médecin/RH
     * @return la demande mise à jour (200), 404 si introuvable, 400 si statut incorrect
     */
    @PutMapping("/{id}/decision-medicale")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> decisionMedicale(
            @PathVariable Long id,
            @RequestBody MedicalDecisionRequest decision,
            Authentication auth) {
        log.info("[Conges] PUT /{}/decision-medicale", id);
        try {
            return ResponseEntity.ok(demandeService.decisionMedicale(id, decision, auth));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retourne toutes les demandes de congé en attente de décision médicale — vue RH.
     * Statut Oracle correspondant : {@code EN_ETUDE_MEDICALE}.
     *
     * @return liste des demandes longue maladie en attente de validation médicale
     */
    @GetMapping("/en-attente-medicale")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getEnAttenteMedicale() {
        try {
            return ResponseEntity.ok(demandeService.getCongesParStatut("EN_ETUDE_MEDICALE"));
        } catch (Exception e) {
            log.error("getEnAttenteMedicale failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Annule une demande de congé par son identifiant.
     * Le statut Oracle est mis à {@code ANNULE}.
     *
     * @param id identifiant de la demande de congé à annuler
     * @return la demande annulée (200) ou 404 si introuvable
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> annulerConge(@PathVariable Long id) {
        log.info("[Conges] DELETE /{}", id);
        try {
            return ResponseEntity.ok(demandeService.annulerDemande(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Retourne toutes les demandes de congé avec les informations complètes de l'employé
     * et du département — vue dédiée à l'administration RH.
     * <p>
     * Utilise une requête SQL native joignant LEAVE_REQUESTS, EMPLOYEES et DEPARTMENTS.
     *
     * @return liste de DTO congés enrichis, triée par date de création décroissante
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<Map<String, Object>>> getAllCongesAdmin() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT lr.REQUEST_ID, lr.EMPLOYEE_ID, lr.LEAVE_TYPE_ID, " +
                "       lr.START_DATE, lr.END_DATE, lr.DAYS_COUNT, lr.REASON, " +
                "       lr.STATUS, lr.CREATED_AT, lr.APPROVED_BY_RH_NAME, " +
                "       lr.APPROVED_AT_RH, lr.REJECTION_REASON, " +
                "       e.FIRST_NAME, e.LAST_NAME, e.PHOTO_URL, " +
                "       d.NAME AS DEPT_NAME " +
                "FROM GERAI.LEAVE_REQUESTS lr " +
                "JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID " +
                "LEFT JOIN GERAI.DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID " +
                "ORDER BY lr.CREATED_AT DESC"
            );
            return ResponseEntity.ok(rows.stream().map(this::toCongeAdminDto).toList());
        } catch (Exception e) {
            log.error("getAllCongesAdmin failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Met à jour le statut d'une demande de congé depuis la vue admin RH.
     * <p>
     * Traduit le statut frontend (APPROUVE, REJETE) en valeurs Oracle (VALIDE_RH, REFUSE).
     * Met à jour LEAVE_REQUESTS via JDBC et retourne la ligne mise à jour enrichie.
     *
     * @param id   identifiant de la demande de congé (LEAVE_REQUESTS.REQUEST_ID)
     * @param body map contenant {@code statut} et optionnellement {@code commentaire}
     * @param auth contexte d'authentification du gestionnaire RH
     * @return la demande mise à jour sous forme de DTO admin (200), 404 ou 500 en cas d'erreur
     */
    @PutMapping("/admin/{id}")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<Map<String, Object>> updateCongeAdmin(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        try {
            String frontStatut = String.valueOf(body.getOrDefault("statut", "EN_ATTENTE"));
            String dbStatus = switch (frontStatut) {
                case "APPROUVE" -> "VALIDE_RH";
                case "REJETE"   -> "REFUSE";
                default         -> frontStatut; // EN_ATTENTE, ANNULE pass through
            };
            String commentaire = (String) body.get("commentaire");
            String approvePar  = auth != null ? auth.getName() : (String) body.get("approvePar");

            jdbcTemplate.update(
                "UPDATE GERAI.LEAVE_REQUESTS SET STATUS = ?, REJECTION_REASON = ?, " +
                "APPROVED_BY_RH_NAME = ?, APPROVED_AT_RH = SYSTIMESTAMP " +
                "WHERE REQUEST_ID = ?",
                dbStatus, commentaire, approvePar, id
            );

            // Return the updated row
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT lr.REQUEST_ID, lr.EMPLOYEE_ID, lr.LEAVE_TYPE_ID, " +
                "       lr.START_DATE, lr.END_DATE, lr.DAYS_COUNT, lr.REASON, " +
                "       lr.STATUS, lr.CREATED_AT, lr.APPROVED_BY_RH_NAME, " +
                "       lr.APPROVED_AT_RH, lr.REJECTION_REASON, " +
                "       e.FIRST_NAME, e.LAST_NAME, e.PHOTO_URL, " +
                "       d.NAME AS DEPT_NAME " +
                "FROM GERAI.LEAVE_REQUESTS lr " +
                "JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID " +
                "LEFT JOIN GERAI.DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID " +
                "WHERE lr.REQUEST_ID = ?", id
            );
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(toCongeAdminDto(rows.get(0)));
        } catch (Exception e) {
            log.error("updateCongeAdmin failed for id={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Transforme une ligne SQL brute (résultat JOIN LEAVE_REQUESTS + EMPLOYEES + DEPARTMENTS)
     * en DTO compatible avec le frontend Angular.
     * <p>
     * Normalise les dates en format {@code yyyy-MM-dd} et traduit le statut Oracle en
     * valeur frontend (APPROUVE, REJETE, EN_ATTENTE).
     *
     * @param r map des colonnes SQL brutes de la requête admin
     * @return DTO formaté pour le composant Angular {@code CongesAdminComponent}
     */
    private Map<String, Object> toCongeAdminDto(Map<String, Object> r) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id",          r.get("REQUEST_ID"));
        dto.put("employeId",   r.get("EMPLOYEE_ID"));
        dto.put("employeNom",  r.getOrDefault("FIRST_NAME", "") + " " + r.getOrDefault("LAST_NAME", ""));
        dto.put("employePhoto",r.get("PHOTO_URL"));
        dto.put("departement", r.get("DEPT_NAME"));
        dto.put("typeConge",   mapLeaveTypeId(r.get("LEAVE_TYPE_ID")));
        dto.put("dateDebut",   r.get("START_DATE")    != null ? r.get("START_DATE").toString().substring(0, 10) : null);
        dto.put("dateFin",     r.get("END_DATE")      != null ? r.get("END_DATE").toString().substring(0, 10)   : null);
        dto.put("nombreJours", r.get("DAYS_COUNT"));
        dto.put("dureeType",   "JOURNEE_COMPLETE");
        dto.put("statut",      mapDbStatus(String.valueOf(r.getOrDefault("STATUS", "EN_ATTENTE"))));
        dto.put("raison",      r.get("REASON"));
        dto.put("dateDemande", r.get("CREATED_AT")    != null ? r.get("CREATED_AT").toString().substring(0, 10)    : null);
        dto.put("approvePar",  r.get("APPROVED_BY_RH_NAME"));
        dto.put("dateApprobation", r.get("APPROVED_AT_RH") != null ? r.get("APPROVED_AT_RH").toString().substring(0, 10) : null);
        dto.put("commentaire", r.get("REJECTION_REASON"));
        return dto;
    }

    /**
     * Traduit l'identifiant numérique du type de congé Oracle en code textuel
     * compréhensible par le frontend Angular.
     *
     * @param id identifiant du type de congé (REF_TYPES_CONGE.TYPE_ID)
     * @return code textuel du type de congé (ex : {@code ANNUEL}, {@code MALADIE})
     */
    private String mapLeaveTypeId(Object id) {
        if (id == null) return "ANNUEL";
        return switch (((Number) id).intValue()) {
            case 1  -> "ANNUEL";
            case 2  -> "MALADIE";
            case 3  -> "RTT";
            case 4  -> "SANS_SOLDE";
            case 5  -> "MATERNITE";
            case 6  -> "NAISSANCE_PERE";
            case 7  -> "EXCEPTIONNEL";
            case 8  -> "FAMILIAL";
            case 9  -> "HAJJ";
            case 10 -> "POSTNATAL";
            case 11 -> "ALLAITEMENT";
            case 12 -> "LONGUE_MALADIE";
            case 13 -> "NAISSANCE_PERE";
            case 14 -> "CREATION_ENTREPRISE";
            case 15 -> "OBLIGATIONS_LEGALES";
            default -> "ANNUEL";
        };
    }

    /**
     * Traduit le statut Oracle de la table LEAVE_REQUESTS en valeur compréhensible
     * par le frontend Angular ({@code APPROUVE}, {@code REJETE}, etc.).
     *
     * @param dbStatus valeur brute du champ STATUS en base Oracle
     * @return code statut normalisé pour le frontend
     */
    private String mapDbStatus(String dbStatus) {
        return switch (dbStatus) {
            case "VALIDE_CHEF" -> "EN_ATTENTE";
            case "VALIDE_RH"   -> "APPROUVE";
            case "REFUSE"      -> "REJETE";
            default            -> dbStatus; // EN_ATTENTE, ANNULE pass through
        };
    }
}
