package com.gerai.demandesservice.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository Spring Data JPA en lecture seule sur {@code GERAI.EMPLOYEES}.
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche données.
 * <p>
 * Utilisé exclusivement pour résoudre les identités Oracle depuis le JWT Keycloak
 * (sub → EMPLOYEE_ID, email → EMPLOYEE_ID) et pour les lookups de notifications.
 * Toutes les requêtes sont des requêtes natives Oracle afin d'éviter de mapper
 * l'intégralité de la table EMPLOYEES dans ce microservice.
 *
 * @since 1.0
 */
@Repository
public interface EmployeeRepository extends JpaRepository<com.gerai.demandesservice.model.EmployeeRef, Long> {

    /**
     * Résout l'identifiant Oracle d'un employé depuis son UUID Keycloak (claim {@code sub}).
     *
     * @param keycloakSub UUID Keycloak de l'utilisateur
     * @return identifiant Oracle de l'employé, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE USER_ID = :keycloakSub AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByKeycloakSub(@Param("keycloakSub") String keycloakSub);

    /**
     * Résout l'identifiant Oracle d'un employé depuis son adresse email
     * (fallback si {@code sub} non trouvé).
     *
     * @param email adresse email de l'employé
     * @return identifiant Oracle de l'employé, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE EMAIL = :email AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByEmail(@Param("email") String email);

    /**
     * Retourne l'identifiant Oracle du manager direct d'un employé (champ {@code MANAGER_ID}).
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return identifiant Oracle du manager, ou {@code null} si non renseigné
     */
    @Query(value = """
            SELECT MANAGER_ID FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    Long findManagerIdByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Retourne l'adresse email du manager direct d'un employé (utilisé pour les notifications Kafka).
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return email du manager, ou {@code null} si le manager n'est pas renseigné ou n'a pas d'email
     */
    @Query(value = """
            SELECT e2.EMAIL
            FROM GERAI.EMPLOYEES e1
            JOIN GERAI.EMPLOYEES e2 ON e1.MANAGER_ID = e2.EMPLOYEE_ID
            WHERE e1.EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findManagerEmailByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Retourne l'UUID Keycloak ({@code USER_ID}) du manager direct d'un employé.
     * Utilisé pour le champ {@code destinataireId} dans les événements de notification.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return UUID Keycloak du manager, ou {@code null} si absent
     */
    @Query(value = """
            SELECT e2.USER_ID
            FROM GERAI.EMPLOYEES e1
            JOIN GERAI.EMPLOYEES e2 ON e1.MANAGER_ID = e2.EMPLOYEE_ID
            WHERE e1.EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findManagerKeycloakSubByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Retourne le nom complet d'un employé ({@code FIRST_NAME || ' ' || LAST_NAME}).
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return nom complet de l'employé, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT FIRST_NAME || ' ' || LAST_NAME
            FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findFullNameByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Retourne les identifiants Oracle de tous les employés actifs ayant un rôle RH ou Admin
     * (détecté via le champ {@code JOB_TITLE}).
     * Utilisé pour diffuser les notifications de nouvelles demandes aux gestionnaires.
     *
     * @return liste des identifiants Oracle des administrateurs RH actifs
     */
    @Query(value = """
            SELECT e.EMPLOYEE_ID
            FROM GERAI.EMPLOYEES e
            JOIN GERAI.POSITIONS p ON e.POSITION_ID = p.POSITION_ID
            WHERE e.STATUS = 'ACTIF'
              AND (
                   UPPER(p.TITLE) LIKE '%ADMINISTRATEUR%'
                OR UPPER(p.TITLE) LIKE '%ADMIN%'
                OR UPPER(p.TITLE) LIKE '%RESPONSABLE RH%'
                OR UPPER(p.TITLE) LIKE '%RESSOURCES HUMAINES%'
                OR UPPER(p.TITLE) LIKE '%DRH%'
                OR UPPER(p.TITLE) LIKE '%DIRECTEUR RH%'
                OR UPPER(p.TITLE) LIKE '%RH%'
                OR UPPER(p.CODE)  LIKE 'RH%'
              )
            """, nativeQuery = true)
    java.util.List<Long> findAdminEmployeeIds();

    @Query(value = """
            SELECT e.EMPLOYEE_ID
            FROM GERAI.EMPLOYEES e
            JOIN GERAI.POSITIONS p ON e.POSITION_ID = p.POSITION_ID
            WHERE e.STATUS = 'ACTIF'
              AND (
                   UPPER(p.TITLE) LIKE '%DIRECTEUR%'
                OR UPPER(p.CODE) = 'DG-001'
              )
            """, nativeQuery = true)
    java.util.List<Long> findDgEmployeeIds();

    /**
     * Retourne l'adresse email d'un employé par son identifiant Oracle.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return adresse email de l'employé, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT EMAIL FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findEmailByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Retourne l'identifiant du département d'un employé.
     * Utilisé comme fallback de résolution hiérarchique quand {@code MANAGER_ID} n'est pas renseigné.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return identifiant Oracle du département (DEPARTMENTS.DEPT_ID), ou {@code null}
     */
    @Query(value = """
            SELECT DEPT_ID FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    Long findDeptIdByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Retourne les identifiants Oracle de tous les employés actifs d'un département.
     * Utilisé comme fallback de résolution d'équipe quand {@code MANAGER_ID} n'est pas renseigné.
     *
     * @param deptId identifiant Oracle du département (DEPARTMENTS.DEPT_ID)
     * @return liste des identifiants Oracle des employés actifs du département
     */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE DEPT_ID = :deptId AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    java.util.List<Long> findEmployeeIdsByDeptId(@Param("deptId") Long deptId);

    /**
     * Fallback de résolution du manager via la table {@code GERAI.PROJECT_MEMBERS} :
     * retourne le {@code CREATED_BY} du premier projet actif auquel l'employé appartient.
     * Cohérent avec la logique de {@code TrainingRequestRepository.findByManager}.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return identifiant Oracle du chef de projet, ou {@code null} si l'employé n'appartient à aucun projet actif
     */
    @Query(value = """
            SELECT p.CREATED_BY
            FROM GERAI.PROJECT_MEMBERS pm
            JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
            WHERE pm.EMPLOYEE_ID = :employeeId
              AND pm.IS_ACTIVE = 1
              AND ROWNUM = 1
            """, nativeQuery = true)
    Long findManagerIdViaProjectByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Fallback de résolution du manager via le département :
     * retourne le premier employé actif du même département dont le {@code JOB_TITLE}
     * contient {@code 'CHEF'} ou {@code 'MANAGER'}.
     * Utilisé quand {@code MANAGER_ID} et les projets ne permettent pas de résoudre le chef.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return identifiant Oracle du chef de département, ou {@code null} si aucun chef trouvé
     */
    @Query(value = """
            SELECT e2.EMPLOYEE_ID
            FROM GERAI.EMPLOYEES e1
            JOIN GERAI.EMPLOYEES e2 ON e1.DEPT_ID = e2.DEPT_ID
            WHERE e1.EMPLOYEE_ID = :employeeId
              AND e1.EMPLOYEE_ID != e2.EMPLOYEE_ID
              AND (UPPER(e2.JOB_TITLE) LIKE '%CHEF%' OR UPPER(e2.JOB_TITLE) LIKE '%MANAGER%')
              AND e2.STATUS = 'ACTIF'
              AND ROWNUM = 1
            """, nativeQuery = true)
    Long findChefInSameDeptByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Retourne le salaire brut mensuel de l'employé depuis son dernier contrat actif
     * (table {@code GERAI.CONTRACTS}, champ {@code GROSS_SALARY}).
     * Utilisé par {@link com.gerai.demandesservice.service.SalaryValidationService}
     * pour valider que le montant d'un crédit ne dépasse pas 3× le salaire mensuel.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return salaire brut mensuel en TND, ou {@code 0} si aucun contrat trouvé
     */
    @Query(value = """
            SELECT NVL(c.GROSS_SALARY, 0)
            FROM GERAI.CONTRACTS c
            WHERE c.EMPLOYEE_ID = :employeeId
            AND c.START_DATE = (
                SELECT MAX(c2.START_DATE) FROM GERAI.CONTRACTS c2
                WHERE c2.EMPLOYEE_ID = :employeeId
            )
            AND ROWNUM = 1
            """, nativeQuery = true)
    java.math.BigDecimal findCurrentSalaryByEmployeeId(@Param("employeeId") Long employeeId);
}