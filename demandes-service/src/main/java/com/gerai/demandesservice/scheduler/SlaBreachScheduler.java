package com.gerai.demandesservice.scheduler;

import com.gerai.demandesservice.dto.NotificationEvent;
import com.gerai.demandesservice.model.LeaveRequest;
import com.gerai.demandesservice.model.LoanRequest;
import com.gerai.demandesservice.model.TrainingRequest;
import com.gerai.demandesservice.repository.EmployeeRepository;
import com.gerai.demandesservice.repository.LeaveRequestRepository;
import com.gerai.demandesservice.repository.LoanRequestRepository;
import com.gerai.demandesservice.repository.TrainingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tâche planifiée de détection des violations de SLA (Service Level Agreement).
 * <p>
 * Rappelle chaque chef que des demandes de son équipe sont en attente depuis plus de 72 heures,
 * en consolidant toutes les demandes (congé + formation + crédit) en une seule notification
 * par chef afin d'éviter le spam.
 * <p>
 * {@code @Component} : enregistre cette classe comme bean Spring géré par le conteneur.
 * <p>
 * {@code @Scheduled(cron = "0 0 9 * * *")} : exécution quotidienne à 09h00.
 * Nécessite {@code @EnableScheduling} sur
 * {@link com.gerai.demandesservice.DemandesServiceApplication}.
 *
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaBreachScheduler {

    /** Nombre d'heures au-delà duquel une demande en attente est considérée en violation SLA. */
    private static final int SLA_HEURES = 72;

    /** Repository pour les demandes de congé. */
    private final LeaveRequestRepository             leaveRepo;
    /** Repository pour les demandes de formation. */
    private final TrainingRequestRepository          trainingRepo;
    /** Repository pour les demandes de crédit. */
    private final LoanRequestRepository              loanRepo;
    /** Repository pour la résolution des informations manager. */
    private final EmployeeRepository                 empRepo;
    /** Producteur Kafka pour l'envoi des notifications. */
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    /** Nom du topic Kafka sur lequel publier les notifications (configuré dans application.yml). */
    @Value("${app.kafka.topic.notifications}")
    private String topic;

    /**
     * Vérifie quotidiennement les demandes en attente depuis plus de {@value #SLA_HEURES} heures
     * et envoie une notification Kafka consolidée à chaque chef concerné.
     * <p>
     * {@code @Scheduled(cron = "0 0 9 * * *")} : exécution tous les jours à 09h00.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void verifierSlaBreach() {
        LocalDateTime seuil = LocalDateTime.now().minusHours(SLA_HEURES);

        List<LeaveRequest>    conges     = leaveRepo.findEnAttentePlusDe(seuil);
        List<TrainingRequest> formations = trainingRepo.findEnAttentePlusDe(seuil);
        List<LoanRequest>     credits    = loanRepo.findEnAttentePlusDe(seuil);

        int total = conges.size() + formations.size() + credits.size();
        if (total == 0) {
            log.info("[SLA] Aucune demande en dépassement SLA.");
            return;
        }
        log.info("[SLA] {} demande(s) en dépassement SLA : {} congés, {} formations, {} crédits.",
                total, conges.size(), formations.size(), credits.size());

        // Compter par chef (manager de l'employé demandeur)
        Map<Long, Integer> compteParChef = new HashMap<>();
        Map<Long, String>  emailParChef  = new HashMap<>();

        for (LeaveRequest lr : conges) {
            accumuler(lr.getEmployeeId(), compteParChef, emailParChef);
        }
        for (TrainingRequest tr : formations) {
            accumuler(tr.getEmployeeId(), compteParChef, emailParChef);
        }
        for (LoanRequest loan : credits) {
            accumuler(loan.getEmployeeId(), compteParChef, emailParChef);
        }

        // Envoyer une notification consolidée par chef
        compteParChef.forEach((chefId, nb) -> {
            try {
                String email   = emailParChef.getOrDefault(chefId, "");
                String prenom  = prenom(empRepo.findFullNameByEmployeeId(chefId));
                String titre   = nb == 1
                        ? "1 demande en attente depuis " + SLA_HEURES + "h"
                        : nb + " demandes en attente depuis plus de " + SLA_HEURES + "h";
                String contenu = String.format(
                        "Bonjour %s, %d demande(s) de votre équipe n'ont pas été traitées depuis plus de %d heures. "
                        + "Merci de les examiner dès que possible.",
                        prenom, nb, SLA_HEURES);

                NotificationEvent event = NotificationEvent.builder()
                        .employeeId(chefId)
                        .email(email)
                        .type("RAPPEL")
                        .title(titre)
                        .content(contenu)
                        .referenceType("DEMANDE")
                        .referenceId(String.valueOf(chefId))
                        .actionUrl("/chef/demandes")
                        .sourceService("DEMANDES-SERVICE")
                        .sendEmail(false)
                        .build();

                send(event, "SLA chef=" + chefId + " nb=" + nb);
            } catch (Exception e) {
                log.warn("[SLA] Erreur chef={} : {}", chefId, e.getMessage());
            }
        });
    }

    /**
     * Résout le manager d'un employé et incrémente son compteur de demandes en violation SLA.
     * Ignore silencieusement les employés sans manager configuré.
     *
     * @param employeeId identifiant Oracle de l'employé auteur de la demande
     * @param compte     map chef → nombre de demandes en violation SLA
     * @param emails     map chef → adresse email du chef (pour la notification)
     */
    private void accumuler(Long employeeId,
                           Map<Long, Integer> compte,
                           Map<Long, String>  emails) {
        if (employeeId == null) return;
        try {
            Long chefId = empRepo.findManagerIdByEmployeeId(employeeId);
            if (chefId == null) return;
            compte.merge(chefId, 1, Integer::sum);
            emails.computeIfAbsent(chefId, id -> {
                try { return empRepo.findManagerEmailByEmployeeId(employeeId); }
                catch (Exception ex) { return ""; }
            });
        } catch (Exception e) {
            log.debug("[SLA] Manager introuvable pour employé {}", employeeId);
        }
    }

    /**
     * Publie un événement de notification sur le topic Kafka {@code notification-events}.
     * Ignore silencieusement les événements sans {@code employeeId}.
     *
     * @param event l'événement de notification à publier
     * @param label libellé court pour les logs (identifie la source de la notification)
     */
    private void send(NotificationEvent event, String label) {
        if (event.getEmployeeId() == null) return;
        kafkaTemplate.send(topic, String.valueOf(event.getEmployeeId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("[SLA] Échec Kafka | {} : {}", label, ex.getMessage());
                    } else {
                        log.info("[SLA] Publié | {} | partition={} | offset={}",
                                label,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Extrait le prénom d'un nom complet (premier mot avant l'espace).
     *
     * @param nomComplet nom complet de la personne (ex : {@code Ahmed Benali})
     * @return le prénom, ou {@code "Chef"} si la valeur est nulle ou vide
     */
    private String prenom(String nomComplet) {
        if (nomComplet == null || nomComplet.isBlank()) return "Chef";
        return nomComplet.split(" ")[0];
    }
}
