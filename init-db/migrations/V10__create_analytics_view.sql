-- ═══════════════════════════════════════════════════════════════════════════
--  GerAI – Vue unifiée V_ALL_DEMANDES
--  Cette vue consolide toutes les tables de demandes pour le service Analytics.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE VIEW V_ALL_DEMANDES AS

-- ── 1. Congés ──────────────────────────────────────────────────────────────
SELECT
    lr.request_id                                   AS id,
    lr.employee_id                                  AS employe_id,
    e.first_name || ' ' || e.last_name              AS employe_nom,
    d.name                                          AS departement,
    'CONGE'                                         AS type,
    lr.status                                       AS statut,
    lr.reason                                       AS description,
    lr.start_date                                   AS date_debut,
    lr.end_date                                     AS date_fin,
    lr.created_at                                   AS date_creation,
    lr.days_count                                   AS nb_jours,
    TO_NUMBER(NULL)                                 AS montant,
    lr.approved_by                                  AS approuve_par_rh,
    lr.approved_at                                  AS date_approbation_rh,
    lr.rejection_reason                             AS commentaire_rh,
    CAST(NULL AS VARCHAR2(500))                     AS commentaire_chef
FROM LEAVE_REQUESTS lr
         JOIN EMPLOYEES e ON lr.employee_id = e.employee_id
         LEFT JOIN DEPARTMENTS d ON e.dept_id = d.dept_id

UNION ALL

-- ── 2. Formations ──────────────────────────────────────────────────────────
SELECT
    tr.request_id                                   AS id,
    tr.employee_id                                  AS employe_id,
    e.first_name || ' ' || e.last_name              AS employe_nom,
    d.name                                          AS departement,
    'FORMATION'                                     AS type,
    tr.status                                       AS statut,
    tr.training_title                               AS description,
    tr.planned_date                                 AS date_debut,
    CAST(NULL AS DATE)                              AS date_fin,
    tr.created_at                                   AS date_creation,
    tr.duration_days                                AS nb_jours,
    tr.estimated_cost                               AS montant,
    tr.approved_by                                  AS approuve_par_rh,
    CAST(NULL AS TIMESTAMP)                         AS date_approbation_rh,
    CAST(NULL AS VARCHAR2(500))                     AS commentaire_rh,
    CAST(NULL AS VARCHAR2(500))                     AS commentaire_chef
FROM TRAINING_REQUESTS tr
         JOIN EMPLOYEES e ON tr.employee_id = e.employee_id
         LEFT JOIN DEPARTMENTS d ON e.dept_id = d.dept_id

UNION ALL

-- ── 3. Prêts ───────────────────────────────────────────────────────────────
SELECT
    lor.request_id                                  AS id,
    lor.employee_id                                 AS employe_id,
    e.first_name || ' ' || e.last_name              AS employe_nom,
    d.name                                          AS departement,
    'PRET'                                          AS type,
    lor.status                                      AS statut,
    lor.reason                                      AS description,
    CAST(NULL AS DATE)                              AS date_debut,
    CAST(NULL AS DATE)                              AS date_fin,
    lor.created_at                                  AS date_creation,
    lor.duration_months                             AS nb_jours,
    lor.amount                                      AS montant,
    lor.approved_by                                 AS approuve_par_rh,
    lor.approved_at                                 AS date_approbation_rh,
    lor.rejection_reason                            AS commentaire_rh,
    CAST(NULL AS VARCHAR2(500))                     AS commentaire_chef
FROM LOAN_REQUESTS lor
         JOIN EMPLOYEES e ON lor.employee_id = e.employee_id
         LEFT JOIN DEPARTMENTS d ON e.dept_id = d.dept_id

UNION ALL

-- ── 4. Documents administratifs ────────────────────────────────────────────
SELECT
    dr.request_id                                   AS id,
    dr.employee_id                                  AS employe_id,
    e.first_name || ' ' || e.last_name              AS employe_nom,
    d.name                                          AS departement,
    'DOCUMENT'                                      AS type,
    dr.status                                       AS statut,
    dr.reason                                       AS description,
    CAST(NULL AS DATE)                              AS date_debut,
    CAST(NULL AS DATE)                              AS date_fin,
    dr.created_at                                   AS date_creation,
    TO_NUMBER(NULL)                                 AS nb_jours,
    TO_NUMBER(NULL)                                 AS montant,
    dr.processed_by                                 AS approuve_par_rh,
    dr.processed_at                                 AS date_approbation_rh,
    CAST(NULL AS VARCHAR2(500))                     AS commentaire_rh,
    CAST(NULL AS VARCHAR2(500))                     AS commentaire_chef
FROM DOCUMENT_REQUESTS dr
         JOIN EMPLOYEES e ON dr.employee_id = e.employee_id
         LEFT JOIN DEPARTMENTS d ON e.dept_id = d.dept_id

UNION ALL

-- ── 5. Autorisations ───────────────────────────────────────────────────────
SELECT
    ar.request_id                                   AS id,
    ar.employee_id                                  AS employe_id,
    e.first_name || ' ' || e.last_name              AS employe_nom,
    d.name                                          AS departement,
    'AUTORISATION'                                  AS type,
    ar.status                                       AS statut,
    ar.reason                                       AS description,
    ar.start_datetime                               AS date_debut,
    ar.end_datetime                                 AS date_fin,
    ar.created_at                                   AS date_creation,
    ar.duration_hours                               AS nb_jours,
    TO_NUMBER(NULL)                                 AS montant,
    ar.approved_by                                  AS approuve_par_rh,
    ar.approved_at                                  AS date_approbation_rh,
    CAST(NULL AS VARCHAR2(500))                     AS commentaire_rh,
    CAST(NULL AS VARCHAR2(500))                     AS commentaire_chef
FROM AUTHORIZATION_REQUESTS ar
         JOIN EMPLOYEES e ON ar.employee_id = e.employee_id
         LEFT JOIN DEPARTMENTS d ON e.dept_id = d.dept_id;

-- Note : La table ABSENCE_STATS est déjà gérée par V6, inutile de la recréer ici.