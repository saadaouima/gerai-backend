package com.gerai.demandesservice.scheduler;

import com.gerai.demandesservice.dto.NotificationEvent;
import com.gerai.demandesservice.model.DepartEmploye;
import com.gerai.demandesservice.repository.DepartRepository;
import com.gerai.demandesservice.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tâche planifiée qui alerte les managers lorsqu'un contrat CDD de leur équipe
 * arrive à expiration dans les 30 prochains jours.
 * <p>
 * {@code @Component} : enregistre cette classe comme bean Spring géré par le conteneur.
 * <p>
 * {@code @Scheduled(cron = "0 30 8 * * *")} : exécution quotidienne à 08h30.
 * Nécessite {@code @EnableScheduling} sur la classe principale
 * {@link com.gerai.demandesservice.DemandesServiceApplication}.
 * <p>
 * Une notification Kafka distincte est envoyée sur le topic {@code notification-events}
 * pour chaque contrat expirant, afin que le manager reçoive une alerte personnalisée
 * contenant le nom de l'employé concerné.
 *
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContratExpiryScheduler {

    /** Nombre de jours avant expiration à partir duquel l'alerte est déclenchée. */
    private static final int JOURS_ALERTE = 30;

    /** Repository pour la récupération des fins de contrat imminentes. */
    private final DepartRepository                        departRepo;
    /** Repository pour la résolution des informations manager. */
    private final EmployeeRepository                      empRepo;
    /** Producteur Kafka pour l'envoi des notifications. */
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    /** Nom du topic Kafka sur lequel publier les notifications (configuré dans application.yml). */
    @Value("${app.kafka.topic.notifications}")
    private String topic;

    /**
     * Vérifie quotidiennement les contrats CDD expirant dans les {@value #JOURS_ALERTE} prochains jours
     * et envoie une notification Kafka au manager de chaque employé concerné.
     * <p>
     * {@code @Scheduled(cron = "0 30 8 * * *")} : exécution tous les jours à 08h30.
     */
    @Scheduled(cron = "0 30 8 * * *")
    public void alerterContratsExpiration() {
        List<DepartEmploye> expirations = departRepo.findFinContratProches(JOURS_ALERTE);

        if (expirations.isEmpty()) {
            log.info("[ContratExpiry] Aucun contrat expirant dans les {} jours.", JOURS_ALERTE);
            return;
        }
        log.info("[ContratExpiry] {} contrat(s) expirant(s) détecté(s).", expirations.size());

        for (DepartEmploye depart : expirations) {
            try {
                Long employeId    = depart.getEmployeId();
                Long managerId    = empRepo.findManagerIdByEmployeeId(employeId);
                if (managerId == null) {
                    log.debug("[ContratExpiry] Pas de manager pour employé {}", employeId);
                    continue;
                }

                String managerEmail = empRepo.findManagerEmailByEmployeeId(employeId);
                String prenomManager = prenom(empRepo.findFullNameByEmployeeId(managerId));
                String nomEmploye   = depart.getEmployeNom() != null
                        ? depart.getEmployeNom()
                        : empRepo.findFullNameByEmployeeId(employeId);

                String titre   = "Contrat expirant : " + truncate(nomEmploye, 40);
                String contenu = String.format(
                        "Bonjour %s, le contrat de %s (poste : %s) arrive à échéance le %s. "
                        + "Merci de prendre les dispositions nécessaires (renouvellement ou clôture).",
                        prenomManager,
                        nomEmploye,
                        depart.getEmployePoste() != null ? depart.getEmployePoste() : "—",
                        depart.getDateDepart());

                NotificationEvent event = NotificationEvent.builder()
                        .employeeId(managerId)
                        .email(managerEmail)
                        .type("RAPPEL")
                        .title(titre)
                        .content(contenu)
                        .referenceType("DEPART")
                        .referenceId(String.valueOf(depart.getId()))
                        .actionUrl("/admin/departs")
                        .sourceService("DEMANDES-SERVICE")
                        .sendEmail(true)
                        .build();

                send(event, "CONTRAT_EXPIRY depart=" + depart.getId() + " employe=" + employeId);
            } catch (Exception e) {
                log.warn("[ContratExpiry] Erreur depart={} : {}", depart.getId(), e.getMessage());
            }
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
                        log.warn("[ContratExpiry] Échec Kafka | {} : {}", label, ex.getMessage());
                    } else {
                        log.info("[ContratExpiry] Publié | {} | partition={} | offset={}",
                                label,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Tronque une chaîne à la longueur maximale spécifiée et ajoute {@code ...} si tronquée.
     *
     * @param s   chaîne à tronquer (peut être nulle)
     * @param max longueur maximale souhaitée
     * @return la chaîne tronquée ou la chaîne originale si elle est plus courte
     */
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    /**
     * Extrait le prénom d'un nom complet (premier mot avant l'espace).
     *
     * @param nomComplet nom complet de la personne (ex : {@code Alice Martin})
     * @return le prénom, ou {@code "Manager"} si la valeur est nulle ou vide
     */
    private String prenom(String nomComplet) {
        if (nomComplet == null || nomComplet.isBlank()) return "Manager";
        return nomComplet.split(" ")[0];
    }
}
