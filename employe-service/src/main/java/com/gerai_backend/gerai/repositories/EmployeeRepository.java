package com.gerai_backend.gerai.repositories;

import com.gerai_backend.gerai.models.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA pour l'accès aux employés ({@link Employee}).
 *
 * <p>@Repository (implicite via JpaRepository) : expose les opérations CRUD standard
 * sur la table {@code EMPLOYEES} et permet la dérivation de requêtes par convention de nommage.
 * La clé primaire est un {@code Long} (NUMBER IDENTITY Oracle).</p>
 *
 * @since 1.0
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Vérifie l'existence d'un employé par son adresse email.
     *
     * @param email l'adresse email à vérifier
     * @return {@code true} si un employé avec cet email existe en base
     */
    boolean existsByEmail(String email);

    /**
     * Recherche un employé par son adresse email.
     *
     * @param email l'adresse email de l'employé
     * @return un {@link Optional} contenant l'employé si trouvé, ou vide sinon
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Recherche un employé par son UUID Keycloak (colonne {@code USER_ID}).
     *
     * @param keycloakUserId l'UUID Keycloak de l'utilisateur
     * @return un {@link Optional} contenant l'employé si trouvé, ou vide sinon
     */
    Optional<Employee> findByKeycloakUserId(String keycloakUserId);

    /**
     * Vérifie l'unicité d'un matricule employé avant insertion.
     *
     * @param employeeCode le matricule au format {@code EMP-XXXX}
     * @return {@code true} si un employé avec ce matricule existe déjà en base
     */
    boolean existsByEmployeeCode(String employeeCode);

    /**
     * Retourne la liste des membres d'une équipe managée par un responsable.
     *
     * @param managerId l'identifiant Oracle du manager ({@code EMPLOYEE_ID})
     * @return la liste des employés dont {@code MANAGER_ID} correspond au manager donné
     */
    List<Employee> findByManagerId(Long managerId);

    /**
     * Recherche des employés par texte libre (insensible à la casse) sur le prénom, le nom ou l'email.
     *
     * @param q le texte à rechercher
     * @return la liste des employés dont le prénom, le nom ou l'email contient le texte donné
     */
    @Query("SELECT e FROM Employee e WHERE " +
           "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(e.lastName)  LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(e.email)     LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Employee> searchByQuery(@Param("q") String q);
}