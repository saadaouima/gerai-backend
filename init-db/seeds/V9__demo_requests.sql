-- Basculer sur l'utilisateur de l'application
ALTER SESSION SET CURRENT_SCHEMA = gerai_user;

-- 1. DEMANDE DE CONGÉ (Table LEAVE_REQUESTS)
INSERT INTO LEAVE_REQUESTS (employee_id, leave_type_id, start_date, end_date, days_count, reason, status, created_at)
VALUES (
           (SELECT employee_id FROM EMPLOYEES WHERE email = 'imed.gerai@gmail.com'),
           (SELECT param_id FROM HR_PARAMETERS WHERE code = 'CONGE_ANNUEL'),
           DATE '2024-06-01',
           DATE '2024-06-15',
           10,
           'Vacances été',
           'VALIDE_RH',
           SYSTIMESTAMP - 30
       );

-- 2. DEMANDE DE FORMATION (Table TRAINING_REQUESTS)
INSERT INTO TRAINING_REQUESTS (employee_id, training_title, reason, status, created_at)
VALUES (
           (SELECT employee_id FROM EMPLOYEES WHERE email = 'imed.gerai@gmail.com'),
           'Mastering Spring Boot',
           'Certification avancée microservices',
           'APPROUVE_RH',
           SYSTIMESTAMP - 45
       );

-- 3. DEMANDE DE PRÊT (Table LOAN_REQUESTS)
INSERT INTO LOAN_REQUESTS (employee_id, amount, duration_months, status, created_at)
VALUES (
           (SELECT employee_id FROM EMPLOYEES WHERE email = 'nourboussaidi009@gmail.com'),
           5000,
           12,
           'REFUSE',
           SYSTIMESTAMP - 10
       );

COMMIT;