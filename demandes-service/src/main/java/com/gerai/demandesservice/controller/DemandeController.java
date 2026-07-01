package com.gerai.demandesservice.controller;

import com.gerai.demandesservice.dto.DemandeRequest;
import com.gerai.demandesservice.dto.DemandeResponse;
import com.gerai.demandesservice.dto.DgDecisionRequest;
import com.gerai.demandesservice.dto.ValidationRequest;
import com.gerai.demandesservice.model.StatutDemande;
import com.gerai.demandesservice.model.TypeDemande;
import com.gerai.demandesservice.service.DemandeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

import java.util.List;

/**
 * Controller REST du demandes-service GerAI.
 * Base URL : /api/demandes
 *
 * Endpoints par rôle :
 *   EMPLOYE → POST /, GET /mes-demandes
 *   CHEF    → GET /equipe, GET /equipe/en-attente,
 *             PUT /{type}/{id}/valider
 *   RH/ADMIN→ GET /toutes, GET /en-attente-rh,
 *             PUT /{type}/{id}/valider
 */
@Slf4j
@RestController
@RequestMapping("/api/demandes")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class DemandeController {

    private final DemandeService demandeService;

    /* ══════════════════════════════════════════════════════════
       EMPLOYÉ — Créer une demande
       ══════════════════════════════════════════════════════════ */

    /**
     * Crée une demande RH selon le type.
     * L'employee_id est résolu depuis le JWT (sub Keycloak → EMPLOYEES.user_id).
     *
     * Exemples de body JSON :
     *
     * CONGÉ :
     * { "type":"CONGE", "leaveTypeId":1, "startDate":"2026-05-01",
     *   "endDate":"2026-05-05", "daysCount":5, "reason":"Vacances" }
     *
     * FORMATION :
     * { "type":"FORMATION", "trainingTitle":"Spring Boot", "provider":"Interne",
     *   "estimatedCost":500, "plannedDate":"2026-06-01", "durationDays":3 }
     *
     * PRÊT :
     * { "type":"PRET", "amount":5000, "currency":"TND", "durationMonths":24 }
     *
     * DOCUMENT :
     * { "type":"DOCUMENT", "docTypeId":1, "copiesCount":2, "language":"FR" }
     *
     * AUTORISATION :
     * { "type":"AUTORISATION", "startDatetime":"2026-04-15T08:00:00",
     *   "endDatetime":"2026-04-15T12:00:00", "reason":"Rendez-vous médical" }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> creerDemande(
            @Valid @RequestBody DemandeRequest request,
            Authentication auth) {
        log.info("[Controller] POST /api/demandes | type={}", request.getType());
        DemandeResponse response = demandeService.creerDemande(request, auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /* ══════════════════════════════════════════════════════════
       EMPLOYÉ — Lire ses propres demandes
       ══════════════════════════════════════════════════════════ */

    /**
     * Retourne toutes les demandes RH de l'employé connecté (toutes catégories).
     *
     * @param auth contexte d'authentification de l'employé connecté
     * @return liste des demandes de l'employé triée par date décroissante, liste vide en cas d'erreur
     */
    @GetMapping("/mes-demandes")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getMesDemandes(Authentication auth) {
        try {
            return ResponseEntity.ok(demandeService.getMesDemandes(auth));
        } catch (Exception e) {
            log.error("getMesDemandes failed: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /* ══════════════════════════════════════════════════════════
       CHEF — Demandes de son équipe
       ══════════════════════════════════════════════════════════ */

    /**
     * Retourne toutes les demandes RH de l'équipe du chef connecté (tous statuts confondus).
     * La résolution de l'équipe se fait via EMPLOYEES.MANAGER_ID, avec fallback par département.
     *
     * @param auth contexte d'authentification du chef de service
     * @return liste des demandes de l'équipe, ou 422 si l'identité du chef ne peut être résolue
     */
    @GetMapping("/equipe")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getDemandesEquipe(Authentication auth) {
        try {
            return ResponseEntity.ok(demandeService.getDemandesEquipe(auth));
        } catch (IllegalStateException e) {
            log.error("getDemandesEquipe: identité non résolue: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (Exception e) {
            log.error("getDemandesEquipe failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retourne uniquement les demandes en attente de validation par le chef connecté (statut {@code EN_ATTENTE}).
     *
     * @param auth contexte d'authentification du chef de service
     * @return liste des demandes en attente de l'équipe, ou code d'erreur si identité non résolue
     */
    @GetMapping("/equipe/en-attente")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getDemandesEnAttenteChef(Authentication auth) {
        try {
            return ResponseEntity.ok(demandeService.getDemandesEnAttenteChef(auth));
        } catch (IllegalStateException e) {
            log.error("getDemandesEnAttenteChef: identité non résolue: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (Exception e) {
            log.error("getDemandesEnAttenteChef failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ══════════════════════════════════════════════════════════
       RH / ADMIN — Toutes les demandes
       ══════════════════════════════════════════════════════════ */

    /**
     * Retourne l'ensemble des demandes RH toutes catégories confondues — vue globale RH/Admin.
     *
     * @return liste complète de toutes les demandes, triée par date de création décroissante
     */
    @GetMapping("/toutes")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getToutesDemandes() {
        return ResponseEntity.ok(demandeService.getToutesDemandes());
    }

    /**
     * Retourne les demandes en attente de la 2ème validation (chef déjà validé → en attente RH).
     * Rassemble les demandes à statut intermédiaire pour chaque type :
     * {@code VALIDE_CHEF} (congé), {@code APPROUVE_CHEF} (formation), {@code EN_ATTENTE} (crédit, document).
     *
     * @return liste des demandes en attente de validation RH, toutes catégories
     */
    @GetMapping("/en-attente-rh")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getDemandesEnAttenteRh() {
        return ResponseEntity.ok(demandeService.getDemandesEnAttenteRh());
    }

    /* ══════════════════════════════════════════════════════════
       TOUS RÔLES — Lire une demande par ID
       ══════════════════════════════════════════════════════════ */

    /**
     * Retourne une demande RH par son identifiant, quel que soit son type.
     * La recherche est effectuée successivement dans les 5 tables de demandes.
     *
     * @param id identifiant de la demande
     * @return la demande trouvée (200) ou 404 si aucune demande ne correspond à cet identifiant
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> getDemandeById(@PathVariable Long id) {
        Optional<DemandeResponse> result = demandeService.getDemandeById(id);
        return result.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    /* ══════════════════════════════════════════════════════════
       EMPLOYÉ — Annuler sa propre demande
       ══════════════════════════════════════════════════════════ */

    /**
     * Annule une demande RH par son identifiant.
     * Le statut Oracle est mis à {@code ANNULE} dans la table correspondante.
     *
     * @param id identifiant de la demande à annuler
     * @return la demande annulée (200) ou 404 si introuvable
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYE','CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> annulerDemande(@PathVariable Long id) {
        log.info("[Controller] DELETE /api/demandes/{}", id);
        try {
            return ResponseEntity.ok(demandeService.annulerDemande(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /* ══════════════════════════════════════════════════════════
       CHEF & RH — Validation générique (sans type dans l'URL)
       ══════════════════════════════════════════════════════════ */

    /**
     * Valide ou rejette une demande RH de manière générique (sans préciser le type dans l'URL).
     * Le service auto-détecte le type de la demande et applique le workflow métier adéquat.
     *
     * @param id         identifiant de la demande à traiter
     * @param validation nouveau statut souhaité et commentaire optionnel
     * @param auth       contexte d'authentification du valideur (Chef ou RH)
     * @return la demande mise à jour (200) ou 404 si introuvable
     */
    @PutMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerDemande(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        log.info("[Controller] PUT /api/demandes/{}/valider | statut={}", id, validation.getNouveauStatut());
        try {
            return ResponseEntity.ok(demandeService.validerGenerique(id, validation, auth));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Rejette une demande RH avec un motif optionnel.
     * Construit un {@link ValidationRequest} avec le statut {@code REJETEE} et délègue à {@code validerGenerique}.
     *
     * @param id   identifiant de la demande à rejeter
     * @param body map optionnelle contenant le champ {@code motif} ou {@code commentaire}
     * @param auth contexte d'authentification du refuseur
     * @return la demande rejetée (200) ou 404 si introuvable
     */
    @PutMapping("/{id}/refuser")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> refuserDemande(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        log.info("[Controller] PUT /api/demandes/{}/refuser", id);
        String motif = body != null ? body.getOrDefault("motif", body.get("commentaire")) : null;
        ValidationRequest validation = ValidationRequest.builder()
                .nouveauStatut(StatutDemande.REJETEE)
                .commentaire(motif)
                .build();
        try {
            return ResponseEntity.ok(demandeService.validerGenerique(id, validation, auth));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /* ══════════════════════════════════════════════════════════
       CHEF — Valider / Refuser via URL chef/{chefId}/{id}/valider
       ══════════════════════════════════════════════════════════ */

    /**
     * Permet au chef d'approuver ou de rejeter une demande de son équipe
     * via une URL incluant son propre identifiant.
     * <p>
     * Le paramètre {@code approuve} détermine le statut cible :
     * {@code true} → {@code VALIDEE_CHEF}, {@code false} → {@code REJETEE}.
     *
     * @param chefId     identifiant Oracle du chef (non utilisé directement, l'auth JWT prime)
     * @param id         identifiant de la demande à traiter
     * @param approuve   {@code true} pour approuver, {@code false} pour rejeter
     * @param commentaire commentaire ou motif de refus (optionnel)
     * @param auth       contexte d'authentification du chef
     * @return la demande mise à jour (200) ou 404 si introuvable
     */
    @PutMapping("/chef/{chefId}/{id}/valider")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> chefValiderDemande(
            @PathVariable Long chefId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean approuve,
            @RequestParam(required = false) String commentaire,
            Authentication auth) {
        log.info("[Controller] PUT /api/demandes/chef/{}/{}/valider | approuve={}", chefId, id, approuve);
        com.gerai.demandesservice.model.StatutDemande statut = approuve
                ? StatutDemande.VALIDEE_CHEF
                : StatutDemande.REJETEE;
        ValidationRequest validation = ValidationRequest.builder()
                .nouveauStatut(statut)
                .commentaire(commentaire)
                .build();
        try {
            return ResponseEntity.ok(demandeService.validerGenerique(id, validation, auth));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /* ══════════════════════════════════════════════════════════
       CHEF & RH — Valider / Rejeter une demande
       Le type de demande est passé en path variable pour que le
       service sache sur quelle table Oracle appliquer la mise à jour.
       ══════════════════════════════════════════════════════════ */

    /**
     * Valide ou rejette une demande de congé (type {@code CONGE}).
     * {@code PUT /api/demandes/conge/{id}/valider}
     * Corps attendu : {@code { "nouveauStatut": "VALIDEE_CHEF", "commentaire": "OK" }}.
     *
     * @param id         identifiant de la demande de congé
     * @param validation nouveau statut souhaité et commentaire optionnel
     * @param auth       contexte d'authentification du valideur (Chef ou RH)
     * @return la demande de congé mise à jour (200)
     */
    @PutMapping("/conge/{id}/valider")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerConge(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        return ResponseEntity.ok(
                demandeService.valider(id, TypeDemande.CONGE, validation, auth));
    }

    /**
     * Valide ou rejette une demande de formation (type {@code FORMATION}).
     *
     * @param id         identifiant de la demande de formation
     * @param validation nouveau statut et commentaire
     * @param auth       contexte d'authentification du valideur
     * @return la demande de formation mise à jour (200)
     */
    @PutMapping("/formation/{id}/valider")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerFormation(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        return ResponseEntity.ok(
                demandeService.valider(id, TypeDemande.FORMATION, validation, auth));
    }

    /**
     * Valide ou rejette une demande de prêt/crédit (type {@code PRET}).
     * Seuls les rôles RH et ADMIN peuvent agir sur un crédit à cette étape.
     *
     * @param id         identifiant de la demande de prêt
     * @param validation nouveau statut et commentaire
     * @param auth       contexte d'authentification du gestionnaire RH
     * @return la demande de prêt mise à jour (200)
     */
    @PutMapping("/pret/{id}/valider")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerPret(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        return ResponseEntity.ok(
                demandeService.valider(id, TypeDemande.PRET, validation, auth));
    }

    /**
     * Valide ou rejette une demande de document administratif (type {@code DOCUMENT}).
     *
     * @param id         identifiant de la demande de document
     * @param validation nouveau statut et commentaire
     * @param auth       contexte d'authentification du gestionnaire RH
     * @return la demande de document mise à jour (200)
     */
    @PutMapping("/document/{id}/valider")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerDocument(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        return ResponseEntity.ok(
                demandeService.valider(id, TypeDemande.DOCUMENT, validation, auth));
    }

    /**
     * Valide ou rejette une demande d'autorisation d'absence (type {@code AUTORISATION}).
     * Le flux est direct : la validation chef donne statut {@code APPROUVE} final.
     *
     * @param id         identifiant de la demande d'autorisation
     * @param validation nouveau statut et commentaire
     * @param auth       contexte d'authentification du valideur
     * @return la demande d'autorisation mise à jour (200)
     */
    @PutMapping("/autorisation/{id}/valider")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerAutorisation(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        return ResponseEntity.ok(
                demandeService.valider(id, TypeDemande.AUTORISATION, validation, auth));
    }

    /* ══════════════════════════════════════════════════════════
       DIRECTEUR GÉNÉRAL — Décision finale sur un crédit
       ══════════════════════════════════════════════════════════ */

    /**
     * Retourne les crédits en attente de décision finale du Directeur Général
     * (statut Oracle {@code EN_ETUDE_DG}).
     * {@code GET /api/demandes/credit/en-attente-dg}
     *
     * @return liste des demandes de crédit transmises au DG, triées par date décroissante
     */
    @GetMapping("/credit/en-attente-dg")
    @PreAuthorize("hasAnyRole('DIRECTEUR_GENERAL','ADMIN','RH','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getCreditsEnAttenteDg() {
        return ResponseEntity.ok(demandeService.getCreditsEnAttenteDg());
    }

    /**
     * Retourne l'historique complet des crédits (tous statuts DG confondus) :
     * {@code EN_ETUDE_DG}, {@code VALIDEE_DG}, {@code APPROUVE}, {@code REFUSE}.
     * {@code GET /api/demandes/credit/all}
     *
     * @return liste complète des demandes de crédit traitées ou en cours d'étude
     */
    @GetMapping("/credit/all")
    @PreAuthorize("hasAnyRole('DIRECTEUR_GENERAL','ADMIN','RH','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getAllCredits() {
        return ResponseEntity.ok(demandeService.getAllCredits());
    }

    /**
     * Décision DG : approuver (avec montant et tranches) ou refuser.
     * PUT /api/demandes/credit/{id}/decision-dg
     * Body : { "approuve": true, "montantApprouve": 8000, "nbTranches": 24, "commentaire": "OK" }
     */
    @PutMapping("/credit/{id}/decision-dg")
    @PreAuthorize("hasAnyRole('DIRECTEUR_GENERAL','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> dgDecision(
            @PathVariable Long id,
            @RequestBody DgDecisionRequest decision,
            Authentication auth) {
        log.info("[Controller] PUT /api/demandes/credit/{}/decision-dg | approuve={}", id, decision.isApprouve());
        try {
            return ResponseEntity.ok(demandeService.decisionDg(id, decision, auth));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}