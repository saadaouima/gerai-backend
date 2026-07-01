package com.gerai_backend.gerai.services;

import com.gerai_backend.gerai.dto.NotificationEvent;
import com.gerai_backend.gerai.models.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Producteur Kafka chargé de publier des événements de notification lors de modifications
 * des employés (création, départ). Les événements sont envoyés vers le topic
 * {@code notification-events} et consommés par le {@code notification-service}.
 *
 * <p>@Service : enregistré comme bean Spring et injecté dans {@link EmployeeService}.</p>
 * <p>La publication est effectuée en mode "fire-and-forget" : une indisponibilité Kafka
 * est journalisée mais ne fait pas échouer l'opération principale.</p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeEventProducer {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    /** Nom du topic Kafka vers lequel les événements de notification sont publiés. */
    @Value("${app.kafka.topic.notifications}")
    private String topic;

    /** Identifiant du service source inclus dans chaque événement Kafka publié. */
    private static final String SOURCE = "EMPLOYE-SERVICE";

    /**
     * Publie un événement Kafka informant les administrateurs qu'un nouvel employé
     * a été créé sur la plateforme.
     *
     * @param emp l'entité {@link Employee} nouvellement créée
     */
    public void notifierNouvelEmploye(Employee emp) {
        String nom = (emp.getFirstName() != null ? emp.getFirstName() : "") + " "
                   + (emp.getLastName()  != null ? emp.getLastName()  : "");
        emit(NotificationEvent.builder()
                .role("ADMIN")
                .type("INFO")
                .title("Nouvel employé créé")
                .content("L'employé " + nom.trim() + " (" + emp.getEmail() + ") a été ajouté à la plateforme.")
                .referenceType("EMPLOYE")
                .referenceId(emp.getId() != null ? emp.getId().toString() : null)
                .actionUrl("/admin/employes")
                .sourceService(SOURCE)
                .sendEmail(false)
                .build());
    }

    /**
     * Publie un événement Kafka informant les administrateurs qu'un employé
     * a quitté la plateforme (statut {@code DÉMISSION} ou suppression physique).
     *
     * @param emp l'entité {@link Employee} supprimée ou passée en statut {@code DEMISSION}
     */
    public void notifierDepartEmploye(Employee emp) {
        String nom = (emp.getFirstName() != null ? emp.getFirstName() : "") + " "
                   + (emp.getLastName()  != null ? emp.getLastName()  : "");
        emit(NotificationEvent.builder()
                .role("ADMIN")
                .type("INFO")
                .title("Départ d'un employé")
                .content("L'employé " + nom.trim() + " a quitté la plateforme (statut : DÉMISSION).")
                .referenceType("EMPLOYE")
                .referenceId(emp.getId() != null ? emp.getId().toString() : null)
                .actionUrl("/admin/employes")
                .sourceService(SOURCE)
                .sendEmail(false)
                .build());
    }

    /**
     * Envoie un événement vers le topic Kafka de notifications.
     * La clé du message est l'identifiant de l'employé ou le rôle ciblé.
     * Les erreurs Kafka sont journalisées mais n'interrompent pas l'opération principale.
     *
     * @param event l'événement de notification à publier
     */
    private void emit(NotificationEvent event) {
        String key = event.getEmployeeId() != null
                ? String.valueOf(event.getEmployeeId())
                : "role-" + event.getRole();
        try {
            kafkaTemplate.send(topic, key, event).whenComplete((r, ex) -> {
                if (ex != null) log.warn("[EmployeeEventProducer] Kafka send failed: {}", ex.getMessage());
                else log.info("[EmployeeEventProducer] Event published: {}", event.getType());
            });
        } catch (Exception e) {
            log.warn("[EmployeeEventProducer] Kafka unavailable, notification skipped: {}", e.getMessage());
        }
    }
}
