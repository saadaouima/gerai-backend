package com.gerai.tachesservice.service;

import com.gerai.tachesservice.dto.ProjetDTO;
import com.gerai.tachesservice.entity.Task;
import com.gerai.tachesservice.event.TacheNotificationEvent;
import com.gerai.tachesservice.repository.EmployeeQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service Kafka producteur responsable de l'envoi des notifications liées aux tâches.
 * <p>
 * {@code @Service} : déclare ce bean comme service Spring géré par le conteneur.
 * {@code @RequiredArgsConstructor} : génère un constructeur injectant toutes les dépendances finales.
 * <p>
 * Toutes les méthodes publiques sont annotées {@code @Async} : elles s'exécutent dans
 * le pool de threads {@code notifExecutor} configuré dans {@link com.gerai.tachesservice.config.AsyncConfig}.
 * Cela garantit que les envois Kafka ne bloquent jamais la transaction HTTP principale.
 * <p>
 * Ce producteur publie des {@link TacheNotificationEvent} vers le topic Kafka configuré,
 * à destination de {@code notification-service} qui gère la persistance en base Oracle
 * et le push WebSocket vers Angular.
 * <p>
 * Scénarios couverts :
 * <ol>
 *   <li>Nouvelle assignation → notification à l'EMPLOYÉ assigné</li>
 *   <li>Changement de statut → notification au CHEF du projet</li>
 *   <li>Modification par le chef → notification à l'EMPLOYÉ assigné</li>
 *   <li>Réassignation → notification à l'ancien et au nouvel assigné</li>
 * </ol>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TacheNotificationProducer {

    /** Repository pour résoudre les informations des employés (nom, email, UUID Keycloak). */
    private final EmployeeQueryRepository                       empRepo;

    /** Template Kafka pour publier les événements vers notification-service. */
    private final KafkaTemplate<String, TacheNotificationEvent> kafkaTemplate;

    /**
     * Nom du topic Kafka cible, injecté depuis la propriété {@code app.kafka.topic.notifications}.
     */
    @Value("${app.kafka.topic.notifications}")
    private String topic;

    /* ── 1. NOUVELLE ASSIGNATION → notification à l'EMPLOYÉ ── */

    /**
     * Notifie l'employé qu'une nouvelle tâche lui a été assignée.
     * <p>
     * S'exécute de manière asynchrone dans le pool {@code notifExecutor}.
     * Si {@code task.getAssignedTo()} est null, la méthode ne fait rien.
     *
     * @param task   la tâche nouvellement créée et assignée
     * @param projet le DTO du projet parent (peut être null en cas d'indisponibilité de projets-service)
     */
    @Async
    public void notifierAssignation(Task task, ProjetDTO projet) {   // ← ProjetDTO, plus Project
        if (task.getAssignedTo() == null) return;

        String nomAssigne  = empRepo.findFullNameById(task.getAssignedTo());
        String emailAssigne = empRepo.findEmailById(task.getAssignedTo());
        String keycloakSub  = empRepo.findKeycloakSubById(task.getAssignedTo());
        String nomProjet    = projet != null ? projet.getNom() : "Projet inconnu";
        String echeance     = task.getDueDate() != null ? task.getDueDate().toString() : "non définie";

        TacheNotificationEvent event = TacheNotificationEvent.builder()
                .employeeId(task.getAssignedTo())
                .email(emailAssigne)
                .keycloakSub(keycloakSub)
                .role("EMPLOYE")
                .type("INFO")
                .title("Nouvelle tâche assignée : " + truncate(task.getTitle(), 60))
                .content(String.format(
                        "Bonjour %s, vous avez été assigné(e) à la tâche \"%s\" "
                                + "dans le projet \"%s\". Échéance : %s.",
                        firstName(nomAssigne), task.getTitle(), nomProjet, echeance))
                .referenceId(String.valueOf(task.getTaskId()))
                .referenceType("TACHE")
                .actionUrl("/employe/taches")
                .build();

        send(event, "ASSIGNATION tâche=" + task.getTaskId() + " → emp=" + task.getAssignedTo());
    }

    /* ── 2. CHANGEMENT DE STATUT → notification au CHEF ───── */

    /**
     * Notifie le chef de projet d'un changement de statut d'une tâche.
     * <p>
     * Envoie une notification de type {@code INFO} si la tâche est terminée,
     * ou de type {@code RAPPEL} pour tout autre changement de statut.
     * S'exécute de manière asynchrone dans le pool {@code notifExecutor}.
     * Si {@code projet} est null ou ne possède pas de {@code createdBy}, la méthode ne fait rien.
     *
     * @param task         la tâche dont le statut a changé
     * @param ancienStatut l'ancien statut Oracle de la tâche (avant modification)
     * @param projet       le DTO du projet parent, contenant l'identifiant du chef ({@code createdBy})
     */
    @Async
    public void notifierStatutChange(Task task, String ancienStatut, ProjetDTO projet) {  // ← ProjetDTO
        if (projet == null || projet.getCreatedBy() == null) return;

        Long chefId        = projet.getCreatedBy();
        String emailChef   = empRepo.findEmailById(chefId);
        String keycloakSub = empRepo.findKeycloakSubById(chefId);
        String nomAssigne  = task.getAssignedTo() != null
                ? empRepo.findFullNameById(task.getAssignedTo()) : "Un employé";

        String type, titre, message;

        if ("TERMINE".equals(task.getStatus())) {
            type    = "INFO";
            titre   = "✅ Tâche terminée : " + truncate(task.getTitle(), 55);
            message = String.format(
                    "%s a terminé la tâche \"%s\" dans le projet \"%s\".",
                    firstName(nomAssigne), task.getTitle(), projet.getNom());
        } else {
            type    = "RAPPEL";
            titre   = "Tâche mise à jour : " + truncate(task.getTitle(), 50);
            message = String.format(
                    "La tâche \"%s\" est passée de %s à %s (projet : %s).",
                    task.getTitle(), toLabel(ancienStatut),
                    toLabel(task.getStatus()), projet.getNom());
        }

        TacheNotificationEvent event = TacheNotificationEvent.builder()
                .employeeId(chefId)
                .email(emailChef)
                .keycloakSub(keycloakSub)
                .role("CHEF")
                .type(type)
                .title(titre)
                .content(message)
                .referenceId(String.valueOf(task.getTaskId()))
                .referenceType("TACHE")
                .actionUrl("/chef/taches")
                .build();

        send(event, "STATUT_CHANGE tâche=" + task.getTaskId()
                + " statut=" + task.getStatus() + " → chef=" + chefId);
    }

    /* ── 4. MODIFICATION PAR LE CHEF → notification à l'EMPLOYÉ ── */

    /**
     * Notifie l'employé assigné que le chef a modifié les détails de sa tâche
     * (échéance, priorité, description, etc.).
     * <p>
     * Envoie une notification de type {@code RAPPEL} à l'employé avec les nouvelles valeurs.
     * S'exécute de manière asynchrone. Ne fait rien si la tâche n'est pas assignée.
     *
     * @param task   la tâche modifiée par le chef (avec les nouvelles valeurs)
     * @param projet le DTO du projet parent (peut être null)
     */
    @Async
    public void notifierChefModif(Task task, ProjetDTO projet) {  // ← ProjetDTO
        if (task.getAssignedTo() == null) return;

        String nomAssigne   = empRepo.findFullNameById(task.getAssignedTo());
        String emailAssigne = empRepo.findEmailById(task.getAssignedTo());
        String keycloakSub  = empRepo.findKeycloakSubById(task.getAssignedTo());
        String nomProjet    = projet != null ? projet.getNom() : "votre projet";

        TacheNotificationEvent event = TacheNotificationEvent.builder()
                .employeeId(task.getAssignedTo())
                .email(emailAssigne)
                .keycloakSub(keycloakSub)
                .role("EMPLOYE")
                .type("RAPPEL")
                .title("Tâche modifiée : " + truncate(task.getTitle(), 60))
                .content(String.format(
                        "Bonjour %s, votre tâche \"%s\" dans \"%s\" a été modifiée. "
                                + "Nouvelle échéance : %s, priorité : %s.",
                        firstName(nomAssigne), task.getTitle(), nomProjet,
                        task.getDueDate() != null ? task.getDueDate().toString() : "inchangée",
                        toAngularPriorite(task.getPriority())))
                .referenceId(String.valueOf(task.getTaskId()))
                .referenceType("TACHE")
                .actionUrl("/employe/taches")
                .build();

        send(event, "MODIF_CHEF tâche=" + task.getTaskId() + " → emp=" + task.getAssignedTo());
    }

    /* ── 5. RÉASSIGNATION ───────────────────────────────────── */

    /**
     * Notifie l'ancien et le nouvel employé assigné lors d'une réassignation de tâche.
     * <p>
     * Envoie deux notifications distinctes :
     * <ol>
     *   <li>À l'ancien assigné : notification de retrait de la tâche (type {@code INFO})</li>
     *   <li>Au nouvel assigné : notification d'assignation via {@link #notifierAssignation}</li>
     * </ol>
     * Si {@code ancienAssigne} est null ou identique au nouvel assigné, seule la
     * notification au nouvel assigné est envoyée.
     * S'exécute de manière asynchrone dans le pool {@code notifExecutor}.
     *
     * @param task          la tâche réassignée (avec le nouvel {@code assignedTo})
     * @param ancienAssigne identifiant Oracle de l'ancien employé assigné (peut être null)
     * @param projet        le DTO du projet parent (peut être null)
     */
    @Async
    public void notifierReassignation(Task task, Long ancienAssigne, ProjetDTO projet) {  // ← ProjetDTO
        if (ancienAssigne != null && !ancienAssigne.equals(task.getAssignedTo())) {
            String emailAncien  = empRepo.findEmailById(ancienAssigne);
            String keycloakSub  = empRepo.findKeycloakSubById(ancienAssigne);
            String nomAncien    = empRepo.findFullNameById(ancienAssigne);
            String nomProjet    = projet != null ? projet.getNom() : "votre projet";

            TacheNotificationEvent retrait = TacheNotificationEvent.builder()
                    .employeeId(ancienAssigne)
                    .email(emailAncien)
                    .keycloakSub(keycloakSub)
                    .role("EMPLOYE")
                    .type("INFO")
                    .title("Tâche retirée : " + truncate(task.getTitle(), 60))
                    .content(String.format(
                            "Bonjour %s, la tâche \"%s\" dans \"%s\" vous a été retirée.",
                            firstName(nomAncien), task.getTitle(), nomProjet))
                    .referenceId(String.valueOf(task.getTaskId()))
                    .referenceType("TACHE")
                    .actionUrl("/employe/taches")
                    .build();

            send(retrait, "RETRAIT_ASSIGNATION tâche=" + task.getTaskId()
                    + " → ancienEmp=" + ancienAssigne);
        }

        if (task.getAssignedTo() != null) {
            notifierAssignation(task, projet);
        }
    }

    /* ── Envoi Kafka ─────────────────────────────────────── */

    /**
     * Publie un événement de notification sur le topic Kafka configuré.
     * <p>
     * La clé Kafka est l'{@code employeeId} du destinataire pour garantir
     * l'ordre des messages par employé. L'envoi est non-bloquant :
     * le résultat est logué via {@code whenComplete}.
     * Ne fait rien si {@code employeeId} est null.
     *
     * @param event    l'événement de notification à publier
     * @param logLabel libellé descriptif pour les logs
     */
    private void send(TacheNotificationEvent event, String logLabel) {
        if (event.getEmployeeId() == null) return;
        kafkaTemplate.send(topic, String.valueOf(event.getEmployeeId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("[TacheNotif] Échec Kafka | {} : {}", logLabel, ex.getMessage());
                    } else {
                        log.info("[TacheNotif] Publié | {} | partition={} | offset={}",
                                logLabel,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /* ── Helpers ─────────────────────────────────────────── */

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
     * @param fullName nom complet au format "Prénom Nom"
     * @return le prénom extrait, ou "Employé" si la valeur est null ou vide
     */
    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Employé";
        return fullName.split(" ")[0];
    }

    /**
     * Convertit un statut Oracle en libellé lisible en français pour les notifications.
     *
     * @param oracleStatut valeur Oracle du statut (A_FAIRE, EN_COURS, EN_REVUE, TERMINE, BLOQUE)
     * @return libellé français correspondant, ou "Inconnu" si null, ou la valeur brute si non reconnue
     */
    private String toLabel(String oracleStatut) {
        if (oracleStatut == null) return "Inconnu";
        return switch (oracleStatut) {
            case "A_FAIRE"  -> "À faire";
            case "EN_COURS" -> "En cours";
            case "EN_REVUE" -> "En révision";
            case "TERMINE"  -> "Terminée";
            case "BLOQUE"   -> "Bloquée";
            default         -> oracleStatut;
        };
    }

    /**
     * Convertit une priorité Oracle en libellé Angular lisible pour les messages de notification.
     *
     * @param oraclePrio valeur Oracle de la priorité (HAUTE, FAIBLE, CRITIQUE, NORMALE)
     * @return libellé Angular correspondant (Haute, Basse, Critique, Moyenne)
     */
    private String toAngularPriorite(String oraclePrio) {
        if (oraclePrio == null) return "Moyenne";
        return switch (oraclePrio) {
            case "HAUTE"    -> "Haute";
            case "FAIBLE"   -> "Basse";
            case "CRITIQUE" -> "Critique";
            default         -> "Moyenne";
        };
    }
}