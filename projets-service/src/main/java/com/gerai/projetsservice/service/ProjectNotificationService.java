package com.gerai.projetsservice.service;

import com.gerai.projetsservice.dto.EmployeDTO;
import com.gerai.projetsservice.dto.NotificationEvent;
import com.gerai.projetsservice.model.Project;
import com.gerai.projetsservice.model.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service centralisé pour toutes les notifications Kafka liées aux projets.
 * <p>
 * Publie des événements via {@link ProjectEventProducer} sur le topic
 * {@code notification-events} dans les cas suivants :
 * <ol>
 *   <li>Membre ajouté au projet — notification à l'employé ajouté.</li>
 *   <li>Tâche assignée — notification à l'employé assigné.</li>
 *   <li>Tâche terminée — notification au chef du projet.</li>
 *   <li>Projet terminé — notification à tous les membres de l'équipe.</li>
 *   <li>Projet modifié — notification aux membres actifs (hors chef).</li>
 * </ol>
 * </p>
 * <p>
 * {@code @Service} : composant Spring géré par le conteneur IoC.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectNotificationService {

    /** Producteur Kafka pour la publication des événements de notification. */
    private final ProjectEventProducer producer;
    /** Service de résolution des informations des employés. */
    private final EmployeService       employeService;

    /** Identifiant du service source dans les événements Kafka. */
    private static final String SOURCE   = "PROJET-SERVICE";
    /** Type de référence métier pour les projets. */
    private static final String REF_TYPE = "PROJET";
    /** Type de référence métier pour les tâches. */
    private static final String TASK_REF = "TACHE";

    /**
     * Retourne le nom complet d'un employé, ou la valeur de repli si indisponible.
     *
     * @param emp      le DTO de l'employé (peut être {@code null})
     * @param fallback valeur de repli si le DTO est absent ou sans nom
     * @return le nom complet ou la valeur de repli
     */
    private String nomComplet(EmployeDTO emp, String fallback) {
        if (emp == null) return fallback;
        if (emp.getNomComplet() != null && !emp.getNomComplet().isBlank()) return emp.getNomComplet();
        String full = ((emp.getPrenom() != null ? emp.getPrenom() : "") + " "
                     + (emp.getNom()    != null ? emp.getNom()    : "")).trim();
        return full.isEmpty() ? fallback : full;
    }

    /* ═══════════════════════════════════════════════════════
       1. MEMBRE AJOUTÉ AU PROJET → employé notifié
       ═══════════════════════════════════════════════════════ */

    /**
     * Notifie un employé qu'il a été ajouté à un projet.
     *
     * @param project    le projet auquel l'employé a été ajouté
     * @param employeeId identifiant Oracle de l'employé ajouté
     * @param chefId     identifiant Oracle du chef de projet ayant effectué l'ajout
     */
    public void notifierMembreAjoute(Project project, Long employeeId, Long chefId) {
        EmployeDTO emp  = employeService.getEmployeById(employeeId);
        EmployeDTO chef = employeService.getEmployeById(chefId);
        String chefNom  = nomComplet(chef, "votre chef");

        emit(
                employeeId,
                emp != null ? emp.getEmail() : null,
                "INFO",
                "Vous avez été ajouté à un projet",
                chefNom + " vous a ajouté au projet « " + project.getName() + " ». "
                        + "Connectez-vous pour consulter les détails et vos tâches.",
                project.getProjectId(),
                chefId,
                REF_TYPE,
                "/employe/projets"
        );
    }

    /* ═══════════════════════════════════════════════════════
       3. TÂCHE ASSIGNÉE → employé assigné notifié
       ═══════════════════════════════════════════════════════ */

    /**
     * Notifie l'employé assigné qu'une nouvelle tâche lui a été confiée.
     * <p>
     * Si la tâche n'a pas d'assigné ({@code assignedTo} est {@code null}), la méthode ne fait rien.
     * </p>
     *
     * @param task   la tâche nouvellement assignée
     * @param chefId identifiant Oracle du chef de projet ayant effectué l'assignation
     */
    public void notifierTacheAssignee(Task task, Long chefId) {
        if (task.getAssignedTo() == null) return;

        EmployeDTO emp  = employeService.getEmployeById(task.getAssignedTo());
        EmployeDTO chef = employeService.getEmployeById(chefId);
        String chefNom  = nomComplet(chef, "votre chef");
        String echeance = task.getDueDate() != null
                ? " (échéance : " + task.getDueDate() + ")" : "";

        emit(
                task.getAssignedTo(),
                emp != null ? emp.getEmail() : null,
                "NOUVELLE_DEMANDE",
                "Nouvelle tâche assignée",
                chefNom + " vous a assigné la tâche « " + task.getTitle() + " » "
                        + "dans le projet « " + task.getProject().getName() + " »" + echeance + ".",
                task.getProject().getProjectId(),
                chefId,
                TASK_REF,
                "/employe/taches"
        );
    }

    /* ═══════════════════════════════════════════════════════
       4. TÂCHE TERMINÉE → chef notifié
       ═══════════════════════════════════════════════════════ */

    /**
     * Notifie le chef de projet qu'un employé a terminé une tâche.
     *
     * @param task       la tâche marquée comme terminée
     * @param employeeId identifiant Oracle de l'employé ayant terminé la tâche
     */
    public void notifierTacheTerminee(Task task, Long employeeId) {
        Long chefId = task.getProject().getCreatedBy();
        EmployeDTO emp  = employeService.getEmployeById(employeeId);
        String empNom   = nomComplet(emp, "Un employé");

        emit(
                chefId,
                null, // le chef reçoit la notif in-app, email optionnel
                "DEMANDE_APPROUVEE",
                "Tâche terminée",
                empNom + " a marqué la tâche « " + task.getTitle() + " » comme terminée "
                        + "dans le projet « " + task.getProject().getName() + " ».",
                task.getProject().getProjectId(),
                employeeId,
                TASK_REF,
                "/chef/projets"
        );
    }

    /* ═══════════════════════════════════════════════════════
       5. PROJET TERMINÉ → tous les membres notifiés
       ═══════════════════════════════════════════════════════ */

    /**
     * Notifie tous les membres de l'équipe (y compris le chef) que le projet est terminé.
     *
     * @param project   le projet terminé
     * @param chefId    identifiant Oracle du chef de projet
     * @param membreIds liste des identifiants Oracle de tous les membres du projet
     */
    public void notifierProjetTermine(Project project, Long chefId,
                                      java.util.List<Long> membreIds) {
        String message = "Le projet « " + project.getName() + " » est terminé. "
                + "Félicitations à toute l'équipe !";

        for (Long empId : membreIds) {
            if (empId.equals(chefId)) continue; // le chef reçoit une notif séparée
            EmployeDTO emp = employeService.getEmployeById(empId);
            emit(empId, emp != null ? emp.getEmail() : null,
                    "DEMANDE_APPROUVEE", "Projet terminé", message,
                    project.getProjectId(), chefId, REF_TYPE, "/employe/projets");
        }

        // Notif au chef
        EmployeDTO chef = employeService.getEmployeById(chefId);
        emit(chefId, chef != null ? chef.getEmail() : null,
                "DEMANDE_APPROUVEE", "Projet terminé",
                "Votre projet « " + project.getName() + " » est marqué comme terminé.",
                project.getProjectId(), chefId, REF_TYPE, "/chef/projets");
    }

    /* ═══════════════════════════════════════════════════════
       6. PROJET MODIFIÉ → membres notifiés
       ═══════════════════════════════════════════════════════ */

    /**
     * Notifie les membres actifs (hors chef) qu'un projet a été mis à jour.
     *
     * @param project   le projet modifié (avec les nouvelles valeurs de statut et avancement)
     * @param chefId    identifiant Oracle du chef de projet ayant effectué la modification
     * @param membreIds liste des identifiants Oracle des membres actifs du projet
     */
    public void notifierProjetModifie(Project project, Long chefId,
                                      java.util.List<Long> membreIds) {
        String message = "Le projet « " + project.getName() + " » a été mis à jour "
                + "(statut : " + project.getStatus() + ", avancement : "
                + project.getProgressPct() + "%).";

        for (Long empId : membreIds) {
            if (empId.equals(chefId)) continue;
            EmployeDTO emp = employeService.getEmployeById(empId);
            emit(empId, emp != null ? emp.getEmail() : null,
                    "INFO", "Projet mis à jour", message,
                    project.getProjectId(), chefId, REF_TYPE, "/employe/projets");
        }
    }

    /* ═══════════════════════════════════════════════════════
       HELPER CENTRAL — construit et envoie l'événement
       ═══════════════════════════════════════════════════════ */

    /**
     * Construit un {@link NotificationEvent} et le publie via {@link ProjectEventProducer}.
     *
     * @param recipientId  identifiant Oracle du destinataire (peut être {@code null} pour broadcast)
     * @param email        adresse email du destinataire (pour l'envoi SMTP complémentaire)
     * @param type         type sémantique de la notification (ex. {@code INFO}, {@code NOUVELLE_DEMANDE})
     * @param title        titre court de la notification
     * @param content      corps du message de notification
     * @param projectId    identifiant du projet de référence
     * @param triggeredBy  identifiant Oracle de l'utilisateur ayant déclenché l'événement
     * @param refType      type de référence métier ({@code PROJET} ou {@code TACHE})
     * @param actionUrl    URL Angular vers laquelle le destinataire est redirigé
     */
    private void emit(Long recipientId, String email,
                      String type, String title, String content,
                      Long projectId, Long triggeredBy, String refType,
                      String actionUrl) {
        NotificationEvent event = NotificationEvent.builder()
                .employeeId(recipientId)
                .email(email)
                .type(type)
                .title(title)
                .content(content)
                .referenceType(refType)
                .referenceId(projectId != null ? projectId.toString() : null)
                .actionUrl(actionUrl)
                .triggeredBy(triggeredBy)
                .sourceService(SOURCE)
                .sendEmail(email != null)
                .build();

        producer.emit(event);
    }
}