package com.gerai_backend.gerai.repositories;

import com.gerai_backend.gerai.models.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Corrections vs l'ancienne version :
 *  - JpaRepository<Employee, UUID> → JpaRepository<Employee, Long>
 *    (PK est maintenant Long IDENTITY, pas UUID)
 *  - Ajout findByKeycloakUserId pour le lookup via user_id Keycloak
 *  - Ajout findByEmployeeCode pour vérification unicité du matricule
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmail(String email);

    Optional<Employee> findByEmail(String email);

    /** Lookup via UUID Keycloak (colonne USER_ID) */
    Optional<Employee> findByKeycloakUserId(String keycloakUserId);

    /** Vérification unicité du matricule avant insertion */
    boolean existsByEmployeeCode(String employeeCode);

    /** Membres d'une équipe managée par un responsable */
    List<Employee> findByManagerId(Long managerId);

    /** Recherche par nom, prénom ou email (insensible à la casse) */
    @Query("SELECT e FROM Employee e WHERE " +
           "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(e.lastName)  LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(e.email)     LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Employee> searchByQuery(@Param("q") String q);
}