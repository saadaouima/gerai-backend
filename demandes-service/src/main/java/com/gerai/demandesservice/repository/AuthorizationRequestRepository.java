package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.AuthorizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository Spring Data JPA pour les demandes d'autorisation d'absence
 * (table {@code GERAI.AUTHORIZATION_REQUESTS}).
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche données.
 * Fournit des requêtes natives Oracle pour la résolution hiérarchique et projet.
 *
 * @since 1.0
 */
@Repository
public interface AuthorizationRequestRepository extends JpaRepository<AuthorizationRequest, Long> {

    /* ── REQUÊTES STANDARDS ── */

    /**
     * Retourne les demandes d'autorisation d'un employé, triées par date de création décroissante.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return liste des demandes d'autorisation de l'employé
     */
    List<AuthorizationRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /**
     * Retourne les demandes d'autorisation ayant un statut donné, triées par date de création décroissante.
     *
     * @param status valeur Oracle du statut (ex : {@code EN_ATTENTE}, {@code APPROUVE})
     * @return liste des demandes d'autorisation correspondant au statut
     */
    List<AuthorizationRequest> findByStatusOrderByCreatedAtDesc(String status);

    /* ── HIÉRARCHIE (MANAGER_ID) ── */

    /**
     * Retourne les demandes d'autorisation des membres directs de l'équipe d'un chef
     * (via {@code MANAGER_ID}), triées par date de création décroissante.
     *
     * @param chefId identifiant Oracle du chef (EMPLOYEES.MANAGER_ID)
     * @return liste des demandes d'autorisation de l'équipe hiérarchique directe
     */
    @Query(value = """
            SELECT ar.* FROM GERAI.AUTHORIZATION_REQUESTS ar
            JOIN GERAI.EMPLOYEES e ON ar.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerViaHierarchy(@Param("chefId") Long chefId);

    /**
     * Retourne les demandes d'autorisation d'un statut donné pour les membres directs
     * d'un chef (via {@code MANAGER_ID}).
     *
     * @param chefId identifiant Oracle du chef
     * @param status valeur Oracle du statut à filtrer (ex : {@code EN_ATTENTE})
     * @return liste filtrée par statut des demandes d'autorisation de l'équipe hiérarchique
     */
    @Query(value = """
            SELECT ar.* FROM GERAI.AUTHORIZATION_REQUESTS ar
            JOIN GERAI.EMPLOYEES e ON ar.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.MANAGER_ID = :chefId
              AND ar.STATUS = :status
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerViaHierarchyAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── PROJET (PROJECT_MEMBERS) ── */

    /**
     * Retourne les demandes d'autorisation des membres actifs d'un projet créé par le chef donné
     * (via {@code PROJECT_MEMBERS}), triées par date de création décroissante.
     *
     * @param chefId identifiant Oracle du chef (PROJECTS.CREATED_BY)
     * @return liste des demandes d'autorisation des membres de projet actifs
     */
    @Query(value = """
            SELECT ar.* FROM GERAI.AUTHORIZATION_REQUESTS ar
            WHERE ar.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerViaProject(@Param("chefId") Long chefId);

    /**
     * Retourne les demandes d'autorisation d'un statut donné pour les membres actifs d'un projet
     * créé par le chef donné (via {@code PROJECT_MEMBERS}).
     *
     * @param chefId identifiant Oracle du chef
     * @param status valeur Oracle du statut à filtrer (ex : {@code EN_ATTENTE})
     * @return liste filtrée par statut des demandes d'autorisation des membres du projet
     */
    @Query(value = """
            SELECT ar.* FROM GERAI.AUTHORIZATION_REQUESTS ar
            WHERE ar.EMPLOYEE_ID IN (
              SELECT DISTINCT pm.EMPLOYEE_ID
              FROM GERAI.PROJECT_MEMBERS pm
              JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
              WHERE p.CREATED_BY = :chefId AND pm.IS_ACTIVE = 1
            )
              AND ar.STATUS = :status
            ORDER BY ar.CREATED_AT DESC
            """, nativeQuery = true)
    List<AuthorizationRequest> findByManagerViaProjectAndStatus(
            @Param("chefId") Long chefId,
            @Param("status") String status);

    /* ── Compatibilité appels existants — merge hiérarchie + projet ── */

    /**
     * Retourne toutes les demandes d'autorisation de l'équipe d'un chef,
     * en fusionnant les résultats par hiérarchie (MANAGER_ID) et par projet (PROJECT_MEMBERS).
     * Les doublons sont dédupliqués en faveur du premier résultat trouvé.
     *
     * @param chefId identifiant Oracle du chef
     * @return liste dédupliquée des demandes d'autorisation de l'équipe du chef
     */
    default List<AuthorizationRequest> findByManager(Long chefId) {
        java.util.Map<Long, AuthorizationRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchy(chefId).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProject(chefId).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    /**
     * Retourne les demandes d'autorisation de l'équipe d'un chef ayant un statut donné,
     * en fusionnant hiérarchie et projet.
     *
     * @param chefId identifiant Oracle du chef
     * @param status valeur Oracle du statut à filtrer
     * @return liste dédupliquée des demandes d'autorisation filtrées par statut
     */
    default List<AuthorizationRequest> findByManager(Long chefId, String status) {
        java.util.Map<Long, AuthorizationRequest> seen = new java.util.LinkedHashMap<>();
        findByManagerViaHierarchyAndStatus(chefId, status).forEach(e -> seen.put(e.getRequestId(), e));
        findByManagerViaProjectAndStatus(chefId, status).forEach(e -> seen.putIfAbsent(e.getRequestId(), e));
        return new java.util.ArrayList<>(seen.values());
    }

    /**
     * Alias de {@link #findByManager(Long, String)} pour la compatibilité des appels existants.
     *
     * @param chefId identifiant Oracle du chef
     * @param status valeur Oracle du statut à filtrer
     * @return liste dédupliquée des demandes d'autorisation filtrées par statut
     */
    default List<AuthorizationRequest> findByManagerAndStatus(Long chefId, String status) {
        return findByManager(chefId, status);
    }

    /**
     * Fallback département : retourne toutes les demandes d'autorisation d'une liste d'employés.
     * Utilisé quand MANAGER_ID n'est pas renseigné.
     *
     * @param employeeIds liste des identifiants Oracle des employés du département
     * @return liste des demandes d'autorisation de ces employés
     */
    List<AuthorizationRequest> findByEmployeeIdIn(java.util.List<Long> employeeIds);
}
