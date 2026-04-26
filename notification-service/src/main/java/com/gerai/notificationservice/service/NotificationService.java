package com.gerai.notificationservice.service;

import com.gerai.notificationservice.dto.CreateNotificationRequest;
import com.gerai.notificationservice.dto.NotificationDTO;
import com.gerai.notificationservice.entity.Notification;
import com.gerai.notificationservice.event.NotificationEvent;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper     notificationMapper;
    private final EmailService           emailService;

    /* ═══════════════════════════════════════
       TRAITEMENT KAFKA
       ═══════════════════════════════════════ */

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
       LECTURE
       ═══════════════════════════════════════ */

    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsByEmployee(Long employeeId) {
        return notificationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream()
                .map(notificationMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getUnreadNotifications(Long employeeId) {
        return notificationRepository
                .findByEmployeeIdAndIsReadFalseOrderByCreatedAtDesc(employeeId)
                .stream()
                .map(notificationMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countUnread(Long employeeId) {
        return notificationRepository.countByEmployeeIdAndIsReadFalse(employeeId);
    }

    /* ═══════════════════════════════════════
       CRÉATION MANUELLE
       ═══════════════════════════════════════ */

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

    @Transactional
    public int markAllAsRead(Long employeeId) {
        return notificationRepository.markAllAsReadByEmployee(employeeId, LocalDateTime.now());
    }

    @Transactional
    public void deleteNotificationsByEmployee(Long employeeId) {
        notificationRepository.deleteByEmployeeId(employeeId);
    }

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