package com.gerai.demandesservice.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository en lecture seule sur EMPLOYEES.
 * Utilisé pour résoudre l'employee_id Oracle depuis le JWT Keycloak.
 *
 * On n'a pas d'entité Employee complète ici — on projette juste les
 * champs nécessaires via des requêtes natives.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<com.gerai.demandesservice.model.EmployeeRef, Long> {

    /** Résolution depuis le UUID Keycloak (claim "sub") */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE USER_ID = :keycloakSub AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByKeycloakSub(@Param("keycloakSub") String keycloakSub);

    /** Résolution depuis l'email (fallback si sub non trouvé) */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE EMAIL = :email AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByEmail(@Param("email") String email);

    /** Résolution de l'employee_id du manager d'un employé */
    @Query(value = """
            SELECT MANAGER_ID FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    Long findManagerIdByEmployeeId(@Param("employeeId") Long employeeId);

    /** Email du manager (pour la notification Kafka) */
    @Query(value = """
            SELECT e2.EMAIL
            FROM GERAI.EMPLOYEES e1
            JOIN GERAI.EMPLOYEES e2 ON e1.MANAGER_ID = e2.EMPLOYEE_ID
            WHERE e1.EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findManagerEmailByEmployeeId(@Param("employeeId") Long employeeId);

    /** UUID Keycloak du manager (pour destinataireId dans NotificationMessage) */
    @Query(value = """
            SELECT e2.USER_ID
            FROM GERAI.EMPLOYEES e1
            JOIN GERAI.EMPLOYEES e2 ON e1.MANAGER_ID = e2.EMPLOYEE_ID
            WHERE e1.EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findManagerKeycloakSubByEmployeeId(@Param("employeeId") Long employeeId);

    /** Nom complet de l'employé */
    @Query(value = """
            SELECT FIRST_NAME || ' ' || LAST_NAME
            FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findFullNameByEmployeeId(@Param("employeeId") Long employeeId);

    /** Tous les employés RH/admin destinataires des notifications (crédit, document). */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE STATUS = 'ACTIF'
              AND (
                   UPPER(JOB_TITLE) LIKE '%ADMINISTRATEUR%'
                OR UPPER(JOB_TITLE) LIKE '%ADMIN%'
                OR UPPER(JOB_TITLE) LIKE '%RESPONSABLE RH%'
                OR UPPER(JOB_TITLE) LIKE '%RESSOURCES HUMAINES%'
                OR UPPER(JOB_TITLE) LIKE '%DRH%'
                OR UPPER(JOB_TITLE) LIKE '%DIRECTEUR RH%'
                OR UPPER(JOB_TITLE) LIKE '%RH%'
              )
            """, nativeQuery = true)
    java.util.List<Long> findAdminEmployeeIds();

    /** Email d'un employé par son ID */
    @Query(value = """
            SELECT EMAIL FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findEmailByEmployeeId(@Param("employeeId") Long employeeId);

    /** Département d'un employé (fallback quand MANAGER_ID n'est pas renseigné) */
    @Query(value = """
            SELECT DEPT_ID FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    Long findDeptIdByEmployeeId(@Param("employeeId") Long employeeId);

    /** Tous les employés actifs d'un département (fallback manager) */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE DEPT_ID = :deptId AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    java.util.List<Long> findEmployeeIdsByDeptId(@Param("deptId") Long deptId);

    /**
     * Fallback notification via PROJECT_MEMBERS :
     * renvoie le CREATED_BY du premier projet actif auquel l'employé appartient.
     * Cohérent avec TrainingRequestRepository.findByManager.
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
     * Fallback notification : trouve le chef du même département
     * (JOB_TITLE contient 'CHEF' ou 'MANAGER'), quand les deux sources ci-dessus échouent.
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

    /** Salaire mensuel depuis le contrat actif (pour la validation du montant crédit) */
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