-- ═══════════════════════════════════════════════════════════════════
--  V13 — Demandes de congé de démonstration (Juin 2026)
--  Insère des congés pour l'équipe IT (EMP-0004 à EMP-0007)
--  afin que le planning Gantt du chef (EMP-0003) affiche des données.
-- ═══════════════════════════════════════════════════════════════════

-- Congé annuel — Amira TRABELSI (EMP-0004) — 9 au 20 juin 2026
INSERT INTO LEAVE_REQUESTS (employee_id, leave_type_id, start_date, end_date, days_count, reason, status, created_at)
VALUES (
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0004'),
    (SELECT param_id FROM HR_PARAMETERS WHERE code = 'CONGE_ANNUEL'),
    DATE '2026-06-09',
    DATE '2026-06-20',
    10,
    'Vacances d''été',
    'VALIDE_RH',
    SYSTIMESTAMP - 10
);

-- Congé maladie — Yassine GHARBI (EMP-0005) — 16 au 18 juin 2026
INSERT INTO LEAVE_REQUESTS (employee_id, leave_type_id, start_date, end_date, days_count, reason, status, created_at)
VALUES (
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0005'),
    (SELECT param_id FROM HR_PARAMETERS WHERE code = 'CONGE_MALADIE'),
    DATE '2026-06-16',
    DATE '2026-06-18',
    3,
    'Arrêt médical',
    'VALIDE_CHEF',
    SYSTIMESTAMP - 3
);

-- Congé annuel — Rania HAMROUNI (EMP-0006) — 23 au 27 juin 2026
INSERT INTO LEAVE_REQUESTS (employee_id, leave_type_id, start_date, end_date, days_count, reason, status, created_at)
VALUES (
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0006'),
    (SELECT param_id FROM HR_PARAMETERS WHERE code = 'CONGE_ANNUEL'),
    DATE '2026-06-23',
    DATE '2026-06-27',
    5,
    'Congé personnel',
    'EN_ATTENTE',
    SYSTIMESTAMP - 1
);

-- Congé annuel — Bilel CHAOUACHI (EMP-0007) — 2 au 6 juin 2026
INSERT INTO LEAVE_REQUESTS (employee_id, leave_type_id, start_date, end_date, days_count, reason, status, created_at)
VALUES (
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0007'),
    (SELECT param_id FROM HR_PARAMETERS WHERE code = 'CONGE_ANNUEL'),
    DATE '2026-06-02',
    DATE '2026-06-06',
    5,
    'Récupération heures supplémentaires',
    'VALIDE_RH',
    SYSTIMESTAMP - 20
);

COMMIT;
