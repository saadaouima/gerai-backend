package com.gerai.tachesservice.scheduler;

import com.gerai.tachesservice.entity.Task;
import com.gerai.tachesservice.event.TacheNotificationEvent;
import com.gerai.tachesservice.repository.EmployeeQueryRepository;
import com.gerai.tachesservice.repository.TacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Planificateur Spring chargé de détecter et notifier les tâches en retard.
 * <p>
 * {@code @Component} : déclare cette classe comme composant Spring géré par le conteneur.
 * {@code @RequiredArgsConstructor} : génère un constructeur injectant toutes les dépendances finales.
 * <p>
 * Ce scheduler s'exécute quotidiennement à 08h00 et envoie une notification Kafka
 * consolidée à chaque chef de projet dont au moins une tâche a dépassé son échéance.
 * Une seule notification par chef est envoyée (pas une notification par tâche en retard),
 * incluant un résumé des tâches concernées.
 * <p>
 * Le scheduler est activé globalement par {@code @EnableScheduling} dans
 * {@link com.gerai.tachesservice.TachesServiceApplication}.
 *
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TacheOverdueScheduler {

    /** Repository pour interroger la table TASKS et récupérer les tâches en retard. */
    private final TacheRepository                               tacheRepo;

    /** Repository pour résoudre les informations de contact des chefs (email, UUID Keycloak). */
    private final EmployeeQueryRepository                       empRepo;

    /** Template Kafka pour publier les événements de notification vers notification-service. */
    private final KafkaTemplate<String, TacheNotificationEvent> kafkaTemplate;

    /**
     * Nom du topic Kafka cible, injecté depuis la propriété {@code app.kafka.topic.notifications}.
     */
    @Value("${app.kafka.topic.notifications}")
    private String topic;

    /**
     * Détecte toutes les tâches dont l'échéance est dépassée et envoie une notification
     * consolidée à chaque chef de projet concerné.
     * <p>
     * Exécuté chaque jour à 08h00 via l'expression cron {@code 0 0 8 * * *}.
     * <p>
     * Algorithme :
     * <ol>
     *   <li>Récupère toutes les tâches en retard (statut actif, date échue)</li>
     *   <li>Groupe les tâches par chef créateur ({@code createdBy})</li>
     *   <li>Pour chaque chef, construit un message personnalisé (singulier ou liste)</li>
     *   <li>Publie un {@link TacheNotificationEvent} de type {@code RAPPEL} via Kafka</li>
     * </ol>
     * Les erreurs par chef sont isolées ({@code try/catch}) pour ne pas interrompre
     * le traitement des autres chefs.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void notifierTachesEnRetard() {
        LocalDate today = LocalDate.now();
        List<Task> overdue = tacheRepo.findOverdueTasks(today);

        if (overdue.isEmpty()) {
            log.info("[TacheOverdue] Aucune tâche en retard.");
            return;
        }

        // Group by chef (createdBy)
        Map<Long, List<Task>> parChef = overdue.stream()
                .filter(t -> t.getCreatedBy() != null)
                .collect(Collectors.groupingBy(Task::getCreatedBy));

        log.info("[TacheOverdue] {} tâche(s) en retard pour {} chef(s).",
                overdue.size(), parChef.size());

        parChef.forEach((chefId, taches) -> {
            try {
                String email      = empRepo.findEmailById(chefId);
                String keycloakSub = empRepo.findKeycloakSubById(chefId);
                String prenom     = prenom(empRepo.findFullNameById(chefId));

                String titre, contenu;
                if (taches.size() == 1) {
                    Task t = taches.get(0);
                    titre   = "Tâche en retard : " + truncate(t.getTitle(), 55);
                    contenu = String.format(
                            "Bonjour %s, la tâche \"%s\" est en retard depuis le %s (statut : %s).",
                            prenom, t.getTitle(), t.getDueDate(), labelStatut(t.getStatus()));
                } else {
                    titre   = taches.size() + " tâches en retard dans vos projets";
                    String liste = taches.stream()
                            .limit(3)
                            .map(t -> "• " + truncate(t.getTitle(), 40) + " (échue le " + t.getDueDate() + ")")
                            .collect(Collectors.joining("\n"));
                    if (taches.size() > 3) liste += "\n... et " + (taches.size() - 3) + " autre(s).";
                    contenu = String.format("Bonjour %s,\n%d tâches sont en retard :\n%s",
                            prenom, taches.size(), liste);
                }

                TacheNotificationEvent event = TacheNotificationEvent.builder()
                        .employeeId(chefId)
                        .email(email)
                        .keycloakSub(keycloakSub)
                        .role("CHEF")
                        .type("RAPPEL")
                        .title(titre)
                        .content(contenu)
                        .referenceId(String.valueOf(taches.get(0).getTaskId()))
                        .referenceType("TACHE")
                        .actionUrl("/chef/taches")
                        .sourceService("TACHES-SERVICE")
                        .build();

                send(event, "OVERDUE chef=" + chefId + " nb=" + taches.size());
            } catch (Exception e) {
                log.warn("[TacheOverdue] Erreur chef={} : {}", chefId, e.getMessage());
            }
        });
    }

    /**
     * Publie un événement de notification sur le topic Kafka configuré.
     * <p>
     * La clé Kafka est l'{@code employeeId} du destinataire, ce qui garantit
     * l'ordre des messages par employé. L'envoi est asynchrone : le résultat
     * (succès ou échec) est logué via {@code whenComplete}.
     * Si {@code employeeId} est null, la méthode ne fait rien.
     *
     * @param event l'événement de notification à publier
     * @param label libellé descriptif pour les logs (ex : "OVERDUE chef=42 nb=3")
     */
    private void send(TacheNotificationEvent event, String label) {
        if (event.getEmployeeId() == null) return;
        kafkaTemplate.send(topic, String.valueOf(event.getEmployeeId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("[TacheOverdue] Échec Kafka | {} : {}", label, ex.getMessage());
                    } else {
                        log.info("[TacheOverdue] Publié | {} | partition={} | offset={}",
                                label,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Tronque une chaîne à {@code max} caractères en ajoutant "..." si nécessaire.
     *
     * @param s   la chaîne à tronquer (peut être null)
     * @param max longueur maximale souhaitée (incluant les "...")
     * @return la chaîne tronquée, ou une chaîne vide si {@code s} est null
     */
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    /**
     * Extrait le prénom depuis un nom complet ("Prénom Nom").
     * <p>
     * Utilisé pour personnaliser les messages de notification ("Bonjour Prénom,").
     *
     * @param nomComplet nom complet de la personne au format "Prénom Nom"
     * @return le prénom extrait, ou "Chef" si la valeur est null ou vide
     */
    private String prenom(String nomComplet) {
        if (nomComplet == null || nomComplet.isBlank()) return "Chef";
        return nomComplet.split(" ")[0];
    }

    /**
     * Convertit un statut Oracle en libellé lisible en français pour les notifications.
     *
     * @param statut statut Oracle de la tâche (A_FAIRE, EN_COURS, EN_REVUE, BLOQUE, TERMINE)
     * @return libellé français correspondant, ou la valeur brute si non reconnue
     */
    private String labelStatut(String statut) {
        if (statut == null) return "inconnu";
        return switch (statut) {
            case "A_FAIRE"  -> "À faire";
            case "EN_COURS" -> "En cours";
            case "EN_REVUE" -> "En révision";
            case "BLOQUE"   -> "Bloquée";
            default         -> statut;
        };
    }
}
