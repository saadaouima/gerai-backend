package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.DocumentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository Spring Data JPA pour les demandes de documents administratifs
 * (table {@code GERAI.DOCUMENT_REQUESTS}).
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche données.
 *
 * @since 1.0
 */
@Repository
public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, Long> {

    /**
     * Retourne les demandes de documents d'un employé, triées par date de création décroissante.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return liste des demandes de documents de l'employé
     */
    List<DocumentRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /**
     * Retourne les demandes de documents ayant un statut donné, triées par date de création décroissante.
     *
     * @param status valeur Oracle du statut (ex : {@code EN_ATTENTE}, {@code LIVRE})
     * @return liste des demandes de documents correspondant au statut
     */
    List<DocumentRequest> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Retourne les demandes de documents traitées par un agent RH donné,
     * triées par date de création décroissante.
     *
     * @param processedBy identifiant Oracle de l'agent RH traitant
     * @return liste des demandes traitées par cet agent
     */
    List<DocumentRequest> findByProcessedByOrderByCreatedAtDesc(Long processedBy);
}
