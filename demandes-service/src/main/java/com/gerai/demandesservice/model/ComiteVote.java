package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant le vote d'un membre du comité de crédit pour une demande de prêt.
 * Mappée sur la table Oracle {@code GERAI.COMITE_VOTES}.
 * <p>
 * La contrainte d'unicité ({@code LOAN_ID, MEMBER_ID}) garantit qu'un membre ne peut voter
 * qu'une seule fois par crédit. Lorsque le seuil de votes FAVORABLE ou DEFAVORABLE est atteint,
 * {@link com.gerai.demandesservice.service.ComiteService} applique la décision automatiquement.
 *
 * @since 1.0
 */
@Entity
@Table(name = "COMITE_VOTES",
       uniqueConstraints = @UniqueConstraint(columnNames = {"LOAN_ID", "MEMBER_ID"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComiteVote {

    /** Identifiant technique du vote (COMITE_VOTES.VOTE_ID). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VOTE_ID")
    private Long voteId;

    /** FK → LOAN_REQUESTS.REQUEST_ID — crédit sur lequel porte ce vote. */
    @Column(name = "LOAN_ID", nullable = false)
    private Long loanId;

    /** FK → EMPLOYEES.EMPLOYEE_ID — membre du comité qui a voté. */
    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    /** Nom complet du membre du comité (dénormalisé depuis le JWT au moment du vote). */
    @Column(name = "MEMBER_NOM", length = 200)
    private String memberNom;

    /** Décision du membre du comité : {@code FAVORABLE} ou {@code DEFAVORABLE}. */
    @Column(name = "VOTE", nullable = false, length = 10)
    private String vote;

    /** Commentaire justifiant la décision (optionnel). */
    @Column(name = "COMMENTAIRE", length = 500)
    private String commentaire;

    /** Montant de crédit suggéré par ce membre (si vote FAVORABLE). */
    @Column(name = "MONTANT_SUGGERE")
    private BigDecimal montantSuggere;

    /** Nombre de mensualités de remboursement suggérées par ce membre (si vote FAVORABLE). */
    @Column(name = "NB_TRANCHES_SUGGERES")
    private Integer nbTranchesSuggeres;

    /** Horodatage de l'enregistrement du vote — positionné par {@code @PrePersist}. */
    @Column(name = "VOTED_AT", nullable = false)
    private LocalDateTime votedAt;

    /**
     * Initialise {@code votedAt} à l'heure courante avant l'insertion JPA.
     */
    @PrePersist
    protected void onCreate() {
        this.votedAt = LocalDateTime.now();
    }
}
