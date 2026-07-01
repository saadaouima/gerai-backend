package com.gerai_backend.gerai.repositories;

import com.gerai_backend.gerai.models.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA pour l'accès aux documents personnels des employés ({@link EmployeeDocument}).
 *
 * <p>@Repository (implicite via JpaRepository) : expose les opérations CRUD standard
 * sur la table {@code EMPLOYEE_DOCUMENTS} et permet la dérivation de requêtes par convention.</p>
 *
 * @since 1.0
 */
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    /**
     * Retourne la liste des documents d'un employé triés par date d'ajout décroissante
     * (le plus récent en premier).
     *
     * @param employeeId l'identifiant Oracle de l'employé propriétaire
     * @return la liste des documents, vide si l'employé n'a aucun document
     */
    List<EmployeeDocument> findByEmployeeIdOrderByDateAjoutDesc(Long employeeId);

    /**
     * Recherche un document par son identifiant et l'identifiant de l'employé propriétaire.
     * Utilisé pour vérifier qu'un employé ne peut accéder qu'à ses propres documents.
     *
     * @param id         l'identifiant Oracle du document ({@code DOC_ID})
     * @param employeeId l'identifiant Oracle de l'employé propriétaire
     * @return un {@link Optional} contenant le document si trouvé et appartenant à l'employé,
     *         ou vide sinon
     */
    Optional<EmployeeDocument> findByIdAndEmployeeId(Long id, Long employeeId);
}
