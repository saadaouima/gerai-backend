package com.gerai.analyticsservice.repository;

import com.gerai.analyticsservice.model.AbsenceStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository dédié à la table GERAI_USER.ABSENCE_STATS.
 *
 * DDL Oracle (à exécuter si la table n'existe pas encore) :
 * ─────────────────────────────────────────────────────────
 *   CREATE TABLE ABSENCE_STATS (
 *       STAT_ID          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 *       EMPLOYE_ID       NUMBER        NOT NULL,
 *       EMPLOYE_NOM      VARCHAR2(150),
 *       ANNEE            NUMBER(4)     NOT NULL,
 *       MOIS             NUMBER(2)     NOT NULL,
 *       NB_JOURS_CONGE   NUMBER(6,2)   DEFAULT 0 NOT NULL,
 *       NB_DEMANDES      NUMBER(5)     DEFAULT 0 NOT NULL,
 *       NB_VALIDEES      NUMBER(5)     DEFAULT 0 NOT NULL,
 *       NB_REFUSEES      NUMBER(5)     DEFAULT 0 NOT NULL,
 *       DATE_MISE_A_JOUR TIMESTAMP     DEFAULT SYSDATE,
 *       CONSTRAINT uk_absence UNIQUE (EMPLOYE_ID, ANNEE, MOIS)
 *   );
 * ─────────────────────────────────────────────────────────
 *
 * CORRECTIONS :
 *  - Toutes les requêtes natives utilisent le schéma complet GERAI_USER.ABSENCE_STATS
 *  - NVL() sur tous les agrégats pour garantir un retour non-null
 *  - sumJoursAbsenceParMois retourne Double (NUMBER(6,2) Oracle → Double Java)
 *  - countEmployesAbsentsParMois retourne Long (COUNT retourne NUMBER en Oracle)
 */
@Repository
public interface AbsenceStatsRepository extends JpaRepository<AbsenceStats, Long> {

    /**
     * Trouve une ligne existante pour upsert depuis le Kafka consumer.
     * Spring Data JPA génère :
     *   SELECT * FROM ABSENCE_STATS
     *   WHERE EMPLOYE_ID = ? AND ANNEE = ? AND MOIS = ?
     *
     * IMPORTANT : la table physique s'appelle ABSENCE_STATS dans le schéma GERAI_USER.
     * Spring Data la trouve via @Table(name="ABSENCE_STATS") sur l'entité AbsenceStats.
     */
    Optional<AbsenceStats> findByEmployeIdAndAnneeAndMois(
            Long employeId, Integer annee, Integer mois);

    /**
     * Taux d'absentéisme moyen pour un mois donné.
     * Base : 22 jours ouvrables par mois.
     * NVL garantit 0.0 si aucune ligne pour ce mois.
     */
    @Query(value = """
            SELECT NVL(
                ROUND(AVG(NB_JOURS_CONGE * 100.0 / 22.0), 2),
                0
            )
            FROM GERAI_USER.ABSENCE_STATS
            WHERE ANNEE = :annee AND MOIS = :mois
            """, nativeQuery = true)
    Double avgTauxAbsenteisme(@Param("annee") int annee,
                              @Param("mois")  int mois);

    /**
     * Total des jours d'absence pour un mois donné.
     * NUMBER(6,2) Oracle → Double Java.
     * Retourne 0.0 si aucune donnée (NVL).
     */
    @Query(value = """
            SELECT NVL(SUM(NB_JOURS_CONGE), 0)
            FROM GERAI_USER.ABSENCE_STATS
            WHERE ANNEE = :annee AND MOIS = :mois
            """, nativeQuery = true)
    Double sumJoursAbsenceParMois(@Param("annee") int annee,
                                  @Param("mois")  int mois);

    /**
     * Nombre d'employés distincts ayant eu des absences ce mois.
     * COUNT retourne NUMBER en Oracle → Long Java.
     */
    @Query(value = """
            SELECT COUNT(DISTINCT EMPLOYE_ID)
            FROM GERAI_USER.ABSENCE_STATS
            WHERE ANNEE = :annee
              AND MOIS  = :mois
              AND NB_JOURS_CONGE > 0
            """, nativeQuery = true)
    Long countEmployesAbsentsParMois(@Param("annee") int annee,
                                     @Param("mois")  int mois);
}