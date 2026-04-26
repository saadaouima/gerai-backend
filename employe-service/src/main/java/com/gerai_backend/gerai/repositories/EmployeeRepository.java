package com.gerai_backend.gerai.repositories;

import com.gerai_backend.gerai.models.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

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
}