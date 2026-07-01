package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.DemandeRecrutement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repository Spring Data JPA pour la gestion des demandes de recrutement.
 * <p>
 * Fournit les opérations CRUD standard sur la table {@code DEMANDES_RECRUTEMENT}
 * ainsi qu'une requête de filtrage par chef de projet.
 * </p>
 *
 * @since 1.0
 */
public interface DemandeRecrutementRepository extends JpaRepository<DemandeRecrutement, Long> {

    /**
     * Retourne les demandes de recrutement soumises par un chef de projet donné.
     *
     * @param chefId identifiant Keycloak du chef de projet
     * @return liste des demandes du chef, triée par défaut par identifiant
     */
    List<DemandeRecrutement> findByChefId(String chefId);
}
