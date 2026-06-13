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
 * TacheNotificationProducer — version dédupliquée.
 *
 * SEUL CHANGEMENT par rapport à la version précédente :
 *   Project (entité JPA) → ProjetDTO (reçu depuis projets-service via Feign)
 *
 * Tous les champs nécessaires sont présents dans ProjetDTO :
 *   projet.getNom()        → nomProjet dans les messages
 *   projet.getCreatedBy()  → chefId pour les notifications au chef
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TacheNotificationProducer {

    private final EmployeeQueryRepository                       empRepo;
    private final KafkaTemplate<String, TacheNotificationEvent> kafkaTemplate;

    @Value("${app.kafka.topic.notifications}")
    private String topic;

    /* ── 1. NOUVELLE ASSIGNATION → notification à l'EMPLOYÉ ── */

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

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Employé";
        return fullName.split(" ")[0];
    }

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