// ═══════════════════════════════════════════════════════════════════════════
//  LeaveRequestRepository.java
// ═══════════════════════════════════════════════════════════════════════════
package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository Spring Data JPA pour les demandes de congé (table {@code GERAI.LEAVE_REQUESTS}).
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche données.
 * Fournit des requêtes natives Oracle pour la résolution hiérarchique, le calcul de quotas,
 * le calendrier équipe et la détection des violations SLA.
 *
 * @since 1.0
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /**
     * Retourne les demandes de congé d'un employé, triées par date de création décroissante.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return liste des demandes de congé de l'employé
     */
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /**
     * Retourne les demandes de congé ayant un statut donné, triées par date décroissante.
     *
     * @param status valeur Oracle du statut (ex : {@code EN_ATTENTE}, {@code VALIDE_RH})
     * @return liste des demandes correspondant au statut
     */
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Retourne les demandes de congé dont le statut est dans une liste donnée, triées par date décroissante.
     *
     * @param statuses liste des valeurs Oracle de statuts recherchés
     * @return liste des demandes correspondant à l'un des statuts
     */
    List<LeaveRequest> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    /* ── Hiérarchie (MANAGER_ID) ── */

    /**
     * Retourne les demandes de congé des membres directs de l'équipe d'un chef (via {@code MANAGER_ID}),
     * triées par date de création décroissante.
     *
     * @param chefId identifiant Oracle du chef (EMPLOYEES.MANAGER_ID)
     * @return liste des demandes de congé de l'équipe hiérarchique directe
     */
    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerViaHierarchy(@Param("chefId") Long chefId);

    /**
     * Retourne les demandes de congé d'un statut donné pour les membres directs d'un chef (via {@code MANAGER_ID}).
     *
     * @param chefId identifiant Oracle du chef
     * @param status valeur Oracle du statut à filtrer (ex : {@code EN_ATTENTE})
     * @return liste filtrée par statut des demandes de l'équipe hiérarchique
     */
    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
              AND lr.STATUS = :status
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerViaHierarchyAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── Projet (PROJECT_MEMBERS) ── */

    /**
     * Retourne les demandes de congé des membres actifs d'un projet créé par le chef donné
     * (via {@code PROJECT_MEMBERS}), triées par date de création décroissante.
     *
     * @param chefId identifiant Oracle du chef (PROJECTS.CREATED_BY)
     * @return liste des demandes de congé des membres de projet actifs
     */
    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            WHERE lr.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerViaProject(@Param("chefId") Long chefId);

    /**
     * Retourne les demandes de congé d'un statut donné pour les membres actifs d'un projet
     * créé par le chef donné (via {@code PROJECT_MEMBERS}).
     *
     * @param chefId identifiant Oracle du chef
     * @param status valeur Oracle du statut à filtrer (ex : {@code EN_ATTENTE})
     * @return liste filtrée par statut des demandes des membres du projet
     */
    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            WHERE lr.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
              AND lr.STATUS = :status
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerViaProjectAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── Compatibilité appels existants — merge hiérarchie + projet ── */

    /**
     * Retourne toutes les demandes de congé de l'équipe d'un chef,
     * en fusionnant les résultats par hiérarchie (MANAGER_ID) et par projet (PROJECT_MEMBERS).
     *
     * @param chefId identifiant Oracle du chef
     * @return liste dédupliquée des demandes de congé de l'équipe
     */
    default List<LeaveRequest> findByManager(Long chefId) {
        java.util.Map<Long, LeaveRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchy(chefId).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProject(chefId).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    /**
     * Retourne les demandes de congé de l'équipe d'un chef ayant un statut donné,
     * en fusionnant hiérarchie et projet.
     *
     * @param chefId identifiant Oracle du chef
     * @param status valeur Oracle du statut à filtrer
     * @return liste dédupliquée des demandes de congé filtrées par statut
     */
    default List<LeaveRequest> findByManagerAndStatus(Long chefId, String status) {
        java.util.Map<Long, LeaveRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchyAndStatus(chefId, status).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProjectAndStatus(chefId, status).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    /* ── Calendrier équipe ── */

    /**
     * Retourne les demandes de congé de l'équipe d'un chef qui chevauchent une plage de dates.
     * Les congés refusés ({@code REFUSE}) et annulés ({@code ANNULE}) sont exclus.
     *
     * @param chefId    identifiant Oracle du chef (EMPLOYEES.MANAGER_ID)
     * @param dateDebut date de début de la plage au format {@code YYYY-MM-DD}
     * @param dateFin   date de fin de la plage au format {@code YYYY-MM-DD}
     * @return liste des congés chevauchant la plage, triés par date de début croissante
     */
    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
              AND lr.STATUS NOT IN ('REFUSE', 'ANNULE')
              AND lr.START_DATE <= TO_DATE(:dateFin,  'YYYY-MM-DD')
              AND lr.END_DATE   >= TO_DATE(:dateDebut, 'YYYY-MM-DD')
            ORDER BY lr.START_DATE
            """, nativeQuery = true)
    List<LeaveRequest> findByManagerAndDateRange(
            @Param("chefId")    Long   chefId,
            @Param("dateDebut") String dateDebut,
            @Param("dateFin")   String dateFin);

    /**
     * Calcule la somme des jours de congé approuvés par RH (statut {@code VALIDE_RH})
     * pour un employé et une année civile donnée.
     * Utilisée par {@link com.gerai.demandesservice.service.DemandeService#getCongesSolde} pour calculer le solde.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @param year       année civile de référence
     * @return somme des jours approuvés, ou {@code 0} si aucune demande
     */
    @Query(value = """
            SELECT NVL(SUM(DAYS_COUNT), 0)
            FROM GERAI.LEAVE_REQUESTS
            WHERE EMPLOYEE_ID = :employeeId
              AND STATUS = 'VALIDE_RH'
              AND EXTRACT(YEAR FROM START_DATE) = :year
            """, nativeQuery = true)
    BigDecimal sumApprovedDaysByYear(
            @Param("employeeId") Long employeeId,
            @Param("year")       int  year);

    /**
     * Compte le nombre de demandes de congé ayant un statut donné pour un employé.
     * Utilisé pour calculer le champ {@code demandesEnAttente} du solde de congés.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @param status     valeur Oracle du statut à compter (ex : {@code EN_ATTENTE})
     * @return nombre de demandes correspondant au statut pour cet employé
     */
    long countByEmployeeIdAndStatus(Long employeeId, String status);

    /**
     * Calcule la somme des jours de congé en cours (pending + approuvés) pour un type et une année donnée.
     * Utilisée par {@link com.gerai.demandesservice.service.LeaveQuotaService#checkAnnualQuota}
     * pour vérifier le quota avant création d'une nouvelle demande.
     * Les statuts {@code REFUSE} et {@code ANNULE} sont exclus du calcul.
     *
     * @param employeeId  identifiant Oracle de l'employé
     * @param leaveTypeId identifiant du type de congé
     * @param year        année civile de référence
     * @return somme des jours utilisés ou en attente pour ce type dans l'année
     */
    @Query(value = """
            SELECT NVL(SUM(DAYS_COUNT), 0)
            FROM GERAI.LEAVE_REQUESTS
            WHERE EMPLOYEE_ID    = :employeeId
              AND LEAVE_TYPE_ID  = :leaveTypeId
              AND STATUS NOT IN ('REFUSE', 'ANNULE')
              AND EXTRACT(YEAR FROM START_DATE) = :year
            """, nativeQuery = true)
    BigDecimal sumPendingAndApprovedDaysByTypeAndYear(
            @Param("employeeId")   Long employeeId,
            @Param("leaveTypeId")  Long leaveTypeId,
            @Param("year")         int  year);

    /**
     * Retourne toutes les demandes de congé ayant exactement le statut Oracle donné.
     * Utilisé par {@link com.gerai.demandesservice.service.DemandeService#getCongesParStatut}.
     *
     * @param status valeur Oracle du statut (ex : {@code EN_ETUDE_MEDICALE})
     * @return liste des demandes correspondant au statut
     */
    List<LeaveRequest> findByStatus(String status);

    /**
     * Fallback département : retourne toutes les demandes de congé d'une liste d'employés.
     * Utilisé quand {@code MANAGER_ID} n'est pas renseigné pour le chef.
     *
     * @param employeeIds liste des identifiants Oracle des employés du département
     * @return liste des demandes de congé de ces employés
     */
    List<LeaveRequest> findByEmployeeIdIn(java.util.List<Long> employeeIds);

    /**
     * Retourne les demandes de congé restées en attente depuis plus d'un seuil horaire donné
     * (violation SLA). Utilisé par {@link com.gerai.demandesservice.scheduler.SlaBreachScheduler}.
     *
     * @param seuil horodatage limite — toute demande créée avant ce seuil est considérée en violation SLA
     * @return liste des demandes de congé en violation SLA, triées par date de création croissante
     */
    @Query(value = """
            SELECT lr.* FROM GERAI.LEAVE_REQUESTS lr
            WHERE lr.STATUS = 'EN_ATTENTE'
              AND lr.CREATED_AT < :seuil
            ORDER BY lr.CREATED_AT ASC
            """, nativeQuery = true)
    List<LeaveRequest> findEnAttentePlusDe(@Param("seuil") LocalDateTime seuil);
}