package com.gerai.notificationservice.repository;

import com.gerai.notificationservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository Oracle pour la gestion des notifications.
 * Adapté à la nouvelle structure basée sur EMPLOYEE_ID.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /* ───────────────────────────────────────────── */
    /* ─── REQUÊTES DE LECTURE ─────────────────── */
    /* ───────────────────────────────────────────── */

    /**
     * Notifications d’un employé (triées par date décroissante).
     */
    List<Notification> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /**
     * Notifications personnelles + broadcasts pour ce rôle (triées par date décroissante).
     */
    @Query("SELECT n FROM Notification n WHERE n.employeeId = :employeeId OR n.role = :role ORDER BY n.createdAt DESC")
    List<Notification> findByEmployeeOrRole(@Param("employeeId") Long employeeId,
                                            @Param("role") String role);

    /**
     * Notifications NON LUES d’un employé.
     */
    List<Notification> findByEmployeeIdAndIsReadFalseOrderByCreatedAtDesc(Long employeeId);

    /**
     * Compter les notifications non lues.
     */
    long countByEmployeeIdAndIsReadFalse(Long employeeId);


    /* ───────────────────────────────────────────── */
    /* ─── MISE À JOUR ─────────────────────────── */
    /* ───────────────────────────────────────────── */

    /**
     * Marquer toutes les notifications non lues comme lues.
     * Retourne le nombre de lignes impactées.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
           UPDATE Notification n
           SET n.isRead = true,
               n.readAt = :readAt
           WHERE (n.employeeId = :employeeId OR n.role = :role)
             AND n.isRead = false
           """)
    int markAllAsReadByEmployeeOrRole(@Param("employeeId") Long employeeId,
                                      @Param("role") String role,
                                      @Param("readAt") LocalDateTime readAt);


    /* ───────────────────────────────────────────── */
    /* ─── SUPPRESSION ─────────────────────────── */
    /* ───────────────────────────────────────────── */

    /**
     * Supprimer toutes les notifications d’un employé.
     */
    @Transactional
    void deleteByEmployeeId(Long employeeId);

}