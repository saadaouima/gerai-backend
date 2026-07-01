package com.gerai.demandesservice.controller;

import com.gerai.demandesservice.dto.ComiteVoteRequest;
import com.gerai.demandesservice.dto.ComiteVoteResponse;
import com.gerai.demandesservice.dto.DemandeResponse;
import com.gerai.demandesservice.service.ComiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST gérant les opérations du comité de crédit (comité de prêt).
 * <p>
 * {@code @RestController} : toutes les méthodes retournent du JSON directement.
 * <p>
 * {@code @RequestMapping("/api/demandes/comite")} : préfixe commun à tous les endpoints.
 * <p>
 * {@code @PreAuthorize} : restreint l'accès aux rôles COMITE, DIRECTEUR_GENERAL, ADMIN, RH, ADMIN_RH.
 * Les membres du comité peuvent voter ; les autres rôles peuvent uniquement consulter.
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/demandes/comite")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class ComiteController {

    /** Service métier orchestrant le workflow de vote du comité de crédit. */
    private final ComiteService comiteService;

    /**
     * Retourne la liste des crédits en attente de vote par le comité (statut {@code EN_ETUDE_DG}).
     *
     * @return liste des demandes de prêt en cours d'étude avec statut HTTP 200
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('DIRECTEUR_GENERAL','ADMIN','RH','ADMIN_RH','COMITE')")
    public ResponseEntity<List<DemandeResponse>> getPending() {
        return ResponseEntity.ok(comiteService.getPendingLoans());
    }

    /**
     * Retourne l'historique complet de tous les crédits traités ou en cours.
     *
     * @return liste de toutes les demandes de prêt avec statut HTTP 200
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('DIRECTEUR_GENERAL','ADMIN','RH','ADMIN_RH','COMITE')")
    public ResponseEntity<List<DemandeResponse>> getAll() {
        return ResponseEntity.ok(comiteService.getAllLoans());
    }

    /**
     * Retourne les votes existants enregistrés pour un crédit donné.
     *
     * @param loanId identifiant du crédit (LOAN_REQUESTS.REQUEST_ID)
     * @return liste des votes avec statut HTTP 200
     */
    @GetMapping("/{loanId}/votes")
    @PreAuthorize("hasAnyRole('DIRECTEUR_GENERAL','ADMIN','RH','ADMIN_RH','COMITE')")
    public ResponseEntity<List<ComiteVoteResponse>> getVotes(@PathVariable Long loanId) {
        return ResponseEntity.ok(comiteService.getVotesForLoan(loanId));
    }

    /**
     * Soumet le vote d'un membre du comité pour un crédit donné.
     * <p>
     * Un seul vote par membre et par crédit est autorisé.
     * Lorsque le seuil de votes favorables ou défavorables est atteint,
     * le service applique automatiquement la décision collective.
     *
     * @param loanId identifiant du crédit à voter
     * @param req    données du vote (FAVORABLE ou DEFAVORABLE, commentaire, montant suggéré)
     * @param auth   contexte d'authentification Spring Security (JWT Keycloak) du votant
     * @return le vote enregistré avec statut HTTP 200
     */
    @PostMapping("/{loanId}/vote")
    @PreAuthorize("hasAnyRole('DIRECTEUR_GENERAL','ADMIN','ADMIN_RH','COMITE')")
    public ResponseEntity<ComiteVoteResponse> castVote(
            @PathVariable Long loanId,
            @RequestBody ComiteVoteRequest req,
            Authentication auth) {
        log.info("[Comite] POST /comite/{}/vote | vote={}", loanId, req.getVote());
        return ResponseEntity.ok(comiteService.castVote(loanId, req, auth));
    }
}
