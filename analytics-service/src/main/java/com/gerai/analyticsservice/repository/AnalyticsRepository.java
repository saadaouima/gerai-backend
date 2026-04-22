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
            FROM GERAI_USER.EMPLOYEES
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
            FROM GERAI_USER.EMPLOYEES
            WHERE USER_ID = :keycloakSub
              AND STATUS  = 'ACTIF'
            """, nativeQuery = true)
    Long findDeptIdBySubject(@Param("keycloakSub") String keycloakSub);

    /**
     * Retourne l'employee_id Oracle via le user_id Keycloak.
     */
    @Query(value = """
            SELECT EMPLOYEE_ID
            FROM GERAI_USER.EMPLOYEES
            WHERE USER_ID = :keycloakSub
              AND STATUS  = 'ACTIF'
            """, nativeQuery = true)
    Long findEmployeeIdBySubject(@Param("keycloakSub") String keycloakSub);

    /* ═══════════════════════════════════════════════════════════
       COMPTAGES GLOBAUX — Vue GERAI_USER.V_ALL_DEMANDES
       ═══════════════════════════════════════════════════════════ */

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.V_ALL_DEMANDES",
            nativeQuery = true)
    long countTotal();

    @Query(value = """
            SELECT COUNT(*)
            FROM GERAI_USER.V_ALL_DEMANDES
            WHERE STATUT = :statut
            """, nativeQuery = true)
    long countByStatut(@Param("statut") String statut);

    @Query(value = """
            SELECT NVL(TYPE,'INCONNU') AS TYPE_VAL, COUNT(*) AS TOTAL
            FROM GERAI_USER.V_ALL_DEMANDES
            GROUP BY TYPE
            ORDER BY TYPE
            """, nativeQuery = true)
    List<Object[]> countGroupByType();

    @Query(value = """
            SELECT NVL(STATUT,'INCONNU') AS STATUT_VAL, COUNT(*) AS TOTAL
            FROM GERAI_USER.V_ALL_DEMANDES
            GROUP BY STATUT
            ORDER BY STATUT
            """, nativeQuery = true)
    List<Object[]> countGroupByStatut();

    @Query(value = """
            SELECT TO_CHAR(DATE_CREATION,'YYYY-MM') AS MOIS, COUNT(*) AS TOTAL
            FROM GERAI_USER.V_ALL_DEMANDES
            WHERE DATE_CREATION IS NOT NULL
            GROUP BY TO_CHAR(DATE_CREATION,'YYYY-MM')
            ORDER BY MOIS
            """, nativeQuery = true)
    List<Object[]> countGroupByMois();

    @Query(value = """
            SELECT TO_CHAR(DATE_CREATION,'YYYY-MM') AS MOIS,
                   NVL(TYPE,'INCONNU')              AS TYPE_VAL,
                   COUNT(*)                         AS TOTAL
            FROM GERAI_USER.V_ALL_DEMANDES
            WHERE DATE_CREATION IS NOT NULL
            GROUP BY TO_CHAR(DATE_CREATION,'YYYY-MM'), TYPE
            ORDER BY MOIS, TYPE
            """, nativeQuery = true)
    List<Object[]> countGroupByMoisAndType();

    /* ═══════════════════════════════════════════════════════════
       STATS CONGÉS — GERAI_USER.LEAVE_REQUESTS
       Statuts : EN_ATTENTE | VALIDE_CHEF | VALIDE_RH | REFUSE | ANNULE
       ═══════════════════════════════════════════════════════════ */

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.LEAVE_REQUESTS",
            nativeQuery = true)
    long countTotalConges();

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.LEAVE_REQUESTS WHERE STATUS = 'VALIDE_RH'",
            nativeQuery = true)
    long countCongesValides();

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.LEAVE_REQUESTS WHERE STATUS = 'REFUSE'",
            nativeQuery = true)
    long countCongesRefuses();

    @Query(value = """
            SELECT COUNT(*)
            FROM GERAI_USER.LEAVE_REQUESTS
            WHERE STATUS IN ('EN_ATTENTE','VALIDE_CHEF')
            """, nativeQuery = true)
    long countCongesEnAttente();

    @Query(value = """
            SELECT NVL(ROUND(AVG(DAYS_COUNT), 2), 0)
            FROM GERAI_USER.LEAVE_REQUESTS
            WHERE DAYS_COUNT IS NOT NULL AND DAYS_COUNT > 0
            """, nativeQuery = true)
    Double avgJoursConge();

    @Query(value = """
            SELECT TO_CHAR(CREATED_AT,'YYYY-MM') AS MOIS, COUNT(*) AS TOTAL
            FROM GERAI_USER.LEAVE_REQUESTS
            WHERE CREATED_AT IS NOT NULL
            GROUP BY TO_CHAR(CREATED_AT,'YYYY-MM')
            ORDER BY MOIS
            """, nativeQuery = true)
    List<Object[]> countCongesGroupByMois();

    /* ═══════════════════════════════════════════════════════════
       STATS FORMATIONS — GERAI_USER.TRAINING_REQUESTS
       Statuts : EN_ATTENTE | APPROUVE_CHEF | APPROUVE_RH | REFUSE | ANNULE
       ═══════════════════════════════════════════════════════════ */

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.TRAINING_REQUESTS",
            nativeQuery = true)
    long countTotalFormations();

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.TRAINING_REQUESTS WHERE STATUS = 'APPROUVE_RH'",
            nativeQuery = true)
    long countFormationsValidees();

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.TRAINING_REQUESTS WHERE STATUS = 'REFUSE'",
            nativeQuery = true)
    long countFormationsRefusees();

    @Query(value = """
            SELECT COUNT(*)
            FROM GERAI_USER.TRAINING_REQUESTS
            WHERE STATUS IN ('EN_ATTENTE','APPROUVE_CHEF')
            """, nativeQuery = true)
    long countFormationsEnAttente();

    @Query(value = """
            SELECT NVL(SUM(ESTIMATED_COST), 0)
            FROM GERAI_USER.TRAINING_REQUESTS
            WHERE STATUS = 'APPROUVE_RH'
            """, nativeQuery = true)
    double sumBudgetFormations();

    @Query(value = """
            SELECT NVL(ROUND(AVG(DURATION_DAYS), 2), 0)
            FROM GERAI_USER.TRAINING_REQUESTS
            WHERE DURATION_DAYS IS NOT NULL AND DURATION_DAYS > 0
            """, nativeQuery = true)
    double avgDureeFormations();

    @Query(value = """
            SELECT TO_CHAR(CREATED_AT,'YYYY-MM') AS MOIS, COUNT(*) AS TOTAL
            FROM GERAI_USER.TRAINING_REQUESTS
            WHERE CREATED_AT IS NOT NULL
            GROUP BY TO_CHAR(CREATED_AT,'YYYY-MM')
            ORDER BY MOIS
            """, nativeQuery = true)
    List<Object[]> countFormationsGroupByMois();

    /* ═══════════════════════════════════════════════════════════
       KPIs TEMPS RÉEL
       ═══════════════════════════════════════════════════════════ */

    @Query(value = """
            SELECT COUNT(DISTINCT EMPLOYEE_ID)
            FROM GERAI_USER.LEAVE_REQUESTS
            WHERE STATUS = 'VALIDE_RH'
              AND TRUNC(SYSDATE) BETWEEN TRUNC(START_DATE) AND TRUNC(END_DATE)
            """, nativeQuery = true)
    long countAbsentsAujourdhui();

    /**
     * Absents aujourd'hui dans un département spécifique (pour le Chef).
     */
    @Query(value = """
            SELECT COUNT(DISTINCT lr.EMPLOYEE_ID)
            FROM GERAI_USER.LEAVE_REQUESTS lr
            JOIN GERAI_USER.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE lr.STATUS = 'VALIDE_RH'
              AND e.DEPT_ID = :deptId
              AND TRUNC(SYSDATE) BETWEEN TRUNC(lr.START_DATE) AND TRUNC(lr.END_DATE)
            """, nativeQuery = true)
    long countAbsentsAujourdhuiParDept(@Param("deptId") Long deptId);

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.PROJECTS WHERE STATUS = 'EN_COURS'",
            nativeQuery = true)
    long countProjetsActifs();

    /**
     * Projets actifs dans un département spécifique (pour le Chef).
     */
    @Query(value = """
            SELECT COUNT(*) FROM GERAI_USER.PROJECTS
            WHERE STATUS = 'EN_COURS' AND DEPT_ID = :deptId
            """, nativeQuery = true)
    long countProjetsActifsParDept(@Param("deptId") Long deptId);

    @Query(value = "SELECT COUNT(*) FROM GERAI_USER.TASKS WHERE STATUS <> 'TERMINE'",
            nativeQuery = true)
    long countTachesOuvertes();

    /**
     * Tâches ouvertes dans les projets d'un département (pour le Chef).
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GERAI_USER.TASKS t
            JOIN GERAI_USER.PROJECTS p ON t.PROJECT_ID = p.PROJECT_ID
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
                FROM GERAI_USER.EMPLOYEES
                WHERE STATUS = 'ACTIF'
            )
            SELECT NVL(
                ROUND(
                    NVL(SUM(lr.DAYS_COUNT), 0) * 100.0
                    / NULLIF(td.DISPO, 0),
                    2
                ), 0
            )
            FROM GERAI_USER.LEAVE_REQUESTS lr, total_dispo td
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
                FROM GERAI_USER.EMPLOYEES
                WHERE STATUS = 'ACTIF' AND DEPT_ID = :deptId
            )
            SELECT NVL(
                ROUND(
                    NVL(SUM(lr.DAYS_COUNT), 0) * 100.0
                    / NULLIF(d.DISPO, 0),
                    2
                ), 0
            )
            FROM GERAI_USER.LEAVE_REQUESTS lr
            JOIN GERAI_USER.EMPLOYEES e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID, dispo d
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
                NVL(lr.REASON, '-')                                     AS MOTIF,
                NVL(lr.REJECTION_REASON, '-')                           AS COMMENTAIRE_RH,
                TO_CHAR(lr.CREATED_AT, 'DD/MM/YYYY HH24:MI')           AS DATE_CREATION
            FROM GERAI_USER.LEAVE_REQUESTS lr
            JOIN      GERAI_USER.EMPLOYEES   e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            LEFT JOIN GERAI_USER.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeCongesForReport();

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
                NVL(lr.REASON, '-')                                     AS MOTIF,
                NVL(lr.REJECTION_REASON, '-')                           AS COMMENTAIRE_RH,
                TO_CHAR(lr.CREATED_AT, 'DD/MM/YYYY HH24:MI')           AS DATE_CREATION
            FROM GERAI_USER.LEAVE_REQUESTS lr
            JOIN GERAI_USER.EMPLOYEES   e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
            JOIN GERAI_USER.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
            WHERE d.DEPT_ID = :deptId
            ORDER BY lr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeCongesParDepartement(@Param("deptId") Long deptId);

    /* ═══════════════════════════════════════════════════════════
       RAPPORTS JASPER — FORMATIONS (globaux + filtrés par dept)
       Colonnes : REQUEST_ID, EMPLOYE_NOM, MATRICULE, DEPARTEMENT,
                  TRAINING_TITLE, PROVIDER, PLANNED_DATE, DURATION_DAYS,
                  ESTIMATED_COST, STATUT, DATE_CREATION
       ═══════════════════════════════════════════════════════════ */

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
            FROM GERAI_USER.TRAINING_REQUESTS tr
            JOIN      GERAI_USER.EMPLOYEES   e ON tr.EMPLOYEE_ID = e.EMPLOYEE_ID
            LEFT JOIN GERAI_USER.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeFormationsForReport();

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
            FROM GERAI_USER.TRAINING_REQUESTS tr
            JOIN GERAI_USER.EMPLOYEES   e ON tr.EMPLOYEE_ID = e.EMPLOYEE_ID
            JOIN GERAI_USER.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
            WHERE d.DEPT_ID = :deptId
            ORDER BY tr.CREATED_AT DESC
            """, nativeQuery = true)
    List<Object[]> listeFormationsParDepartement(@Param("deptId") Long deptId);

    /* ═══════════════════════════════════════════════════════════
       FICHE EMPLOYÉ — Historique depuis V_ALL_DEMANDES
       ═══════════════════════════════════════════════════════════ */

    @Query(value = """
            SELECT
                NVL(TYPE,'-')                               AS TYPE_VAL,
                NVL(STATUT,'-')                             AS STATUT_VAL,
                TO_CHAR(DATE_CREATION,'DD/MM/YYYY')         AS DATE_CREATION,
                NVL(DESCRIPTION,'-')                        AS DESCRIPTION
            FROM GERAI_USER.V_ALL_DEMANDES
            WHERE EMPLOYE_ID = :employeeId
            ORDER BY DATE_CREATION DESC NULLS LAST
            """, nativeQuery = true)
    List<Object[]> demandesParEmploye(@Param("employeeId") Long employeeId);

    /* ═══════════════════════════════════════════════════════════
       STATS PAR DÉPARTEMENT (dashboard global RH)
       ═══════════════════════════════════════════════════════════ */

    @Query(value = """
            SELECT
                dep.NAME                                                        AS DEPT_NAME,
                COUNT(DISTINCT e.EMPLOYEE_ID)                                   AS HEADCOUNT,
                NVL(SUM(CASE WHEN v.TYPE='CONGE'     THEN 1 ELSE 0 END),0)     AS NB_CONGES,
                NVL(SUM(CASE WHEN v.TYPE='FORMATION' THEN 1 ELSE 0 END),0)     AS NB_FORMATIONS,
                COUNT(DISTINCT p.PROJECT_ID)                                    AS NB_PROJETS
            FROM GERAI_USER.DEPARTMENTS dep
            LEFT JOIN GERAI_USER.EMPLOYEES      e ON e.DEPT_ID    = dep.DEPT_ID
                                                 AND e.STATUS     = 'ACTIF'
            LEFT JOIN GERAI_USER.V_ALL_DEMANDES v ON v.EMPLOYE_ID = e.EMPLOYEE_ID
            LEFT JOIN GERAI_USER.PROJECTS       p ON p.DEPT_ID    = dep.DEPT_ID
                                                 AND p.STATUS     = 'EN_COURS'
            GROUP BY dep.DEPT_ID, dep.NAME
            ORDER BY dep.NAME
            """, nativeQuery = true)
    List<Object[]> statsParDepartement();

    /* ═══════════════════════════════════════════════════════════
       TOP 5 — Employés les plus absents (année courante)
       ═══════════════════════════════════════════════════════════ */

    @Query(value = """
            SELECT *
            FROM (
                SELECT
                    NVL(e.FIRST_NAME,'') || ' ' || NVL(e.LAST_NAME,'')   AS NOM,
                    NVL(d.NAME,'Non défini')                              AS DEPARTEMENT,
                    NVL(SUM(lr.DAYS_COUNT), 0)                            AS TOTAL_JOURS
                FROM GERAI_USER.LEAVE_REQUESTS lr
                JOIN      GERAI_USER.EMPLOYEES   e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
                LEFT JOIN GERAI_USER.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
                WHERE lr.STATUS = 'VALIDE_RH'
                  AND TO_CHAR(lr.START_DATE,'YYYY') = TO_CHAR(SYSDATE,'YYYY')
                GROUP BY e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, d.NAME
                ORDER BY TOTAL_JOURS DESC
            )
            WHERE ROWNUM <= 5
            """, nativeQuery = true)
    List<Object[]> top5EmployesAbsences();
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
                FROM GERAI_USER.LEAVE_REQUESTS lr
                JOIN GERAI_USER.EMPLOYEES   e ON lr.EMPLOYEE_ID = e.EMPLOYEE_ID
                JOIN GERAI_USER.DEPARTMENTS d ON e.DEPT_ID      = d.DEPT_ID
                WHERE lr.STATUS = 'VALIDE_RH'
                  AND d.DEPT_ID = :deptId
                  AND TO_CHAR(lr.START_DATE,'YYYY') = TO_CHAR(SYSDATE,'YYYY')
                GROUP BY e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, d.NAME
                ORDER BY TOTAL_JOURS DESC
            )
            WHERE ROWNUM <= 5
            """, nativeQuery = true)
    List<Object[]> top5EmployesAbsencesParDept(@Param("deptId") Long deptId);
}