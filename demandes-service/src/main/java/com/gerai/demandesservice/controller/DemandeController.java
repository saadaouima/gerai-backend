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

    /** Toutes les demandes de l'équipe (toutes statuts) */
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

    /** Uniquement les demandes en attente de validation Chef */
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

    @GetMapping("/toutes")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getToutesDemandes() {
        return ResponseEntity.ok(demandeService.getToutesDemandes());
    }

    /** Demandes en attente de la 2ème validation (Chef déjà validé → RH) */
    @GetMapping("/en-attente-rh")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getDemandesEnAttenteRh() {
        return ResponseEntity.ok(demandeService.getDemandesEnAttenteRh());
    }

    /* ══════════════════════════════════════════════════════════
       TOUS RÔLES — Lire une demande par ID
       ══════════════════════════════════════════════════════════ */

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
     * Valide ou rejette une demande de CONGÉ.
     * PUT /api/demandes/conge/{id}/valider
     * Body : { "nouveauStatut": "VALIDEE_CHEF", "commentaire": "OK" }
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

    @PutMapping("/formation/{id}/valider")
    @PreAuthorize("hasAnyRole('CHEF','RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerFormation(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        return ResponseEntity.ok(
                demandeService.valider(id, TypeDemande.FORMATION, validation, auth));
    }

    @PutMapping("/pret/{id}/valider")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerPret(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        return ResponseEntity.ok(
                demandeService.valider(id, TypeDemande.PRET, validation, auth));
    }

    @PutMapping("/document/{id}/valider")
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<DemandeResponse> validerDocument(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequest validation,
            Authentication auth) {
        return ResponseEntity.ok(
                demandeService.valider(id, TypeDemande.DOCUMENT, validation, auth));
    }

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
     * Crédits en attente de décision DG (statut EN_ETUDE_DG).
     * GET /api/demandes/credit/en-attente-dg
     */
    @GetMapping("/credit/en-attente-dg")
    @PreAuthorize("hasAnyRole('DIRECTEUR_GENERAL','ADMIN','RH','ADMIN_RH')")
    public ResponseEntity<List<DemandeResponse>> getCreditsEnAttenteDg() {
        return ResponseEntity.ok(demandeService.getCreditsEnAttenteDg());
    }

    /**
     * Historique complet des crédits (tous statuts DG).
     * GET /api/demandes/credit/all
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