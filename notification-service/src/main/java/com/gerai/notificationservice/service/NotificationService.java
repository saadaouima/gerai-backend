package com.gerai.notificationservice.service;

import com.gerai.notificationservice.dto.CreateNotificationRequest;
import com.gerai.notificationservice.dto.NotificationDTO;
import com.gerai.notificationservice.entity.Notification;
import com.gerai.notificationservice.event.NotificationEvent;
import com.gerai.notificationservice.enums.TypeNotification;
import com.gerai.notificationservice.mapper.NotificationMapper;
import com.gerai.notificationservice.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service métier central du microservice notification-service.
 * <p>
 * {@code @Service} : enregistre ce bean comme composant de la couche service Spring.<br>
 * {@code @RequiredArgsConstructor} : injecte {@link NotificationRepository},
 * {@link NotificationMapper} et {@link EmailService} par constructeur Lombok.<br>
 * {@code @Slf4j} : fournit un logger Lombok pour tracer les opérations métier.
 * </p>
 * <p>
 * Responsabilités principales :
 * <ul>
 *   <li>Traitement des événements Kafka entrants : persistance + déclenchement email.</li>
 *   <li>Persistance des notifications broadcast (par rôle).</li>
 *   <li>Lecture des notifications par employé et par rôle.</li>
 *   <li>Création manuelle de notifications (interface d'administration).</li>
 *   <li>Gestion de l'état de lecture (marquer comme lue, compteur de badge).</li>
 *   <li>Suppression des notifications d'un employé.</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /** Repository JPA pour les opérations CRUD sur la table NOTIFICATIONS. */
    private final NotificationRepository notificationRepository;

    /** Mapper MapStruct pour les conversions entre entité, DTO et événement Kafka. */
    private final NotificationMapper     notificationMapper;

    /** Service d'envoi d'emails HTML via SMTP Gmail. */
    private final EmailService           emailService;

    /* ═══════════════════════════════════════
       TRAITEMENT KAFKA
       ═══════════════════════════════════════ */

    /**
     * Traite un événement de notification reçu depuis Kafka, le persiste en base de données
     * et déclenche l'envoi d'un email si l'adresse email du destinataire est fournie.
     * <p>
     * La conversion du champ {@code referenceId} (String Kafka) vers Long Oracle est sécurisée :
     * seuls les caractères numériques sont conservés, les valeurs trop longues sont tronquées
     * et les références purement alphanumériques sont converties en {@code null}.
     * </p>
     *
     * @param event l'événement Kafka à traiter ({@code null} ignoré silencieusement)
     * @return l'entité {@link Notification} persistée, ou {@code null} si l'événement
     *         est ignoré (null ou sans {@code employeeId})
     * @throws RuntimeException si la persistance en base de données échoue
     */
    @Transactional
    public Notification processNotificationEvent(NotificationEvent event) {

        if (event == null) {
            log.warn("[NotificationService] Event Kafka null ignoré.");
            return null;
        }

        log.info("[NotificationService] Event reçu | Source: {} | EmployeeId: {}",
                event.getSourceService(), event.getEmployeeId());

        if (event.getEmployeeId() == null) {
            log.warn("[NotificationService] EmployeeId absent. Event ignoré.");
            return null;
        }

        try {
            Notification notification = notificationMapper.toEntity(event);

            /* ── ADAPTATION POUR BASE DE DONNÉES (REFERENCE_ID NUMBER) ── */
            String rawRefId = event.getReferenceId();

            if (rawRefId != null && !rawRefId.trim().isEmpty()) {
                // 1. Extraire uniquement les chiffres
                String numericOnly = rawRefId.replaceAll("[^0-9]", "");

                try {
                    // 2. Vérifier si on a bien des chiffres après le nettoyage
                    if (!numericOnly.isEmpty()) {

                        // 3. Protection contre les nombres trop longs pour un Long (max 19 chiffres)
                        // Si c'est trop long, on tronque pour éviter le crash
                        if (numericOnly.length() > 18) {
                            numericOnly = numericOnly.substring(0, 18);
                        }

                        notification.setReferenceId(Long.parseLong(numericOnly));

                    } else {
                        // Cas où rawRefId était "ABC" -> numericOnly est ""
                        log.warn("[NotificationService] Aucun chiffre trouvé dans referenceId '{}'.", rawRefId);
                        notification.setReferenceId(null);
                    }
                } catch (NumberFormatException e) {
                    log.error("[NotificationService] Erreur critique de parsing pour : {}", numericOnly);
                    notification.setReferenceId(null);
                }
            } else {
                // Cas où c'est null ou vide ("")
                notification.setReferenceId(null);
            }
            /* ────────────────────────────────────────────────────────── */

            // Initialisation de l'état de lecture
            notification.setIsRead(false);

            Notification saved = notificationRepository.saveAndFlush(notification);

            log.info("[NotificationService] Notification sauvegardée | ID: {}",
                    saved.getNotificationId());

            // Envoi de l'email
            if (event.getEmail() != null && !event.getEmail().isBlank()) {
                try {
                    emailService.envoyerDepuisEvent(event);
                } catch (Exception ex) {
                    log.error("[NotificationService] Erreur envoi email : {}", ex.getMessage());
                }
            }

            return saved;

        } catch (Exception e) {
            log.error("[NotificationService] Erreur traitement Kafka : {}", e.getMessage(), e);
            throw e;
        }
    }
    /* ═══════════════════════════════════════
       BROADCAST — PERSISTANCE
       ═══════════════════════════════════════ */

    /**
     * Persiste une notification broadcast destinée à tous les utilisateurs d'un rôle.
     * <p>
     * Contrairement aux notifications personnelles, cette entité ne contient pas
     * d'identifiant d'employé ; elle est routée par rôle via WebSocket.
     * La conversion du {@code referenceId} suit la même logique de sécurisation
     * que {@link #processNotificationEvent(NotificationEvent)}.
     * </p>
     *
     * @param event l'événement Kafka de broadcast contenant le rôle cible
     * @return l'entité {@link Notification} broadcast persistée
     */
    @Transactional
    public Notification saveBroadcastNotification(NotificationEvent event) {
        TypeNotification typeEnum;
        try { typeEnum = TypeNotification.valueOf(event.getType()); }
        catch (Exception e) { typeEnum = TypeNotification.INFO; }

        Long refId = null;
        if (event.getReferenceId() != null) {
            String numeric = event.getReferenceId().replaceAll("[^0-9]", "");
            if (!numeric.isEmpty()) {
                try { refId = Long.parseLong(numeric.length() > 18 ? numeric.substring(0, 18) : numeric); }
                catch (NumberFormatException ignored) {}
            }
        }

        Notification notif = Notification.builder()
                .role(event.getRole())
                .type(typeEnum)
                .title(event.getTitle())
                .content(event.getContent())
                .referenceType(event.getReferenceType())
                .referenceId(refId)
                .actionUrl(event.getActionUrl())
                .isRead(false)
                .build();
        Notification saved = notificationRepository.saveAndFlush(notif);
        log.info("[NotificationService] Broadcast sauvegardé | role={} | id={}", event.getRole(), saved.getNotificationId());
        return saved;
    }

    /* ═══════════════════════════════════════
       LECTURE
       ═══════════════════════════════════════ */

    /**
     * Retourne toutes les notifications personnelles d'un employé,
     * triées par date de création décroissante.
     *
     * @param employeeId l'identifiant Oracle de l'employé destinataire
     * @return la liste des DTOs de notification de l'employé
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsByEmployee(Long employeeId) {
        return notificationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream()
                .map(notificationMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retourne les notifications personnelles de l'employé ainsi que les broadcasts
     * de son rôle, triés par date décroissante.
     * Utilisé par l'endpoint principal {@code GET /api/notifications}.
     *
     * @param employeeId l'identifiant Oracle de l'employé
     * @param role       le rôle de l'employé (ADMIN, CHEF, EMPLOYE)
     * @return la liste unifiée des DTOs de notification
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsByEmployeeAndRole(Long employeeId, String role) {
        return notificationRepository
                .findByEmployeeOrRole(employeeId, role)
                .stream()
                .map(notificationMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retourne uniquement les notifications non lues de l'employé,
     * triées par date décroissante.
     *
     * @param employeeId l'identifiant Oracle de l'employé
     * @return la liste des DTOs de notification non lues
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getUnreadNotifications(Long employeeId) {
        return notificationRepository
                .findByEmployeeIdAndIsReadFalseOrderByCreatedAtDesc(employeeId)
                .stream()
                .map(notificationMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Compte le nombre de notifications non lues pour alimenter le badge de l'interface Angular.
     *
     * @param employeeId l'identifiant Oracle de l'employé
     * @return le nombre total de notifications non lues de cet employé
     */
    @Transactional(readOnly = true)
    public long countUnread(Long employeeId) {
        return notificationRepository.countByEmployeeIdAndIsReadFalse(employeeId);
    }

    /* ═══════════════════════════════════════
       CRÉATION MANUELLE
       ═══════════════════════════════════════ */

    /**
     * Crée manuellement une notification depuis l'interface d'administration (RH/ADMIN).
     * L'horodatage de création est géré par Oracle ({@code DEFAULT SYSTIMESTAMP}).
     *
     * @param request les données de la notification à créer (validées par Bean Validation)
     * @return le DTO de la notification créée et persistée
     */
    @Transactional
    public NotificationDTO create(CreateNotificationRequest request) {
        Notification notification = notificationMapper.toEntity(request);
        notification.setIsRead(false);

        // On laisse Oracle gérer le CREATED_AT
        Notification saved = notificationRepository.saveAndFlush(notification);

        log.info("[NotificationService] Notification créée manuellement | ID: {}",
                saved.getNotificationId());
        return notificationMapper.toDTO(saved);
    }

    /* ═══════════════════════════════════════
       ACTIONS
       ═══════════════════════════════════════ */

    /**
     * Marque toutes les notifications non lues de l'employé (personnelles et broadcasts de rôle)
     * comme lues avec l'horodatage actuel.
     *
     * @param employeeId l'identifiant Oracle de l'employé
     * @param role       le rôle de l'employé pour inclure les broadcasts non lus
     * @return le nombre de notifications effectivement mises à jour
     */
    @Transactional
    public int markAllAsRead(Long employeeId, String role) {
        return notificationRepository.markAllAsReadByEmployeeOrRole(employeeId, role, LocalDateTime.now());
    }

    /**
     * Supprime une notification par son identifiant technique Oracle.
     *
     * @param id l'identifiant Oracle de la notification à supprimer
     */
    @Transactional
    public void deleteById(Long id) {
        notificationRepository.deleteById(id);
    }

    /**
     * Supprime toutes les notifications personnelles d'un employé.
     *
     * @param employeeId l'identifiant Oracle de l'employé dont les notifications sont supprimées
     */
    @Transactional
    public void deleteNotificationsByEmployee(Long employeeId) {
        notificationRepository.deleteByEmployeeId(employeeId);
    }

    /**
     * Marque une notification individuelle comme lue si elle ne l'est pas déjà,
     * en enregistrant l'horodatage de lecture.
     *
     * @param id l'identifiant Oracle de la notification à marquer comme lue
     * @return le DTO de la notification mise à jour
     * @throws jakarta.persistence.EntityNotFoundException si aucune notification
     *         n'existe avec cet identifiant
     */
    @Transactional
    public NotificationDTO markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Notification avec ID " + id + " non trouvée"));

        // Utilisation de getIsRead() qui fonctionne maintenant grâce au type Boolean
        if (notification.getIsRead() == null || !notification.getIsRead()) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
        }

        return notificationMapper.toDTO(notificationRepository.save(notification));
    }
}