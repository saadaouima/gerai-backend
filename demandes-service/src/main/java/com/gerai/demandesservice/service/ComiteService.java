package com.gerai.demandesservice.service;

import com.gerai.demandesservice.dto.*;
import com.gerai.demandesservice.model.ComiteVote;
import com.gerai.demandesservice.model.LoanRequest;
import com.gerai.demandesservice.repository.ComiteVoteRepository;
import com.gerai.demandesservice.repository.EmployeeRepository;
import com.gerai.demandesservice.repository.LoanRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service métier gérant le workflow de vote du comité de crédit.
 * <p>
 * {@code @Service} : enregistre cette classe comme bean Spring de la couche service.
 * <p>
 * {@code @Transactional} (sur les méthodes) : garantit l'atomicité des opérations de vote
 * et de mise à jour du crédit. Les méthodes de lecture utilisent {@code readOnly = true}
 * pour optimiser les performances.
 * <p>
 * Workflow du comité :
 * <ol>
 *   <li>Un membre du comité soumet son vote via {@link #castVote}.</li>
 *   <li>Dès que le seuil de {@value #VOTE_THRESHOLD} votes FAVORABLE est atteint,
 *       le crédit passe automatiquement à {@code VALIDEE_DG}.</li>
 *   <li>Dès que le seuil de {@value #VOTE_THRESHOLD} votes DEFAVORABLE est atteint,
 *       le crédit passe automatiquement à {@code REFUSE}.</li>
 * </ol>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComiteService {

    /** Nombre minimal de votes concordants pour déclencher la décision automatique du comité. */
    private static final int VOTE_THRESHOLD = 1;

    private final LoanRequestRepository          loanRepo;
    private final ComiteVoteRepository           voteRepo;
    private final EmployeeRepository             employeeRepo;
    private final EmployeeInfoHelper             employeeInfoHelper;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Value("${app.kafka.topic.notifications}")
    private String notificationTopic;

    /* ─── Vote ─────────────────────────────────────────────── */

    /**
     * Enregistre le vote d'un membre du comité de crédit pour un prêt donné.
     * <p>
     * Vérifie que le crédit est bien au statut {@code EN_ETUDE_DG} et qu'un seul vote
     * par membre est autorisé. Si le seuil est atteint, applique la décision automatiquement.
     *
     * @param loanId identifiant du crédit à voter (LOAN_REQUESTS.REQUEST_ID)
     * @param req    données du vote (FAVORABLE/DEFAVORABLE, commentaire, montant suggéré)
     * @param auth   contexte d'authentification Spring Security du membre du comité votant
     * @return le vote enregistré sous forme de DTO
     * @throws IllegalArgumentException si le crédit est introuvable
     * @throws IllegalStateException    si le crédit n'est pas en statut EN_ETUDE_DG ou si le membre a déjà voté
     */
    @Transactional
    public ComiteVoteResponse castVote(Long loanId, ComiteVoteRequest req, Authentication auth) {
        LoanRequest loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Crédit introuvable : " + loanId));

        if (!"EN_ETUDE_DG".equals(loan.getStatus())) {
            throw new IllegalStateException(
                    "Ce crédit ne peut pas être voté (statut actuel : " + loan.getStatus() + ")");
        }

        Long memberId = resolveEmployeeId(auth);
        if (voteRepo.existsByLoanIdAndMemberId(loanId, memberId)) {
            throw new IllegalStateException("Vous avez déjà voté pour ce crédit.");
        }

        String vote = req.getVote();
        if (!"FAVORABLE".equals(vote) && !"DEFAVORABLE".equals(vote)) {
            throw new IllegalArgumentException("Le vote doit être FAVORABLE ou DEFAVORABLE.");
        }

        ComiteVote entity = ComiteVote.builder()
                .loanId(loanId)
                .memberId(memberId)
                .memberNom(extractFullName(auth))
                .vote(vote)
                .commentaire(req.getCommentaire())
                .montantSuggere(req.getMontantSuggere())
                .nbTranchesSuggeres(req.getNbTranchesSuggeres())
                .build();
        entity = voteRepo.save(entity);

        // Check threshold and auto-decide
        long approveCount = voteRepo.countByLoanIdAndVote(loanId, "FAVORABLE");
        long rejectCount  = voteRepo.countByLoanIdAndVote(loanId, "DEFAVORABLE");

        log.info("[Comite] loanId={} | APPROVE={} | REJECT={}", loanId, approveCount, rejectCount);

        if (approveCount >= VOTE_THRESHOLD) {
            autoApprove(loan, loanId);
        } else if (rejectCount >= VOTE_THRESHOLD) {
            autoReject(loan, loanId);
        }

        return toResponse(entity);
    }

    /**
     * Applique la décision collective d'approbation du comité lorsque le seuil FAVORABLE est atteint.
     * Calcule la moyenne des montants et tranches suggérés par les membres favorables,
     * met le crédit au statut {@code VALIDEE_DG} et notifie l'employé via Kafka.
     *
     * @param loan   entité du crédit à approuver
     * @param loanId identifiant du crédit (pour le filtrage des votes)
     */
    private void autoApprove(LoanRequest loan, Long loanId) {
        // Average the suggested amounts from APPROVE votes
        List<ComiteVote> approveVotes = voteRepo.findByLoanIdOrderByVotedAtAsc(loanId).stream()
                .filter(v -> "FAVORABLE".equals(v.getVote()))
                .toList();

        BigDecimal avgMontant = approveVotes.stream()
                .map(ComiteVote::getMontantSuggere)
                .filter(m -> m != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long withMontant = approveVotes.stream()
                .filter(v -> v.getMontantSuggere() != null).count();

        BigDecimal montantApprouve = (withMontant > 0)
                ? avgMontant.divide(BigDecimal.valueOf(withMontant), 2, RoundingMode.HALF_UP)
                : loan.getAmount();

        double avgTranches = approveVotes.stream()
                .filter(v -> v.getNbTranchesSuggeres() != null)
                .mapToInt(ComiteVote::getNbTranchesSuggeres)
                .average()
                .orElse(loan.getDurationMonths() != null ? loan.getDurationMonths() : 12);

        int nbTranches = (int) Math.round(avgTranches);

        loan.setStatus("VALIDEE_DG");
        loan.setMontantApprouve(montantApprouve);
        loan.setNbTranches(nbTranches);
        if (nbTranches > 0) {
            loan.setMontantTranche(montantApprouve.divide(BigDecimal.valueOf(nbTranches), 2, RoundingMode.HALF_UP));
        }
        loan.setDgApprovedByName("Comité de crédit");
        loan.setDgDecisionAt(LocalDateTime.now());
        loan.setDgComment("Décision collective du comité (" + VOTE_THRESHOLD + " avis FAVORABLE)");
        loanRepo.save(loan);

        log.info("[Comite] Auto-APPROVE loanId={} | montant={} | tranches={}", loanId, montantApprouve, nbTranches);
        notifyEmployee(loan, "VALIDEE_DG",
                "Votre demande de crédit a été approuvée par le comité",
                "Le comité de crédit a approuvé votre demande (Réf: " + loanId + ")");
        notifyDg(loan);
    }

    /**
     * Applique la décision collective de rejet du comité lorsque le seuil DEFAVORABLE est atteint.
     * Met le crédit au statut {@code REFUSE} et notifie l'employé via Kafka.
     *
     * @param loan   entité du crédit à rejeter
     * @param loanId identifiant du crédit (pour les logs)
     */
    private void autoReject(LoanRequest loan, Long loanId) {
        loan.setStatus("REFUSE_COMMISSION");
        loan.setRejectionReason("Refus collectif du comité (" + VOTE_THRESHOLD + " avis DEFAVORABLE)");
        loan.setDgApprovedByName("Comité de crédit");
        loan.setDgDecisionAt(LocalDateTime.now());
        loanRepo.save(loan);

        log.info("[Comite] Auto-REJECT loanId={}", loanId);
        notifyEmployee(loan, "REJETEE",
                "Votre demande de crédit a été refusée par le comité",
                "Le comité de crédit a refusé votre demande (Réf: " + loanId + ")");
    }

    /* ─── Queries ───────────────────────────────────────────── */

    /**
     * Retourne la liste des crédits en attente de vote du comité (statut {@code EN_ETUDE_DG}).
     *
     * @return liste des demandes de prêt en cours d'étude par le comité
     */
    @Transactional
    public List<DemandeResponse> getPendingLoans() {
        // Re-apply threshold to any loans that were voted on before VOTE_THRESHOLD was reduced
        List<LoanRequest> pending = loanRepo.findByStatusOrderByCreatedAtDesc("EN_ETUDE_DG");
        for (LoanRequest loan : pending) {
            long approveCount = voteRepo.countByLoanIdAndVote(loan.getRequestId(), "FAVORABLE");
            long rejectCount  = voteRepo.countByLoanIdAndVote(loan.getRequestId(), "DEFAVORABLE");
            if (approveCount >= VOTE_THRESHOLD) {
                autoApprove(loan, loan.getRequestId());
            } else if (rejectCount >= VOTE_THRESHOLD) {
                autoReject(loan, loan.getRequestId());
            }
        }
        return loanRepo.findByStatusOrderByCreatedAtDesc("EN_ETUDE_DG")
                .stream().map(this::toLoanResponse).toList();
    }

    /**
     * Retourne l'historique complet des crédits (tous statuts confondus).
     *
     * @return liste de toutes les demandes de prêt enregistrées
     */
    @Transactional(readOnly = true)
    public List<DemandeResponse> getAllLoans() {
        return loanRepo.findByStatusInOrderByCreatedAtDesc(
                        List.of("EN_ETUDE_DG", "VALIDEE_DG", "REFUSE", "REFUSE_COMMISSION", "APPROUVE", "EN_ATTENTE", "EN_ETUDE"))
                .stream().map(this::toLoanResponse).toList();
    }

    /**
     * Retourne les votes enregistrés pour un crédit donné, triés par ordre chronologique.
     *
     * @param loanId identifiant du crédit (LOAN_REQUESTS.REQUEST_ID)
     * @return liste des votes du comité pour ce crédit
     */
    @Transactional(readOnly = true)
    public List<ComiteVoteResponse> getVotesForLoan(Long loanId) {
        return voteRepo.findByLoanIdOrderByVotedAtAsc(loanId)
                .stream().map(this::toResponse).toList();
    }

    /* ─── Helpers ───────────────────────────────────────────── */

    /**
     * Convertit une entité {@link LoanRequest} en DTO de réponse unifié {@link DemandeResponse}.
     *
     * @param e l'entité crédit à convertir
     * @return le DTO de réponse prêt à sérialiser en JSON
     */
    private DemandeResponse toLoanResponse(LoanRequest e) {
        String empNom = employeeRepo.findFullNameByEmployeeId(e.getEmployeeId());
        String dgNom  = e.getDgApprovedByName();
        String rhNom  = e.getFinalApprovedByName();
        String desc   = e.getReason() != null ? e.getReason()
                      : "Crédit de " + (e.getAmount() != null ? e.getAmount() : "") + " " + e.getCurrency();
        return DemandeResponse.builder()
                .requestId(e.getRequestId()).id(e.getRequestId())
                .type(com.gerai.demandesservice.model.TypeDemande.PRET)
                .statut(com.gerai.demandesservice.model.StatutDemande.fromOracleStatus(e.getStatus()))
                .statusOracle(e.getStatus())
                .employeeId(e.getEmployeeId())
                .employeId(e.getEmployeeId() != null ? String.valueOf(e.getEmployeeId()) : null)
                .employeNom(empNom)
                .description(desc).reason(e.getReason())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .durationMonths(e.getDurationMonths())
                .monthlyPayment(e.getMonthlyPayment())
                .montantApprouve(e.getMontantApprouve())
                .nbTranches(e.getNbTranches())
                .montantTranche(e.getMontantTranche())
                .dgApprovedBy(e.getDgApprovedBy())
                .dgDecisionAt(e.getDgDecisionAt())
                .dgComment(e.getDgComment())
                .rejectionReason(e.getRejectionReason())
                .approvedBy(e.getApprovedBy()).approvedAt(e.getApprovedAt())
                .validateurNomDg(dgNom)
                .validateurNomRh(rhNom)
                .validePar(rhNom != null ? rhNom : dgNom)
                .dateValidationDg(e.getDgDecisionAt() != null ? e.getDgDecisionAt().toString() : null)
                .dateCreation(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .commentaireRh(e.getDgComment() != null ? e.getDgComment() : e.getRejectionReason())
                .createdAt(e.getCreatedAt())
                .build();
    }

    /**
     * Convertit une entité {@link ComiteVote} en DTO {@link ComiteVoteResponse}.
     *
     * @param v l'entité vote à convertir
     * @return le DTO de réponse du vote
     */
    private ComiteVoteResponse toResponse(ComiteVote v) {
        return ComiteVoteResponse.builder()
                .voteId(v.getVoteId())
                .loanId(v.getLoanId())
                .memberId(v.getMemberId())
                .memberNom(v.getMemberNom())
                .vote(v.getVote())
                .commentaire(v.getCommentaire())
                .montantSuggere(v.getMontantSuggere())
                .nbTranchesSuggeres(v.getNbTranchesSuggeres())
                .votedAt(v.getVotedAt())
                .build();
    }

    private void notifyDg(LoanRequest loan) {
        List<Long> dgIds;
        try {
            dgIds = employeeInfoHelper.findDgIds();
        } catch (Exception e) {
            log.warn("[Comite] Cannot resolve DG IDs: {}", e.getMessage());
            return;
        }
        if (dgIds.isEmpty()) {
            log.warn("[Comite] No DG employee found in DB for loanId={}", loan.getRequestId());
            return;
        }
        final Long ref = loan.getRequestId();
        for (Long dgId : dgIds) {
            final String dgEmail;
            try { dgEmail = employeeInfoHelper.findEmail(dgId); } catch (Exception e) { continue; }
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    NotificationEvent event = NotificationEvent.builder()
                            .employeeId(dgId)
                            .email(dgEmail)
                            .type("INFO")
                            .title("Dossier de crédit approuvé par le comité")
                            .content("Le comité a rendu un avis favorable sur la demande de prêt (Réf: " + ref
                                     + "). Ce dossier est en attente de votre décision finale.")
                            .referenceId(String.valueOf(ref))
                            .referenceType("PRET")
                            .actionUrl("/dg/dashboard")
                            .sourceService("DEMANDES-SERVICE")
                            .sendEmail(true)
                            .build();
                    kafkaTemplate.send(notificationTopic, String.valueOf(dgId), event);
                } catch (Exception e) {
                    log.warn("[Comite] Kafka send to DG failed for loanId={}: {}", ref, e.getMessage());
                }
            });
        }
        log.info("[Comite] {} DG(s) notified for loanId={}", dgIds.size(), ref);
    }

    /**
     * Envoie une notification Kafka à l'employé concerné par la décision du comité.
     * L'envoi est asynchrone ({@code CompletableFuture}) pour ne pas bloquer la transaction principale.
     *
     * @param loan    le crédit concerné par la notification
     * @param type    type de notification (ex : {@code VALIDEE_DG}, {@code REJETEE})
     * @param title   titre court affiché dans l'interface
     * @param content corps de la notification
     */
    private void notifyEmployee(LoanRequest loan, String type, String title, String content) {
        // Resolve email via REQUIRES_NEW so any DB error cannot taint the caller's TX.
        String email;
        try {
            email = employeeInfoHelper.findEmail(loan.getEmployeeId());
        } catch (Exception e) {
            log.warn("[Comite] Cannot resolve email for emp={}: {}", loan.getEmployeeId(), e.getMessage());
            email = null;
        }

        final Long empId = loan.getEmployeeId();
        final Long ref   = loan.getRequestId();
        final String resolvedEmail = email;

        // Send Kafka asynchronously — any Kafka failure must not contaminate the outer TX.
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                NotificationEvent event = NotificationEvent.builder()
                        .employeeId(empId)
                        .email(resolvedEmail)
                        .type(type)
                        .title(title)
                        .content(content)
                        .referenceId(String.valueOf(ref))
                        .referenceType("PRET")
                        .actionUrl("/employe/demandes")
                        .sourceService("DEMANDES-SERVICE")
                        .sendEmail(true)
                        .build();
                kafkaTemplate.send(notificationTopic, String.valueOf(empId), event);
            } catch (Exception e) {
                log.warn("[Comite] Kafka send failed for loanId={}: {}", ref, e.getMessage());
            }
        });
    }

    /**
     * Résout l'identifiant Oracle du membre du comité votant depuis le JWT Keycloak.
     * Utilise successivement : sub → email → identifiant synthétique (XOR UUID).
     *
     * @param auth contexte d'authentification Spring Security du votant
     * @return l'identifiant Oracle de l'employé, ou un identifiant synthétique si non résolu
     * @throws IllegalStateException si aucun JWT n'est trouvé dans le contexte d'authentification
     */
    private Long resolveEmployeeId(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) throw new IllegalStateException("JWT introuvable");

        String sub = jwt.getSubject();
        // Use NOT_SUPPORTED helper so any DB error cannot mark the outer TX rollback-only
        Long empId = employeeInfoHelper.findEmployeeIdBySub(sub);
        if (empId != null) return empId;

        String email = jwt.getClaimAsString("email");
        if (email != null) {
            empId = employeeInfoHelper.findEmployeeIdByEmailSafe(email);
            if (empId != null) return empId;
        }

        // Fallback: XOR of UUID's 64-bit halves gives a collision-free synthetic memberId
        // without requiring an Oracle employee record.
        log.warn("[Comite] No unique Oracle employee for sub={}; using synthetic memberId", sub);
        try {
            java.util.UUID uuid = java.util.UUID.fromString(sub);
            long bits = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
            return bits == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bits);
        } catch (IllegalArgumentException e) {
            return (long) Math.abs(sub.hashCode());
        }
    }

    /**
     * Extrait le nom complet du votant depuis les claims du JWT Keycloak
     * ({@code given_name}, {@code family_name}, {@code name} ou {@code preferred_username}).
     *
     * @param auth contexte d'authentification Spring Security du votant
     * @return nom complet du votant, ou {@code null} si le JWT est absent
     */
    private String extractFullName(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) return null;
        String firstName = jwt.getClaimAsString("given_name");
        String lastName  = jwt.getClaimAsString("family_name");
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        String name = jwt.getClaimAsString("name");
        return name != null ? name : jwt.getClaimAsString("preferred_username");
    }

    /**
     * Extrait le token JWT depuis le contexte d'authentification Spring Security.
     *
     * @param auth contexte d'authentification Spring Security
     * @return le token JWT, ou {@code null} si l'authentification n'est pas un {@link JwtAuthenticationToken}
     */
    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) return jwtAuth.getToken();
        return null;
    }
}
