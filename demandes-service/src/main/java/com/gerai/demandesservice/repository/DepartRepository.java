package com.gerai.demandesservice.repository;

import com.gerai.demandesservice.model.DepartEmploye;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour les départs d'employés (table {@code GERAI.DEPARTS_EMPLOYES}).
 * <p>
 * {@code @Repository} : marque cette interface comme composant Spring de la couche données.
 * Fournit une requête native Oracle pour la surveillance des fins de contrat imminentes,
 * utilisée par {@link com.gerai.demandesservice.scheduler.ContratExpiryScheduler}.
 *
 * @since 1.0
 */
@Repository
public interface DepartRepository extends JpaRepository<DepartEmploye, Long> {

    /**
     * Retourne les fins de contrat CDD non annulées arrivant à échéance dans les prochains jours.
     * <p>
     * La colonne {@code DATE_DEPART} est stockée au format {@code DD/MM/YYYY} — la conversion
     * en {@code DATE} Oracle est effectuée via {@code TO_DATE} dans la requête native.
     *
     * @param joursAvance nombre de jours à anticiper (ex : 30 pour alerter 30 jours avant)
     * @return liste des départs de type FIN_CONTRAT dont la date est dans la fenêtre d'alerte
     */
    @Query(value = """
            SELECT d.* FROM GERAI.DEPARTS_EMPLOYES d
            WHERE d.TYPE_DEPART = 'FIN_CONTRAT'
              AND d.STATUT != 'ANNULE'
              AND TO_DATE(d.DATE_DEPART, 'DD/MM/YYYY')
                    BETWEEN TRUNC(SYSDATE) AND TRUNC(SYSDATE) + :joursAvance
            ORDER BY TO_DATE(d.DATE_DEPART, 'DD/MM/YYYY') ASC
            """, nativeQuery = true)
    List<DepartEmploye> findFinContratProches(@Param("joursAvance") int joursAvance);
}
