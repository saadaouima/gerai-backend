package com.gerai.tachesservice.repository;

import com.gerai.tachesservice.entity.EmployeeRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository en lecture seule sur GERAI.EMPLOYEES.
 *
 * MISE À JOUR : ajout de findEmailById() et findKeycloakSubById()
 * nécessaires par TacheNotificationProducer pour construire les events Kafka.
 */
@Repository
public interface EmployeeQueryRepository extends JpaRepository<EmployeeRef, Long> {

    /* ── Résolution employee_id depuis le JWT ─────────────── */

    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE USER_ID = :sub AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdBySub(@Param("sub") String sub);

    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE EMAIL = :email AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByEmail(@Param("email") String email);

    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE UPPER(FIRST_NAME || ' ' || LAST_NAME) = UPPER(:fullName)
              AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByFullName(@Param("fullName") String fullName);

    /* ── Données employé pour construire les events Kafka ─── */

    /** "Prénom Nom" — affiché dans le contenu des notifications */
    @Query(value = """
            SELECT FIRST_NAME || ' ' || LAST_NAME
            FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findFullNameById(@Param("employeeId") Long employeeId);

    /**
     * Email de l'employé — transmis à EmailService via le event Kafka.
     * Null si l'employé n'a pas d'email enregistré.
     */
    @Query(value = """
            SELECT EMAIL FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findEmailById(@Param("employeeId") Long employeeId);

    /**
     * UUID Keycloak (USER_ID) de l'employé.
     * Utilisé comme clé STOMP par notification-service pour le push WebSocket.
     */
    @Query(value = """
            SELECT USER_ID FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findKeycloakSubById(@Param("employeeId") Long employeeId);
}