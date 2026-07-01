-- ═══════════════════════════════════════════════════════════════════
--  V14 — Demandes de formation EN_ATTENTE pour l'équipe IT (chef EMP-0003)
--  Permet de tester le workflow de validation chef dans la vue "Formations – Mon Équipe".
-- ═══════════════════════════════════════════════════════════════════

ALTER SESSION SET CURRENT_SCHEMA = gerai_user;

-- EMP-0004 (Amira Trabelsi) — demande en attente de validation chef
INSERT INTO TRAINING_REQUESTS (
    employee_id, training_title, provider, reason,
    duration_days, mode_formation, estimated_cost, status, created_at
) VALUES (
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0004'),
    'React & Angular Avancé', 'Udemy Business',
    'Renforcer les compétences frontend pour le projet Synapse',
    3, 'DISTANCIEL', 450.00, 'EN_ATTENTE', SYSTIMESTAMP - 5
);

-- EMP-0005 (Yassine Gharbi) — demande en attente de validation chef
INSERT INTO TRAINING_REQUESTS (
    employee_id, training_title, provider, reason,
    duration_days, mode_formation, estimated_cost, status, created_at
) VALUES (
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0005'),
    'Kubernetes et CI/CD avec GitLab', 'DataArt Training',
    'Automatiser les déploiements microservices Spring Boot',
    5, 'PRESENTIEL', 1800.00, 'EN_ATTENTE', SYSTIMESTAMP - 3
);

-- EMP-0006 (Rania Hamrouni) — déjà approuvée par le chef (statut APPROUVE_CHEF)
INSERT INTO TRAINING_REQUESTS (
    employee_id, training_title, provider, reason,
    duration_days, mode_formation, estimated_cost, status, created_at
) VALUES (
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0006'),
    'Oracle PL/SQL Avancé', 'Oracle University',
    'Optimisation des requêtes pour la base Oracle 23ai',
    4, 'HYBRIDE', 1200.00, 'APPROUVE_CHEF', SYSTIMESTAMP - 15
);

-- EMP-0007 (Bilel Chaouachi) — demande en attente de validation chef
INSERT INTO TRAINING_REQUESTS (
    employee_id, training_title, provider, reason,
    duration_days, mode_formation, status, created_at
) VALUES (
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0007'),
    'Spring Boot Microservices & Kafka', 'Pluralsight',
    'Approfondir l''architecture événementielle du projet',
    4, 'DISTANCIEL', 'EN_ATTENTE', SYSTIMESTAMP - 1
);

COMMIT;
