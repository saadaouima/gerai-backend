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
 * Repository Spring Data JPA pour la persistance et la récupération des notifications
 * depuis la table Oracle {@code NOTIFICATIONS}.
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche
 * d’accès aux données et active la traduction des exceptions JPA en exceptions Spring.
 * Étend {@link JpaRepository} pour bénéficier des opérations CRUD standard.
 * </p>
 * <p>
 * Les requêtes personnalisées combinent les notifications personnelles ({@code EMPLOYEE_ID})
 * et les broadcasts de rôle ({@code ROLE}) afin d’exposer une vue unifiée à l’employé connecté.
 * </p>
 *
 * @since 1.0
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /* ───────────────────────────────────────────── */
    /* ─── REQUÊTES DE LECTURE ─────────────────── */
    /* ───────────────────────────────────────────── */

    /**
     * Retourne toutes les notifications personnelles d’un employé,
     * triées par date de création décroissante.
     *
     * @param employeeId l’identifiant Oracle de l’employé destinataire
     * @return la liste des notifications personnelles de l’employé
     */
    List<Notification> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /**
     * Retourne les notifications personnelles de l’employé ainsi que les broadcasts
     * destinés à son rôle, triées par date de création décroissante.
     * Utilisé par l’endpoint principal {@code GET /api/notifications}.
     *
     * @param employeeId l’identifiant Oracle de l’employé destinataire
     * @param role       le rôle de l’employé (ADMIN, CHEF ou EMPLOYE) pour inclure les broadcasts
     * @return la liste unifiée des notifications personnelles et de rôle
     */
    @Query("SELECT n FROM Notification n WHERE n.employeeId = :employeeId OR n.role = :role ORDER BY n.createdAt DESC")
    List<Notification> findByEmployeeOrRole(@Param("employeeId") Long employeeId,
                                            @Param("role") String role);

    /**
     * Retourne uniquement les notifications non lues d’un employé,
     * triées par date de création décroissante.
     * Utilisé par l’endpoint {@code GET /api/notifications/unread}.
     *
     * @param employeeId l’identifiant Oracle de l’employé destinataire
     * @return la liste des notifications non lues de l’employé
     */
    List<Notification> findByEmployeeIdAndIsReadFalseOrderByCreatedAtDesc(Long employeeId);

    /**
     * Compte le nombre de notifications non lues d’un employé.
     * Utilisé pour alimenter le badge de notification de l’interface Angular.
     *
     * @param employeeId l’identifiant Oracle de l’employé
     * @return le nombre total de notifications non lues
     */
    long countByEmployeeIdAndIsReadFalse(Long employeeId);


    /* ───────────────────────────────────────────── */
    /* ─── MISE À JOUR ─────────────────────────── */
    /* ───────────────────────────────────────────── */

    /**
     * Marque toutes les notifications non lues de l’employé (personnelles et de son rôle)
     * comme lues en positionnant {@code isRead = true} et l’horodatage de lecture.
     *
     * @param employeeId l’identifiant Oracle de l’employé
     * @param role       le rôle de l’employé pour inclure les broadcasts non lus
     * @param readAt     l’horodatage de lecture à enregistrer
     * @return le nombre de lignes effectivement mises à jour
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
     * Supprime toutes les notifications personnelles d’un employé.
     * Utilisé par l’endpoint {@code DELETE /api/notifications}.
     *
     * @param employeeId l’identifiant Oracle de l’employé dont les notifications sont supprimées
     */
    @Transactional
    void deleteByEmployeeId(Long employeeId);

}