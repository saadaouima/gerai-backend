-- ==========================================================
-- GerAI — S2__demo_employees.sql
-- ==========================================================

-- 1. IMED : ADMIN RH
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, dept_id, position_id, hire_date, status
) VALUES (
             'd8bd5a01-4468-42fd-bfc6-7a0197bd0daf',
             'EMP-RH-001',
             'Imed', 'Gerai',
             'imed.gerai@gmail.com',
             (SELECT dept_id FROM DEPARTMENTS WHERE code = 'RH'),
             (SELECT position_id FROM POSITIONS WHERE code = 'RH-001'),
             DATE '2022-01-01',
             'ACTIF'
         );

-- 2. MARIEM : CHEF IT
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, dept_id, position_id, hire_date, status
) VALUES (
             'd09c31ae-593b-49e5-839b-ba1e77ea4444',
             'EMP-IT-001',
             'Mariem', 'Saadaoui',
             'mariemsaadaoui@gmail.com',
             (SELECT dept_id FROM DEPARTMENTS WHERE code = 'IT'),
             (SELECT position_id FROM POSITIONS WHERE code = 'IT-001'),
             DATE '2021-06-01',
             'ACTIF'
         );

-- 3. NOUR : EMPLOYÉ DÉVELOPPEUR
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, dept_id, position_id, manager_id, hire_date, status
) VALUES (
             'b66cdc6f-a84e-4741-894a-c6cbef8a3531',
             'EMP-IT-002',
             'Nour El Houda', 'Boussaidi',
             'nourboussaidi009@gmail.com',
             (SELECT dept_id FROM DEPARTMENTS WHERE code = 'IT'),
             (SELECT position_id FROM POSITIONS WHERE code = 'IT-002'),
             (SELECT employee_id FROM EMPLOYEES WHERE email = 'mariemsaadaoui@gmail.com'),
             DATE '2023-03-01',
             'ACTIF'
         );

-- ----------------------------------------------------------
-- DONNÉES SOCIALES & CONTRATS
-- ----------------------------------------------------------
INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE email = 'imed.gerai@gmail.com'), 'MARIE', 'STB');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE email = 'imed.gerai@gmail.com'), 'CDI', DATE '2022-01-01', 5500.00, 4800.00, 'ACTIF');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE email = 'mariemsaadaoui@gmail.com'), 'MARIE', 'BNA');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE email = 'mariemsaadaoui@gmail.com'), 'CDI', DATE '2021-06-01', 4200.00, 3600.00, 'ACTIF');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE email = 'nourboussaidi009@gmail.com'), 'CELIBATAIRE');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE email = 'nourboussaidi009@gmail.com'), 'CDI', DATE '2023-03-01', 2800.00, 2400.00, 'ACTIF');

-- ----------------------------------------------------------
-- MISE À JOUR DES MANAGERS DE DÉPARTEMENTS
-- ----------------------------------------------------------
UPDATE DEPARTMENTS SET manager_id = (SELECT employee_id FROM EMPLOYEES WHERE email = 'imed.gerai@gmail.com') WHERE code = 'RH';
UPDATE DEPARTMENTS SET manager_id = (SELECT employee_id FROM EMPLOYEES WHERE email = 'mariemsaadaoui@gmail.com') WHERE code = 'IT';

-- NOTE : J'ai retiré l'INSERT dans LEAVE_BALANCES car la table n'est pas dans ton V1.
-- Si tu as un fichier V2 qui crée cette table, mets cet insert dans un fichier S3__leave_data.sql

COMMIT;