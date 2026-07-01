package com.gerai.tachesservice.repository;

import com.gerai.tachesservice.entity.EmployeeRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository Spring Data JPA pour les accès en lecture sur la table GERAI.EMPLOYEES.
 * <p>
 * {@code @Repository} : déclare ce bean comme composant de persistance Spring,
 * active la traduction des exceptions JPA en exceptions Spring.
 * <p>
 * Ce repository est utilisé exclusivement en lecture pour deux usages :
 * <ol>
 *   <li><b>Résolution d'identité</b> : retrouver l'{@code employee_id} Oracle
 *       à partir des claims JWT ({@code sub}, {@code email}) ou du nom complet.</li>
 *   <li><b>Construction des événements Kafka</b> : récupérer le nom complet,
 *       l'email et l'UUID Keycloak nécessaires à {@code TacheNotificationProducer}.</li>
 * </ol>
 * <p>
 * Toutes les requêtes natives ciblent le schéma {@code GERAI.EMPLOYEES}.
 *
 * @since 1.0
 */
@Repository
public interface EmployeeQueryRepository extends JpaRepository<EmployeeRef, Long> {

    /* ── Résolution employee_id depuis le JWT ─────────────── */

    /**
     * Résout l'identifiant Oracle d'un employé à partir de son UUID Keycloak (claim {@code sub} du JWT).
     * <p>
     * Filtre sur le statut {@code ACTIF} pour exclure les comptes désactivés.
     *
     * @param sub UUID Keycloak de l'utilisateur (valeur du claim {@code sub})
     * @return l'identifiant Oracle de l'employé, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE USER_ID = :sub AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdBySub(@Param("sub") String sub);

    /**
     * Résout l'identifiant Oracle d'un employé à partir de son adresse email.
     * <p>
     * Utilisé en fallback si le claim {@code sub} ne permet pas la résolution.
     *
     * @param email adresse email de l'employé (EMPLOYEES.email)
     * @return l'identifiant Oracle de l'employé, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE EMAIL = :email AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByEmail(@Param("email") String email);

    /**
     * Résout l'identifiant Oracle d'un employé à partir de son nom complet ("Prénom Nom").
     * <p>
     * Comparaison insensible à la casse via {@code UPPER()}. Utilisé pour résoudre
     * le champ {@code assigneA} envoyé par Angular lors de la création d'une tâche.
     *
     * @param fullName nom complet de l'employé au format "Prénom Nom"
     * @return l'identifiant Oracle de l'employé actif correspondant, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT EMPLOYEE_ID FROM GERAI.EMPLOYEES
            WHERE UPPER(FIRST_NAME || ' ' || LAST_NAME) = UPPER(:fullName)
              AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByFullName(@Param("fullName") String fullName);

    /* ── Données employé pour construire les events Kafka ─── */

    /**
     * Récupère le nom complet ("Prénom Nom") d'un employé par son identifiant Oracle.
     * <p>
     * Utilisé dans le contenu des notifications pour personnaliser les messages.
     *
     * @param employeeId identifiant Oracle de l'employé (EMPLOYEES.employee_id)
     * @return le nom complet concaténé, ou {@code null} si l'employé est introuvable
     */
    @Query(value = """
            SELECT FIRST_NAME || ' ' || LAST_NAME
            FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findFullNameById(@Param("employeeId") Long employeeId);

    /**
     * Récupère l'adresse email d'un employé par son identifiant Oracle.
     * <p>
     * Transmise à {@code EmailService} via l'événement Kafka pour l'envoi de notifications email.
     * Peut retourner {@code null} si l'employé n'a pas d'email enregistré.
     *
     * @param employeeId identifiant Oracle de l'employé (EMPLOYEES.employee_id)
     * @return l'adresse email de l'employé, ou {@code null} si absente
     */
    @Query(value = """
            SELECT EMAIL FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findEmailById(@Param("employeeId") Long employeeId);

    /**
     * Récupère l'UUID Keycloak (USER_ID) d'un employé par son identifiant Oracle.
     * <p>
     * Utilisé comme clé de routage STOMP par notification-service pour
     * envoyer les notifications en temps réel vers le bon client Angular connecté.
     *
     * @param employeeId identifiant Oracle de l'employé (EMPLOYEES.employee_id)
     * @return l'UUID Keycloak de l'employé, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT USER_ID FROM GERAI.EMPLOYEES
            WHERE EMPLOYEE_ID = :employeeId
            """, nativeQuery = true)
    String findKeycloakSubById(@Param("employeeId") Long employeeId);
}