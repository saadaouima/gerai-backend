package com.gerai.projetsservice.service;

import com.gerai.projetsservice.dto.NotificationEvent;
import com.gerai.projetsservice.model.Candidate;
import com.gerai.projetsservice.model.DemandeRecrutement;
import com.gerai.projetsservice.model.Interview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service de notifications Kafka déclenchées par les actions du workflow de recrutement.
 * <p>
 * Conventions de broadcast par rôle (aucun {@code employeeId} interne cible) :
 * <ul>
 *   <li>Candidature reçue → broadcast {@code ADMIN}.</li>
 *   <li>Statut candidat modifié → broadcast {@code ADMIN}.</li>
 *   <li>Entretien planifié → broadcast {@code ADMIN}.</li>
 *   <li>Décision entretien (retenu/rejeté) → broadcast {@code ADMIN}.</li>
 * </ul>
 * </p>
 * <p>
 * {@code @Service} : composant Spring géré par le conteneur IoC.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.<br>
 * {@code @RequiredArgsConstructor} : injection du producteur Kafka par constructeur.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentNotificationService {

    /** Producteur Kafka pour la publication des événements de notification. */
    private final ProjectEventProducer producer;

    /** Identifiant du service source dans les événements Kafka. */
    private static final String SOURCE   = "PROJETS-SERVICE";
    /** Type de référence métier pour les candidats. */
    private static final String REF_CAND = "CANDIDAT";
    /** Type de référence métier pour les entretiens. */
    private static final String REF_INT  = "ENTRETIEN";

    /* ── Candidature reçue / statut changé ─── */

    /**
     * Notifie les administrateurs qu'une nouvelle candidature a été soumise.
     *
     * @param c le candidat ayant soumis sa candidature
     */
    public void notifierNouveauCandidat(Candidate c) {
        emitBroadcast(
                "ADMIN",
                "INFO",
                "Nouvelle candidature reçue",
                c.getPrenom() + " " + c.getNom() + " a postulé pour le poste de « " + c.getJobTitre() + " ».",
                c.getId(),
                REF_CAND,
                "/admin/recrutement/candidats"
        );
    }

    /**
     * Notifie les administrateurs que le statut d'un candidat a changé dans le pipeline de recrutement.
     *
     * @param c           le candidat dont le statut a été modifié (avec le nouveau statut)
     * @param ancienStatut l'ancien statut avant modification (non utilisé dans le message actuel)
     */
    public void notifierStatutCandidat(Candidate c, String ancienStatut) {
        String label = statutLabel(c.getStatut());
        emitBroadcast(
                "ADMIN",
                typeForStatut(c.getStatut()),
                "Candidat " + label,
                c.getPrenom() + " " + c.getNom() + " (" + c.getJobTitre() + ") est maintenant « " + label + " ».",
                c.getId(),
                REF_CAND,
                "/admin/recrutement/candidats"
        );
    }

    /**
     * Notifie les administrateurs qu'un chef a soumis une demande de recrutement.
     *
     * @param d la demande de recrutement créée
     */
    public void notifierNouvelleDemandeRecrutement(DemandeRecrutement d) {
        emitBroadcast(
                "ADMIN",
                "INFO",
                "Nouvelle demande de recrutement",
                d.getChefNom() + " a soumis une demande pour le poste de « " + d.getTitrePoste()
                        + " » (" + d.getDepartement() + ").",
                d.getId(),
                "DEMANDE_RECRUTEMENT",
                "/admin/recrutement"
        );
    }

    /* ── Entretien ─────────────────────────── */

    /**
     * Notifie les administrateurs qu'un entretien a été planifié pour un candidat.
     *
     * @param i l'entretien planifié contenant la date, l'heure et les informations du candidat
     */
    public void notifierEntretienPlanifie(Interview i) {
        emitBroadcast(
                "ADMIN",
                "INFO",
                "Entretien planifié",
                "Un entretien a été planifié pour " + i.getCandidatPrenom() + " " + i.getCandidatNom()
                        + " (poste : " + i.getJobTitre() + ") le " + i.getDate()
                        + " à " + i.getHeureDebut() + ".",
                i.getId(),
                REF_INT,
                "/admin/recrutement/candidats"
        );
    }

    /**
     * Notifie les administrateurs de la décision prise à l'issue d'un entretien (retenu ou rejeté).
     *
     * @param i l'entretien avec la décision finale ({@code RETENU} ou autre valeur pour rejeté)
     */
    public void notifierDecisionEntretien(Interview i) {
        boolean retenu = "RETENU".equals(i.getDecision());
        String type  = retenu ? "DEMANDE_APPROUVEE" : "DEMANDE_REJETEE";
        String title = retenu ? "Candidat retenu" : "Candidat rejeté";
        String msg   = i.getCandidatPrenom() + " " + i.getCandidatNom()
                + " a été " + (retenu ? "retenu(e)" : "rejeté(e)")
                + " après l'entretien pour le poste de « " + i.getJobTitre() + " ».";

        emitBroadcast("ADMIN", type, title, msg, i.getId(), REF_INT, "/admin/recrutement/candidats");
    }

    /* ── Helpers ───────────────────────────── */

    private void emitBroadcast(String role, String type, String title, String content,
                                Long refId, String refType, String actionUrl) {
        NotificationEvent event = NotificationEvent.builder()
                .role(role)
                .type(type)
                .title(title)
                .content(content)
                .referenceId(refId != null ? refId.toString() : null)
                .referenceType(refType)
                .actionUrl(actionUrl)
                .sourceService(SOURCE)
                .sendEmail(false)
                .build();
        try {
            producer.emit(event);
        } catch (Exception e) {
            log.warn("[RecruitmentNotif] Kafka emit failed: {}", e.getMessage());
        }
    }

    private String statutLabel(String statut) {
        if (statut == null) return "mis à jour";
        return switch (statut) {
            case "NOUVEAU"       -> "nouveau";
            case "EN_REVUE"      -> "en revue";
            case "SHORTLISTE"    -> "shortlisté";
            case "INTERVIEWE"    -> "en entretien";
            case "OFFRE_ENVOYEE" -> "offre envoyée";
            case "EMBAUCHE"      -> "embauché";
            case "REJETE"        -> "rejeté";
            default              -> statut.toLowerCase();
        };
    }

    private String typeForStatut(String statut) {
        if (statut == null) return "INFO";
        return switch (statut) {
            case "EMBAUCHE"      -> "DEMANDE_APPROUVEE";
            case "REJETE"        -> "DEMANDE_REJETEE";
            case "SHORTLISTE", "OFFRE_ENVOYEE" -> "INFO";
            default              -> "INFO";
        };
    }
}
