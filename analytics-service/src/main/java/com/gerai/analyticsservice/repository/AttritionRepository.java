package com.gerai.analyticsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.gerai.analyticsservice.model.Demande;

import java.util.List;

/**
 * Fetches all data needed to compute per-employee attrition risk scores
 * in a single optimised Oracle query.
 *
 * Columns returned (Object[]):
 *  [0]  EMPLOYEE_ID       – Long/BigDecimal
 *  [1]  FIRST_NAME        – String
 *  [2]  LAST_NAME         – String
 *  [3]  PHOTO_URL         – String (nullable)
 *  [4]  HIRE_DATE         – java.sql.Date
 *  [5]  DEPARTEMENT       – String
 *  [6]  POSTE             – String
 *  [7]  ABSENCE_DAYS      – Number (approved leave days, past 12 months)
 *  [8]  TOTAL_DEMANDES    – Number (all request types, past 12 months)
 *  [9]  DEMANDES_REFUSEES – Number (refused requests, past 12 months)
 *  [10] PROJETS_ACTIFS    – Number (active project memberships)
 */
@Repository
public interface AttritionRepository extends JpaRepository<Demande, Long> {

    /**
     * Charge les données brutes nécessaires au calcul du score d'attrition
     * pour chaque employé actif, en un seul appel Oracle optimisé.
     * Les 4 facteurs (ancienneté, absences, taux de refus, engagement projet)
     * sont calculés via des sous-requêtes et joints directement.
     *
     * @return la liste des lignes brutes Oracle, une par employé actif ;
     *         les colonnes sont décrites dans le Javadoc de la classe
     */
    @Query(value = """
            SELECT
                e.EMPLOYEE_ID,
                e.FIRST_NAME,
                e.LAST_NAME,
                e.PHOTO_URL,
                e.HIRE_DATE,
                NVL(d.NAME,   'Non défini')    AS DEPARTEMENT,
                NVL(pos.TITLE,'Non spécifié')  AS POSTE,
                NVL(abs_s.ABSENCE_DAYS,    0)  AS ABSENCE_DAYS,
                NVL(req_s.TOTAL_DEMANDES,  0)  AS TOTAL_DEMANDES,
                NVL(req_s.DEMANDES_REF,    0)  AS DEMANDES_REFUSEES,
                NVL(prj_s.PROJETS_ACTIFS,  0)  AS PROJETS_ACTIFS
            FROM GERAI.EMPLOYEES e
            LEFT JOIN GERAI.DEPARTMENTS d   ON d.DEPT_ID     = e.DEPT_ID
            LEFT JOIN GERAI.POSITIONS   pos ON pos.POSITION_ID = e.POSITION_ID
            -- Factor 2: approved absence days in the last 12 months
            LEFT JOIN (
                SELECT EMPLOYEE_ID,
                       SUM(DAYS_COUNT) AS ABSENCE_DAYS
                FROM   GERAI.LEAVE_REQUESTS
                WHERE  STATUS     = 'VALIDE_RH'
                  AND  START_DATE >= ADD_MONTHS(SYSDATE, -12)
                GROUP BY EMPLOYEE_ID
            ) abs_s ON abs_s.EMPLOYEE_ID = e.EMPLOYEE_ID
            -- Factor 3: request rejection rate in the last 12 months
            LEFT JOIN (
                SELECT EMPLOYE_ID,
                       COUNT(*) AS TOTAL_DEMANDES,
                       SUM(CASE WHEN STATUT = 'REFUSE' THEN 1 ELSE 0 END) AS DEMANDES_REF
                FROM   GERAI.V_ALL_DEMANDES
                WHERE  DATE_CREATION >= ADD_MONTHS(SYSDATE, -12)
                GROUP BY EMPLOYE_ID
            ) req_s ON req_s.EMPLOYE_ID = e.EMPLOYEE_ID
            -- Factor 4: active project memberships
            LEFT JOIN (
                SELECT pm.EMPLOYEE_ID,
                       COUNT(DISTINCT pm.PROJECT_ID) AS PROJETS_ACTIFS
                FROM   GERAI.PROJECT_MEMBERS pm
                JOIN   GERAI.PROJECTS p ON p.PROJECT_ID = pm.PROJECT_ID
                WHERE  pm.IS_ACTIVE = 1
                  AND  p.STATUS     = 'EN_COURS'
                GROUP BY pm.EMPLOYEE_ID
            ) prj_s ON prj_s.EMPLOYEE_ID = e.EMPLOYEE_ID
            WHERE e.STATUS = 'ACTIF'
            ORDER BY e.EMPLOYEE_ID
            """, nativeQuery = true)
    List<Object[]> findAttritionRawData();
}
