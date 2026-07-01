package com.gerai.analyticsservice.repository;

import com.gerai.analyticsservice.model.Demande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository analytique — schéma GERAI_USER (Oracle).
 *
 * Stratégie de résolution du département d'un Chef :
 *   1. Priorité : claim JWT "dept_id" (extrait par JwtHelper) — zéro requête Oracle
 *   2. Fallback : findDeptIdByEmail() / findDeptIdBySubject() si le claim n'est pas configuré
 */
@Repository
public interface AnalyticsRepository extends JpaRepository<Demande, Long> {

    /* ═══════════════════════════════════════════════════════════
       RÉSOLUTION IDENTITÉ (fallback si claims custom absents)
       ═══════════════════════════════════════════════════════════ */

    /**
     * Retourne le dept_id Oracle de l'employé correspondant à cet email.
     * Utilisé si le claim "dept_id" n'est pas encore configuré dans Keycloak.
     */
    @Query(value = """
            SELECT DEPT_ID
            FROM GERAI.EMPLOYEES
            WHERE EMAIL = :email
              AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findDeptIdByEmail(@Param("email") String email);

    /**
     * Retourne le dept_id via le user_id Keycloak (UUID = sub du JWT).
     * Plus robuste que l'email si l'email peut changer.
     */
    @Query(value = """
            SELECT DEPT_ID
            FROM GERAI.EMPLOYEES
            WHERE USER_ID = :keycloakSub
              AND STATUS  = 'ACTIF'
            """, nativeQuery = true)
    Long findDeptIdBySubject(@Param("keycloakSub") String keycloakSub);

    /**
     * Retourne l'employee_id Oracle via le user_id Keycloak.
     */
    @Query(value = """
            SELECT EMPLOYEE_ID
            FROM GERAI.EMPLOYEES
            WHERE USER_ID = :keycloakSub
              AND STATUS  = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdBySubject(@Param("keycloakSub") String keycloakSub);

    /* ═══════════════════════════════════════════════════════════
       COMPTAGES GLOBAUX — Vue GERAI.V_ALL_DEMANDES
       ═══════════════════════════════════════════════════════════ */

    /**
     * Compte le nombre total de demandes dans la vue V_ALL_DEMANDES (toutes tables confondues).
     *
     * @return le nombre total de demandes RH
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.V_ALL_DEMANDES",
            nativeQuery = true)
    long countTotal();

    /**
     * Compte les demandes ayant un statut précis dans la vue V_ALL_DEMANDES.
     *
     * @param statut le statut à filtrer (ex : "EN_ATTENTE", "REFUSE", "VALIDE_RH")
     * @return le nombre de demandes correspondant au statut donné
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GERAI.V_ALL_DEMANDES
            WHERE STATUT = :statut
            """, nativeQuery = true)
    long countByStatut(@Param("statut") String statut);

    /**
     * Retourne le comptage des demandes regroupées par type.
     * Chaque ligne contient [TYPE_VAL (String), TOTAL (Number)].
     *
     * @return la liste des paires [type, total], triées par type
     */
    @Query(value = """
            SELECT NVL(TYPE,'INCONNU') AS TYPE_VAL, COUNT(*) AS TOTAL
            FROM GERAI.V_ALL_DEMANDES
            GROUP BY TYPE
            ORDER BY TYPE
            """, nativeQuery = true)
    List<Object[]> countGroupByType();

    /**
     * Retourne le comptage des demandes regroupées par statut.
     * Chaque ligne contient [STATUT_VAL (String), TOTAL (Number)].
     *
     * @return la liste des paires [statut, total], triées par statut
     */
    @Query(value = """
            SELECT NVL(STATUT,'INCONNU') AS STATUT_VAL, COUNT(*) AS TOTAL
            FROM GERAI.V_ALL_DEMANDES
            GROUP BY STATUT
            ORDER BY STATUT
            """, nativeQuery = true)
    List<Object[]> countGroupByStatut();

    /**
     * Retourne le comptage mensuel des demandes.
     * Chaque ligne contient [MOIS (String "YYYY-MM"), TOTAL (Number)].
     *
     * @return la liste des paires [mois, total], triées chronologiquement
     */
    @Query(value = """
            SELECT TO_CHAR(DATE_CREATION,'YYYY-MM') AS MOIS, COUNT(*) AS TOTAL
            FROM GERAI.V_ALL_DEMANDES
            WHERE DATE_CREATION IS NOT NULL
            GROUP BY TO_CHAR(DATE_CREATION,'YYYY-MM')
            ORDER BY MOIS
            """, nativeQuery = true)
    List<Object[]> countGroupByMois();

    /**
     * Retourne le comptage des demandes regroupées par mois ET par type.
     * Chaque ligne contient [MOIS (String "YYYY-MM"), TYPE_VAL (String), TOTAL (Number)].
     * Utilisé pour les graphiques de tendances multi-séries dans Angular.
     *
     * @return la liste des triplets [mois, type, total], triés par mois puis par type
     */
    @Query(value = """
            SELECT TO_CHAR(DATE_CREATION,'YYYY-MM') AS MOIS,
                   NVL(TYPE,'INCONNU')              AS TYPE_VAL,
                   COUNT(*)                         AS TOTAL
            FROM GERAI.V_ALL_DEMANDES
            WHERE DATE_CREATION IS NOT NULL
            GROUP BY TO_CHAR(DATE_CREATION,'YYYY-MM'), TYPE
            ORDER BY MOIS, TYPE
            """, nativeQuery = true)
    List<Object[]> countGroupByMoisAndType();

    /* ═══════════════════════════════════════════════════════════
       STATS CONGÉS — GERAI.LEAVE_REQUESTS
       Statuts : EN_ATTENTE | VALIDE_CHEF | VALIDE_RH | REFUSE | ANNULE
       ═══════════════════════════════════════════════════════════ */

    /**
     * Compte le nombre total de demandes de congé dans LEAVE_REQUESTS.
     *
     * @return le nombre total de congés
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.LEAVE_REQUESTS",
            nativeQuery = true)
    long countTotalConges();

    /**
     * Compte les congés validés par le RH (statut = VALIDE_RH).
     *
     * @return le nombre de congés validés
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.LEAVE_REQUESTS WHERE STATUS = 'VALIDE_RH'",
            nativeQuery = true)
    long countCongesValides();

    /**
     * Compte les congés refusés (statut = REFUSE).
     *
     * @return le nombre de congés refusés
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.LEAVE_REQUESTS WHERE STATUS = 'REFUSE'",
            nativeQuery = true)
    long countCongesRefuses();

    /**
     * Compte les congés en attente de validation (statuts EN_ATTENTE et VALIDE_CHEF).
     *
     * @return le nombre de congés en attente
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GERAI.LEAVE_REQUESTS
            WHERE STATUS IN ('EN_ATTENTE','VALIDE_CHEF')
            """, nativeQuery = true)
    long countCongesEnAttente();

    /**
     * Calcule la durée moyenne des congés en jours (AVG de DAYS_COUNT).
     * Retourne 0.0 si aucune donnée disponible (NVL Oracle).
     *
     * @return la durée moyenne des congés, arrondie à 2 décimales
     */
    @Query(value = """
            SELECT NVL(ROUND(AVG(DAYS_COUNT), 2), 0)
            FROM GERAI.LEAVE_REQUESTS
            WHERE DAYS_COUNT IS NOT NULL AND DAYS_COUNT > 0
            """, nativeQuery = true)
    Double avgJoursConge();

    /**
     * Retourne le comptage mensuel des demandes de congé.
     * Chaque ligne contient [MOIS (String "YYYY-MM"), TOTAL (Number)].
     *
     * @return la liste des paires [mois, total], triées chronologiquement
     */
    @Query(value = """
            SELECT TO_CHAR(CREATED_AT,'YYYY-MM') AS MOIS, COUNT(*) AS TOTAL
            FROM GERAI.LEAVE_REQUESTS
            WHERE CREATED_AT IS NOT NULL
            GROUP BY TO_CHAR(CREATED_AT,'YYYY-MM')
            ORDER BY MOIS
            """, nativeQuery = true)
    List<Object[]> countCongesGroupByMois();

    /* ═══════════════════════════════════════════════════════════
       STATS FORMATIONS — GERAI.TRAINING_REQUESTS
       Statuts : EN_ATTENTE | APPROUVE_CHEF | APPROUVE_RH | REFUSE | ANNULE
       ═══════════════════════════════════════════════════════════ */

    /**
     * Compte le nombre total de demandes de formation dans TRAINING_REQUESTS.
     *
     * @return le nombre total de formations demandées
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.TRAINING_REQUESTS",
            nativeQuery = true)
    long countTotalFormations();

    /**
     * Compte les formations approuvées par le RH (statut = APPROUVE_RH).
     *
     * @return le nombre de formations approuvées
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.TRAINING_REQUESTS WHERE STATUS = 'APPROUVE_RH'",
            nativeQuery = true)
    long countFormationsValidees();

    /**
     * Compte les formations refusées (statut = REFUSE).
     *
     * @return le nombre de formations refusées
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.TRAINING_REQUESTS WHERE STATUS = 'REFUSE'",
            nativeQuery = true)
    long countFormationsRefusees();

    /**
     * Compte les formations en attente de validation (statuts EN_ATTENTE et APPROUVE_CHEF).
     *
     * @return le nombre de formations en attente
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GERAI.TRAINING_REQUESTS
            WHERE STATUS IN ('EN_ATTENTE','APPROUVE_CHEF')
            """, nativeQuery = true)
    long countFormationsEnAttente();

    /**
     * Calcule le budget total des formations approuvées par le RH (SUM d'ESTIMATED_COST).
     * Retourne 0 si aucune formation approuvée (NVL Oracle).
     *
     * @return le budget total en dinars tunisiens
     */
    @Query(value = """
            SELECT NVL(SUM(ESTIMATED_COST), 0)
            FROM GERAI.TRAINING_REQUESTS
            WHERE STATUS = 'APPROUVE_RH'
            """, nativeQuery = true)
    double sumBudgetFormations();

    /**
     * Calcule la durée moyenne des formations en jours (AVG de DURATION_DAYS).
     * Retourne 0 si aucune donnée disponible (NVL Oracle).
     *
     * @return la durée moyenne des formations, arrondie à 2 décimales
     */
    @Query(value = """
            SELECT NVL(ROUND(AVG(DURATION_DAYS), 2), 0)
            FROM GERAI.TRAINING_REQUESTS
            WHERE DURATION_DAYS IS NOT NULL AND DURATION_DAYS > 0
            """, nativeQuery = true)
    double avgDureeFormations();

    /**
     * Retourne le comptage mensuel des demandes de formation.
     * Chaque ligne contient [MOIS (String "YYYY-MM"), TOTAL (Number)].
     *
     * @return la liste des paires [mois, total], triées chronologiquement
     */
    @Query(value = """
            SELECT TO_CHAR(CREATED_AT,'YYYY-MM') AS MOIS, COUNT(*) AS TOTAL
            FROM GERAI.TRAINING_REQUESTS
            WHERE CREATED_AT IS NOT NULL
            GROUP BY TO_CHAR(CREATED_AT,'YYYY-MM')
            ORDER BY MOIS
            """, nativeQuery = true)
    List<Object[]> countFormationsGroupByMois();

    /* ═══════════════════════════════════════════════════════════
       KPIs TEMPS RÉEL
       ═══════════════════════════════════════════════════════════ */

    /**
     * Compte les employés distincts en congé validé aujourd'hui.
     * SYSDATE doit être compris entre START_DATE et END_DATE du congé.
     *
     * @return le nombre d'employés absents aujourd'hui (globalement)
     */
    @Query(value = """
            SELECT COUNT(DISTINCT EMPLOYEE_ID)
            FROM GERAI.LEAVE_REQUESTS
            WHERE STATUS = 'VALIDE_RH'
              AND TRUNC(SYSDATE) BETWEEN TRUNC(START_DATE) AND TRUNC(END_DATE)
            """, nativeQuery = true)
    long countAbsentsAujourdhui();

    /**
     * Absents aujourd'hui dans un département spécifique (pour le Chef).
     */
    @Query(value = """
            SELECT COUNT(DISTINCT lr.EMPLOYEE_ID)
            FROM GERAI.LEAVE_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE lr.STATUS = 'VALIDE_RH'
              AND e.DEPT_ID = :deptId
              AND TRUNC(SYSDATE) BETWEEN TRUNC(lr.START_DATE) AND TRUNC(lr.END_DATE)
            """, nativeQuery = true)
    long countAbsentsAujourdhuiParDept(@Param("deptId") Long deptId);

    /**
     * Compte le nombre total de projets actifs (statut = EN_COURS) dans toute l'organisation.
     *
     * @return le nombre de projets en cours
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.PROJECTS WHERE STATUS = 'EN_COURS'",
            nativeQuery = true)
    long countProjetsActifs();

    /**
     * Projets actifs dans un département spécifique (pour le Chef).
     */
    @Query(value = """
            SELECT COUNT(*) FROM GERAI.PROJECTS
            WHERE STATUS = 'EN_COURS' AND DEPT_ID = :deptId
            """, nativeQuery = true)
    long countProjetsActifsParDept(@Param("deptId") Long deptId);

    /**
     * Compte le nombre total de tâches non terminées (tous projets, tous statuts sauf TERMINE).
     *
     * @return le nombre de tâches ouvertes dans l'organisation
     */
    @Query(value = "SELECT COUNT(*) FROM GERAI.TASKS WHERE STATUS <> 'TERMINE'",
            nativeQuery = true)
    long countTachesOuvertes();

    /**
     * Tâches ouvertes dans les projets d'un département (pour le Chef).
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GERAI.TASKS t
            JOIN GERAI.PROJECTS p ON t.PROJECT_ID = p.PROJECT_ID
            WHERE t.STATUS  <> 'TERMINE'
              AND p.DEPT_ID = :deptId
            """, nativeQuery = true)
    long countTachesOuvertesParDept(@Param("deptId") Long deptId);

    /**
     * Taux d'absentéisme global mois courant (CTE pour éviter ORA-00937).
     * Retourne 0 si aucun employé actif ou aucun congé ce mois.
     */
    @Query(value = """
            WITH total_dispo AS (
                SELECT COUNT(*) * 22 AS DISPO
                FROM GERAI.EMPLOYEES
                WHERE STATUS = 'ACTIF'
            )
            SELECT NVL(
                ROUND(
                    NVL(SUM(lr.DAYS_COUNT), 0) * 100.0
                    / NULLIF(td.DISPO, 0),
                    2
                ), 0
            )
            FROM GERAI.LEAVE_REQUESTS lr, total_dispo td
            WHERE lr.STATUS = 'VALIDE_RH'
              AND TO_CHAR(lr.START_DATE,'YYYY-MM') = TO_CHAR(SYSDATE,'YYYY-MM')
            GROUP BY td.DISPO
            """, nativeQuery = true)
    Double getTauxAbsenteismeMoisCourant();

    /**
     * Taux d'absentéisme pour un département spécifique mois courant.
     */
    @Query(value = """
            WITH dispo AS (
                SELECT COUNT(*) * 22 AS DISPO
                FROM GERAI.EMPLOYEES
                WHERE STATUS = 'ACTIF' AND DEPT_ID = :deptId
            )
            SELECT NVL(
                ROUND(
                    NVL(SUM(lr.DAYS_COUNT), 0) * 100.0
                    / NULLIF(d.DISPO, 0),
                    2
                ), 0
            )
            FROM GERAI.LEAVE_REQUESTS lr
            JOIN GERAI.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID, dispo d
            WHERE lr.STATUS = 'VALIDE_RH'
              AND e.DEPT_ID = :deptId
              AND TO_CHAR(lr.START_DATE,'YYYY-MM') = TO_CHAR(SYSDATE,'YYYY-MM')
            GROUP BY d.DISPO
            """, nativeQuery = true)
    Double getTauxAbsenteismeParDept(@Param("deptId") Long deptId);

    /* ═══════════════════════════════════════════════════════════
       RAPPORTS JASPER — CONGÉS (globaux + filtrés par dept)
       Colonnes : REQUESTID, MATRICULE, EMPLOYE_NOM, DEPARTEMENT,
                  DATE_DEBUT, DATE_FIN, NB_JOURS, STATUT, MOTIF,
                  COMMENTAIRE_RH, DATE_CREATION
       ═══════════════════════════════════════════════════════════ */

    /**
     * Retourne la liste complète des demandes de congé pour le rapport global (tous départements).
     * Colonnes : REQUESTID, MATRICULE, EMPLOYE_NOM, DEPARTEMENT, DATE_DEBUT, DATE_FIN,
     * NB_JOURS, STATUT, MOTIF, COMMENTAIRE_RH, DATE_CREATION.
     *
     * @return la liste de toutes les demandes de congé triées par date de création décroissante
     */
    @Query(value = """
            SELECT
                lr.REQUEST_ID                                           AS REQUESTID,
                NVL(e.EMPLOYEE_CODE, 'N/A')                             AS MATRICULE,
                NVL(e.FIRST_NAME,'') || ' ' || NVL(e.LAST_NAME,'')     AS EMPLOYE_NOM,
                NVL(d.NAME, 'Non défini')                               AS DEPARTEMENT,
                TO_CHAR(lr.START_DATE,  'DD/MM/YYYY')                  AS DATE_DEBUT,
                TO_CHAR(lr.END_DATE,    'DD/MM/YYYY')                  AS DATE_FIN,
                NVL(lr.DAYS_COUNT, 0)                                   AS NB_JOURS,
                lr.STATUS                                               AS STATUT,
                NVL(rtc.LIBELLE, 'Congé annuel')                        AS TYPE_CONGE,
                NVL(lr.REASON, '-')                                     AS MOTIF,
                NVL(lr.REJECTION_REASON, '-')                           AS COMMENTAIRE_RH,
                TO_CHAR(lr.CREATED_AT, 'DD/MM/YYYY HH24:MI')           AS DATE_CREATION
            FROM GERAI.LEAVE_REQUESTS lr
            JOIN      GERAI.EMPLOYEES        e   ON lr.EMPLOYEE_ID   = e.EMPLOYEE_ID
            LEFT JOIN GERAI.DEPARTMENTS      d   ON e.DEPT_ID        = d.DEPT_ID
            LEFT JOIN GERAI.REF_TYPES_CONGE  rtc ON lr.LEAVE_TYPE_ID = rtc.TYPE_ID
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeCongesForReport();

    /**
     * Retourne la liste des demandes de congé filtrées par département (pour les Chefs).
     * Colonnes identiques à {@link #listeCongesForReport()}.
     *
     * @param deptId l'identifiant Oracle du département (DEPARTMENTS.dept_id)
     * @return la liste des congés du département triés par date de création décroissante
     */
    @Query(value = """
            SELECT
                lr.REQUEST_ID                                           AS REQUESTID,
                NVL(e.EMPLOYEE_CODE, 'N/A')                             AS MATRICULE,
                NVL(e.FIRST_NAME,'') || ' ' || NVL(e.LAST_NAME,'')     AS EMPLOYE_NOM,
                NVL(d.NAME, 'Non défini')                               AS DEPARTEMENT,
                TO_CHAR(lr.START_DATE,  'DD/MM/YYYY')                  AS DATE_DEBUT,
                TO_CHAR(lr.END_DATE,    'DD/MM/YYYY')                  AS DATE_FIN,
                NVL(lr.DAYS_COUNT, 0)                                   AS NB_JOURS,
                lr.STATUS                                               AS STATUT,
                NVL(rtc.LIBELLE, 'Congé annuel')                        AS TYPE_CONGE,
                NVL(lr.REASON, '-')                                     AS MOTIF,
                NVL(lr.REJECTION_REASON, '-')                           AS COMMENTAIRE_RH,
                TO_CHAR(lr.CREATED_AT, 'DD/MM/YYYY HH24:MI')           AS DATE_CREATION
            FROM GERAI.LEAVE_REQUESTS lr
            JOIN      GERAI.EMPLOYEES        e   ON lr.EMPLOYEE_ID   = e.EMPLOYEE_ID
            JOIN      GERAI.DEPARTMENTS      d   ON e.DEPT_ID        = d.DEPT_ID
            LEFT JOIN GERAI.REF_TYPES_CONGE  rtc ON lr.LEAVE_TYPE_ID = rtc.TYPE_ID
            WHERE d.DEPT_ID = :deptId
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeCongesParDepartement(@Param("deptId") Long deptId);

    /**
     * Retourne les congés des membres directs d'un chef (MANAGER_ID + PROJECT_MEMBERS).
     * Colonnes identiques à {@link #listeCongesForReport()}.
     *
     * @param chefEmployeeId l'identifiant Oracle du chef (EMPLOYEES.employee_id)
     * @return la liste des congés des membres d'équipe triés par date de création décroissante
     */
    @Query(value = """
            SELECT
                lr.REQUEST_ID                                           AS REQUESTID,
                NVL(e.EMPLOYEE_CODE, 'N/A')                             AS MATRICULE,
                NVL(e.FIRST_NAME,'') || ' ' || NVL(e.LAST_NAME,'')     AS EMPLOYE_NOM,
                NVL(d.NAME, 'Non défini')                               AS DEPARTEMENT,
                TO_CHAR(lr.START_DATE,  'DD/MM/YYYY')                  AS DATE_DEBUT,
                TO_CHAR(lr.END_DATE,    'DD/MM/YYYY')                  AS DATE_FIN,
                NVL(lr.DAYS_COUNT, 0)                                   AS NB_JOURS,
                lr.STATUS                                               AS STATUT,
                NVL(rtc.LIBELLE, 'Congé annuel')                        AS TYPE_CONGE,
                NVL(lr.REASON, '-')                                     AS MOTIF,
                NVL(lr.REJECTION_REASON, '-')                           AS COMMENTAIRE_RH,
                TO_CHAR(lr.CREATED_AT, 'DD/MM/YYYY HH24:MI')           AS DATE_CREATION
            FROM GERAI.LEAVE_REQUESTS lr
            JOIN      GERAI.EMPLOYEES        e   ON lr.EMPLOYEE_ID   = e.EMPLOYEE_ID
            LEFT JOIN GERAI.DEPARTMENTS      d   ON e.DEPT_ID        = d.DEPT_ID
            LEFT JOIN GERAI.REF_TYPES_CONGE  rtc ON lr.LEAVE_TYPE_ID = rtc.TYPE_ID
            WHERE lr.EMPLOYEE_ID IN (
                SELECT DISTINCT e2.EMPLOYEE_ID FROM GERAI.EMPLOYEES e2
                WHERE e2.MANAGER_ID = :chefEmployeeId
                UNION
                SELECT DISTINCT pm.EMPLOYEE_ID FROM GERAI.PROJECT_MEMBERS pm
                JOIN GERAI.PROJECTS p ON pm.PROJECT_ID = p.PROJECT_ID
                WHERE p.CREATED_BY = :chefEmployeeId AND pm.IS_ACTIVE = 1
            )
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeCongesParManager(@Param("chefEmployeeId") Long chefEmployeeId);

    /* ═══════════════════════════════════════════════════════════
       RAPPORTS JASPER — FORMATIONS (globaux + filtrés par dept)
       Colonnes : REQUEST_ID, EMPLOYE_NOM, MATRICULE, DEPARTEMENT,
                  TRAINING_TITLE, PROVIDER, PLANNED_DATE, DURATION_DAYS,
                  ESTIMATED_COST, STATUT, DATE_CREATION
       ═══════════════════════════════════════════════════════════ */

    /**
     * Retourne la liste complète des demandes de formation pour le rapport global (tous départements).
     * Colonnes : REQUEST_ID, EMPLOYE_NOM, MATRICULE, DEPARTEMENT, TRAINING_TITLE,
     * PROVIDER, PLANNED_DATE, DURATION_DAYS, ESTIMATED_COST, STATUT, DATE_CREATION.
     *
     * @return la liste de toutes les formations triées par date de création décroissante
     */
    @Query(value = """
            SELECT
                tr.REQUEST_ID                                           AS REQUEST_ID,
                NVL(e.FIRST_NAME,'') || ' ' || NVL(e.LAST_NAME,'')     AS EMPLOYE_NOM,
                NVL(e.EMPLOYEE_CODE, 'N/A')                             AS MATRICULE,
                NVL(d.NAME, 'Non défini')                               AS DEPARTEMENT,
                tr.TRAINING_TITLE                                       AS TRAINING_TITLE,
                NVL(tr.PROVIDER, 'Interne')                             AS PROVIDER,
                TO_CHAR(tr.PLANNED_DATE, 'DD/MM/YYYY')                 AS PLANNED_DATE,
                NVL(tr.DURATION_DAYS, 0)                                AS DURATION_DAYS,
                NVL(tr.ESTIMATED_COST, 0)                               AS ESTIMATED_COST,
                tr.STATUS                                               AS STATUT,
                TO_CHAR(tr.CREATED_AT, 'DD/MM/YYYY HH24:MI')           AS DATE_CREATION
            FROM GERAI.TRAINING_REQUESTS tr
            JOIN      GERAI.EMPLOYEES   e ON tr.EMPLOYEE_ID = e.EMPLOYEE_ID
            LEFT JOIN GERAI.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeFormationsForReport();

    /**
     * Retourne la liste des demandes de formation filtrées par département (pour les Chefs).
     * Colonnes identiques à {@link #listeFormationsForReport()}.
     *
     * @param deptId l'identifiant Oracle du département (DEPARTMENTS.dept_id)
     * @return la liste des formations du département triées par date de création décroissante
     */
    @Query(value = """
            SELECT
                tr.REQUEST_ID                                           AS REQUEST_ID,
                NVL(e.FIRST_NAME,'') || ' ' || NVL(e.LAST_NAME,'')     AS EMPLOYE_NOM,
                NVL(e.EMPLOYEE_CODE, 'N/A')                             AS MATRICULE,
                NVL(d.NAME, 'Non défini')                               AS DEPARTEMENT,
                tr.TRAINING_TITLE                                       AS TRAINING_TITLE,
                NVL(tr.PROVIDER, 'Interne')                             AS PROVIDER,
                TO_CHAR(tr.PLANNED_DATE, 'DD/MM/YYYY')                 AS PLANNED_DATE,
                NVL(tr.DURATION_DAYS, 0)                                AS DURATION_DAYS,
                NVL(tr.ESTIMATED_COST, 0)                               AS ESTIMATED_COST,
                tr.STATUS                                               AS STATUT,
                TO_CHAR(tr.CREATED_AT, 'DD/MM/YYYY HH24:MI')           AS DATE_CREATION
            FROM GERAI.TRAINING_REQUESTS tr
            JOIN GERAI.EMPLOYEES   e ON tr.EMPLOYEE_ID = e.EMPLOYEE_ID
            JOIN GERAI.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
            WHERE d.DEPT_ID = :deptId
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeFormationsParDepartement(@Param("deptId") Long deptId);

    /* ═══════════════════════════════════════════════════════════
       FICHE EMPLOYÉ — Historique depuis V_ALL_DEMANDES
       ═══════════════════════════════════════════════════════════ */

    /**
     * Retourne l'historique des demandes RH d'un employé spécifique.
     * Chaque ligne contient [TYPE_VAL, STATUT_VAL, DATE_CREATION, DESCRIPTION].
     *
     * @param employeeId l'identifiant Oracle de l'employé (EMPLOYEES.employee_id)
     * @return la liste des demandes triées par date de création décroissante
     */
    @Query(value = """
            SELECT
                NVL(TYPE,'-')                               AS TYPE_VAL,
                NVL(STATUT,'-')                             AS STATUT_VAL,
                TO_CHAR(DATE_CREATION,'DD/MM/YYYY')         AS DATE_CREATION,
                NVL(DESCRIPTION,'-')                        AS DESCRIPTION
            FROM GERAI.V_ALL_DEMANDES
            WHERE EMPLOYE_ID = :employeeId
            ORDER BY DATE_CREATION DESC NULLS LAST
            """, nativeQuery = true)
    List<Object[]> demandesParEmploye(@Param("employeeId") Long employeeId);

    /* ═══════════════════════════════════════════════════════════
       STATS PAR DÉPARTEMENT (dashboard global RH)
       ═══════════════════════════════════════════════════════════ */

    /**
     * Retourne les statistiques agrégées par département pour le tableau de bord RH global.
     * Colonnes : DEPT_NAME, HEADCOUNT, NB_CONGES, NB_FORMATIONS, NB_PROJETS.
     *
     * @return la liste des statistiques par département, triées par nom de département
     */
    @Query(value = """
            SELECT
                dep.NAME                                                        AS DEPT_NAME,
                COUNT(DISTINCT e.EMPLOYEE_ID)                                   AS HEADCOUNT,
                NVL(SUM(CASE WHEN v.TYPE='CONGE'     THEN 1 ELSE 0 END),0)     AS NB_CONGES,
                NVL(SUM(CASE WHEN v.TYPE='FORMATION' THEN 1 ELSE 0 END),0)     AS NB_FORMATIONS,
                COUNT(DISTINCT p.PROJECT_ID)                                    AS NB_PROJETS
            FROM GERAI.DEPARTMENTS dep
            LEFT JOIN GERAI.EMPLOYEES      e ON e.DEPT_ID    = dep.DEPT_ID
                                                 AND e.STATUS     = 'ACTIF'
            LEFT JOIN GERAI.V_ALL_DEMANDES v ON v.EMPLOYE_ID = e.EMPLOYEE_ID
            LEFT JOIN GERAI.PROJECTS       p ON p.DEPT_ID    = dep.DEPT_ID
                                                 AND p.STATUS     = 'EN_COURS'
            GROUP BY dep.DEPT_ID, dep.NAME
            ORDER BY dep.NAME
            """, nativeQuery = true)
    List<Object[]> statsParDepartement();

    /* ═══════════════════════════════════════════════════════════
       TOP 5 — Employés les plus absents (année courante)
       ═══════════════════════════════════════════════════════════ */

    /**
     * Retourne le top 5 des employés les plus absents sur l'année courante (tous départements).
     * Colonnes : NOM, DEPARTEMENT, TOTAL_JOURS.
     * Seuls les congés validés par le RH (statut = VALIDE_RH) sont pris en compte.
     *
     * @return la liste des 5 employés les plus absents, triés par total de jours décroissant
     */
    @Query(value = """
            SELECT *
            FROM (
                SELECT
                    NVL(e.FIRST_NAME,'') || ' ' || NVL(e.LAST_NAME,'')   AS NOM,
                    NVL(d.NAME,'Non défini')                              AS DEPARTEMENT,
                    NVL(SUM(lr.DAYS_COUNT), 0)                            AS TOTAL_JOURS
                FROM GERAI.LEAVE_REQUESTS lr
                JOIN      GERAI.EMPLOYEES   e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
                LEFT JOIN GERAI.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
                WHERE lr.STATUS = 'VALIDE_RH'
                  AND TO_CHAR(lr.START_DATE,'YYYY') = TO_CHAR(SYSDATE,'YYYY')
                GROUP BY e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, d.NAME
                ORDER BY TOTAL_JOURS DESC
            )
            WHERE ROWNUM <= 5
            """, nativeQuery = true)
    List<Object[]> top5EmployesAbsences();
    /**
     * Retourne les informations de base d'un employé pour l'en-tête de la fiche signalétique.
     * Colonnes : FIRST_NAME, LAST_NAME, EMPLOYEE_CODE, DEPT_NAME, EMAIL, PHONE, HIRE_DATE.
     *
     * @param id l'identifiant Oracle de l'employé (EMPLOYEES.employee_id)
     * @return une liste contenant au plus une ligne avec les informations de base de l'employé
     */
    @Query(value = """
    SELECT e.FIRST_NAME, e.LAST_NAME, e.EMPLOYEE_CODE, d.NAME as DEPT_NAME, 
           e.EMAIL, e.PHONE, TO_CHAR(e.HIRE_DATE, 'DD/MM/YYYY') as HIRE_DATE
    FROM EMPLOYEES e
    LEFT JOIN DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID
    WHERE e.EMPLOYEE_ID = :id
    """, nativeQuery = true)
    List<Object[]> findEmployeBasicInfos(@Param("id") Long id);
    /**
     * Top 5 absences dans un département spécifique (pour le Chef).
     */
    @Query(value = """
            SELECT *
            FROM (
                SELECT
                    NVL(e.FIRST_NAME,'') || ' ' || NVL(e.LAST_NAME,'')   AS NOM,
                    NVL(d.NAME,'Non défini')                              AS DEPARTEMENT,
                    NVL(SUM(lr.DAYS_COUNT), 0)                            AS TOTAL_JOURS
                FROM GERAI.LEAVE_REQUESTS lr
                JOIN GERAI.EMPLOYEES   e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
                JOIN GERAI.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
                WHERE lr.STATUS = 'VALIDE_RH'
                  AND d.DEPT_ID = :deptId
                  AND TO_CHAR(lr.START_DATE,'YYYY') = TO_CHAR(SYSDATE,'YYYY')
                GROUP BY e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, d.NAME
                ORDER BY TOTAL_JOURS DESC
            )
            WHERE ROWNUM <= 5
            """, nativeQuery = true)
    List<Object[]> top5EmployesAbsencesParDept(@Param("deptId") Long deptId);

    /* ═══════════════════════════════════════════════════════════
       RAPPORTS — PROJETS (globaux + filtrés par département)
       Colonnes : PROJET, CHEF_NOM, NB_MEMBRES, PROGRESSION, STATUT
       ═══════════════════════════════════════════════════════════ */

    /**
     * Retourne la liste complète des projets pour le rapport global (tous créateurs).
     * Colonnes : PROJET, PRIORITE, DATE_DEBUT, DATE_FIN, NB_MEMBRES, TOTAL_TACHES,
     * TACHES_COMPLETEES, PROGRESSION, STATUT.
     * Les projets sont triés par priorité (CRITIQUE > HAUTE > NORMALE > FAIBLE) puis par nom.
     *
     * @return la liste de tous les projets avec leurs indicateurs d'avancement
     */
    @Query(value = """
            SELECT
                p.NAME                                                                  AS PROJET,
                NVL(p.PRIORITY, 'NORMALE')                                              AS PRIORITE,
                TO_CHAR(p.START_DATE, 'DD/MM/YYYY')                                    AS DATE_DEBUT,
                TO_CHAR(p.END_DATE,   'DD/MM/YYYY')                                    AS DATE_FIN,
                COUNT(DISTINCT pm.EMPLOYEE_ID)                                          AS NB_MEMBRES,
                COUNT(DISTINCT t.TASK_ID)                                               AS TOTAL_TACHES,
                COUNT(DISTINCT CASE WHEN t.STATUS = 'TERMINE' THEN t.TASK_ID END)      AS TACHES_COMPLETEES,
                NVL(p.PROGRESS_PCT, 0)                                                  AS PROGRESSION,
                p.STATUS                                                                AS STATUT
            FROM GERAI.PROJECTS p
            LEFT JOIN GERAI.PROJECT_MEMBERS pm ON pm.PROJECT_ID = p.PROJECT_ID
                                               AND pm.IS_ACTIVE = 1
            LEFT JOIN GERAI.TASKS t            ON t.PROJECT_ID  = p.PROJECT_ID
            GROUP BY p.PROJECT_ID, p.NAME, p.PRIORITY, p.START_DATE, p.END_DATE,
                     p.PROGRESS_PCT, p.STATUS
            ORDER BY CASE NVL(p.PRIORITY,'NORMALE')
                         WHEN 'CRITIQUE' THEN 1 WHEN 'HAUTE' THEN 2
                         WHEN 'NORMALE'  THEN 3 ELSE 4 END, p.NAME
            """, nativeQuery = true)
    List<Object[]> listeProjetsForReport();

    /**
     * Retourne la liste des projets créés par un employé spécifique (pour les Chefs).
     * Colonnes identiques à {@link #listeProjetsForReport()}.
     *
     * @param employeeId l'identifiant Oracle de l'employé créateur du projet (PROJECTS.created_by)
     * @return la liste des projets créés par cet employé, triés par priorité puis par nom
     */
    @Query(value = """
            SELECT
                p.NAME                                                                  AS PROJET,
                NVL(p.PRIORITY, 'NORMALE')                                              AS PRIORITE,
                TO_CHAR(p.START_DATE, 'DD/MM/YYYY')                                    AS DATE_DEBUT,
                TO_CHAR(p.END_DATE,   'DD/MM/YYYY')                                    AS DATE_FIN,
                COUNT(DISTINCT pm.EMPLOYEE_ID)                                          AS NB_MEMBRES,
                COUNT(DISTINCT t.TASK_ID)                                               AS TOTAL_TACHES,
                COUNT(DISTINCT CASE WHEN t.STATUS = 'TERMINE' THEN t.TASK_ID END)      AS TACHES_COMPLETEES,
                NVL(p.PROGRESS_PCT, 0)                                                  AS PROGRESSION,
                p.STATUS                                                                AS STATUT
            FROM GERAI.PROJECTS p
            LEFT JOIN GERAI.PROJECT_MEMBERS pm ON pm.PROJECT_ID = p.PROJECT_ID
                                               AND pm.IS_ACTIVE = 1
            LEFT JOIN GERAI.TASKS t            ON t.PROJECT_ID  = p.PROJECT_ID
            WHERE p.CREATED_BY = :employeeId
            GROUP BY p.PROJECT_ID, p.NAME, p.PRIORITY, p.START_DATE, p.END_DATE,
                     p.PROGRESS_PCT, p.STATUS
            ORDER BY CASE NVL(p.PRIORITY,'NORMALE')
                         WHEN 'CRITIQUE' THEN 1 WHEN 'HAUTE' THEN 2
                         WHEN 'NORMALE'  THEN 3 ELSE 4 END, p.NAME
            """, nativeQuery = true)
    List<Object[]> listeProjetsParCreateur(@Param("employeeId") Long employeeId);

    /**
     * Retourne l'identifiant Oracle d'un employé actif à partir de son adresse email.
     * Utilisé comme fallback dans {@code resolveEmployeeId()} si le sub Keycloak échoue.
     *
     * @param email l'adresse email de l'employé (EMPLOYEES.email)
     * @return l'identifiant Oracle de l'employé, ou {@code null} si introuvable
     */
    @Query(value = """
            SELECT EMPLOYEE_ID
            FROM GERAI.EMPLOYEES
            WHERE EMAIL = :email
              AND STATUS = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdByEmail(@Param("email") String email);
}