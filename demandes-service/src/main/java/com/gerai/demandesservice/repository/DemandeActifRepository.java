package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.DemandeActif;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA pour les demandes d'actifs informatiques
 * (table {@code GERAI.DEMANDES_ACTIFS}).
 * <p>
 * Hérite de {@link JpaRepository} pour les opérations CRUD standard.
 *
 * @since 1.0
 */
public interface DemandeActifRepository extends JpaRepository<DemandeActif, Long> {

    /**
     * Retourne toutes les demandes d'actifs soumises par un employé donné.
     *
     * @param employeId identifiant Oracle de l'employé
     * @return liste des demandes d'actifs de l'employé
     */
    java.util.List<DemandeActif> findByEmployeId(Long employeId);
}
