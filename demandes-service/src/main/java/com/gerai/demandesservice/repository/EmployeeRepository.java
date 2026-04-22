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
            SELECT EMPLOYEE_ID FROM GERAI_USER.EMPLOYEES
            WHERE USER_ID = :keycloakSub AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByKeycloakSub(@Param("keycloakSub") String keycloakSub);

    /** Résolution depuis l'email (fallback si sub non trouvé) */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI_USER.EMPLOYEES
            WHERE EMAIL = :email AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByEmail(@Param("email") String email);

    /** Résolution de l'employee_id du manager d'un employé */
    @Query(value = """
            SELECT MANAGER_ID FROM GERAI_USER.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    Long findManagerIdByEmployeeId(@Param("employeeId") Long employeeId);

    /** Email du manager (pour la notification Kafka) */
    @Query(value = """
            SELECT e2.EMAIL
            FROM GERAI_USER.EMPLOYEES e1
            JOIN GERAI_USER.EMPLOYEES e2 ON e1.MANAGER_ID = e2.EMPLOYEE_ID
            WHERE e1.EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findManagerEmailByEmployeeId(@Param("employeeId") Long employeeId);

    /** UUID Keycloak du manager (pour destinataireId dans NotificationMessage) */
    @Query(value = """
            SELECT e2.USER_ID
            FROM GERAI_USER.EMPLOYEES e1
            JOIN GERAI_USER.EMPLOYEES e2 ON e1.MANAGER_ID = e2.EMPLOYEE_ID
            WHERE e1.EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findManagerKeycloakSubByEmployeeId(@Param("employeeId") Long employeeId);

    /** Nom complet de l'employé */
    @Query(value = """
            SELECT FIRST_NAME || ' ' || LAST_NAME
            FROM GERAI_USER.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findFullNameByEmployeeId(@Param("employeeId") Long employeeId);
}