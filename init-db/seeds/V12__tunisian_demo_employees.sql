-- ═══════════════════════════════════════════════════════════════════
--  V12 — Données de démonstration Tunisiennes (Arabsoft)
--  Remplace les anciennes données de démo (V8/V9) par des employés
--  tunisiens réalistes avec user_id = NULL.
--  Les comptes Keycloak seront créés via l'interface Angular
--  (/admin/employes/ajouter) pour lier les UUID du realm SYNAPSE.
-- ═══════════════════════════════════════════════════════════════════

-- ─── 1. SUPPRESSION DONNÉES DE DÉMO PRÉCÉDENTES (ordre FK) ─────────

-- Demandes liées aux anciens employés
DELETE FROM LEAVE_REQUESTS    WHERE employee_id IN (SELECT employee_id FROM EMPLOYEES WHERE employee_code LIKE 'EMP-%');
DELETE FROM LOAN_REQUESTS     WHERE employee_id IN (SELECT employee_id FROM EMPLOYEES WHERE employee_code LIKE 'EMP-%');
DELETE FROM TRAINING_REQUESTS WHERE employee_id IN (SELECT employee_id FROM EMPLOYEES WHERE employee_code LIKE 'EMP-%');
DELETE FROM DOCUMENT_REQUESTS WHERE employee_id IN (SELECT employee_id FROM EMPLOYEES WHERE employee_code LIKE 'EMP-%');
DELETE FROM LEAVE_BALANCES    WHERE employee_id IN (SELECT employee_id FROM EMPLOYEES WHERE employee_code LIKE 'EMP-%');

-- Données RH liées
DELETE FROM CONTRACTS      WHERE employee_id IN (SELECT employee_id FROM EMPLOYEES WHERE employee_code LIKE 'EMP-%');
DELETE FROM EMPLOYEE_SOCIAL WHERE employee_id IN (SELECT employee_id FROM EMPLOYEES WHERE employee_code LIKE 'EMP-%');

-- Libérer les FK circulaires avant suppression
UPDATE DEPARTMENTS SET manager_id = NULL;
UPDATE EMPLOYEES   SET manager_id = NULL WHERE employee_code LIKE 'EMP-%';

-- Suppression des employés de démo
DELETE FROM EMPLOYEES WHERE employee_code LIKE 'EMP-%';


-- ─── 2. NOUVEAUX DÉPARTEMENTS ────────────────────────────────────────

INSERT INTO DEPARTMENTS (name, code, description) VALUES
    ('Commerce', 'COM', 'Direction Commerciale et Développement des Affaires');

INSERT INTO DEPARTMENTS (name, code, description) VALUES
    ('Technique', 'TECH', 'Infrastructure, DevOps et Systèmes');


-- ─── 3. NOUVEAUX POSTES ──────────────────────────────────────────────

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Gestionnaire RH', 'RH-002',
     (SELECT dept_id FROM DEPARTMENTS WHERE code = 'RH'), 'N2', 'CONFIRME');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Analyste Fonctionnel', 'IT-004',
     (SELECT dept_id FROM DEPARTMENTS WHERE code = 'IT'), 'N2', 'CONFIRME');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Ingénieur DevOps', 'TECH-001',
     (SELECT dept_id FROM DEPARTMENTS WHERE code = 'TECH'), 'N3', 'SENIOR');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Responsable Finance', 'FIN-002',
     (SELECT dept_id FROM DEPARTMENTS WHERE code = 'FIN'), 'N3', 'MANAGER');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Directeur Commercial', 'COM-001',
     (SELECT dept_id FROM DEPARTMENTS WHERE code = 'COM'), 'N4', 'MANAGER');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Commercial Senior', 'COM-002',
     (SELECT dept_id FROM DEPARTMENTS WHERE code = 'COM'), 'N2', 'CONFIRME');


-- ─── 4. EMPLOYÉS TUNISIENS (user_id = NULL → lier via Angular UI) ────

-- EMP-0001 : Sana KHELIFI — Responsable RH
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, hire_date, status
) VALUES (
    NULL, 'EMP-0001', 'Sana', 'Khelifi',
    's.khelifi@arabsoft.tn', '+216 25 310 742', 'F',
    DATE '1990-04-15', '09345621',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'RH'),
    (SELECT position_id FROM POSITIONS WHERE code = 'RH-001'),
    DATE '2019-03-01', 'ACTIF'
);

-- EMP-0002 : Dhia Eddine MANSOURI — Gestionnaire RH
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, manager_id, hire_date, status
) VALUES (
    NULL, 'EMP-0002', 'Dhia Eddine', 'Mansouri',
    'd.mansouri@arabsoft.tn', '+216 52 478 193', 'M',
    DATE '1995-11-20', '09578234',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'RH'),
    (SELECT position_id FROM POSITIONS WHERE code = 'RH-002'),
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0001'),
    DATE '2022-09-01', 'ACTIF'
);

-- EMP-0003 : Karim BEN SALAH — Chef de Projet IT
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, hire_date, status
) VALUES (
    NULL, 'EMP-0003', 'Karim', 'Ben Salah',
    'k.bensalah@arabsoft.tn', '+216 98 654 017', 'M',
    DATE '1987-07-03', '08764512',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'IT'),
    (SELECT position_id FROM POSITIONS WHERE code = 'IT-001'),
    DATE '2018-06-15', 'ACTIF'
);

-- EMP-0004 : Amira TRABELSI — Développeur Senior
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, manager_id, hire_date, status
) VALUES (
    NULL, 'EMP-0004', 'Amira', 'Trabelsi',
    'a.trabelsi@arabsoft.tn', '+216 27 891 364', 'F',
    DATE '1993-02-28', '09234178',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'IT'),
    (SELECT position_id FROM POSITIONS WHERE code = 'IT-002'),
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0003'),
    DATE '2020-01-10', 'ACTIF'
);

-- EMP-0005 : Yassine GHARBI — Développeur Senior
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, manager_id, hire_date, status
) VALUES (
    NULL, 'EMP-0005', 'Yassine', 'Gharbi',
    'y.gharbi@arabsoft.tn', '+216 55 203 871', 'M',
    DATE '1992-09-14', '09102947',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'IT'),
    (SELECT position_id FROM POSITIONS WHERE code = 'IT-002'),
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0003'),
    DATE '2021-04-01', 'ACTIF'
);

-- EMP-0006 : Rania HAMROUNI — Développeur Junior
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, manager_id, hire_date, status
) VALUES (
    NULL, 'EMP-0006', 'Rania', 'Hamrouni',
    'r.hamrouni@arabsoft.tn', '+216 93 715 482', 'F',
    DATE '1999-05-22', '09876345',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'IT'),
    (SELECT position_id FROM POSITIONS WHERE code = 'IT-003'),
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0003'),
    DATE '2023-07-01', 'ACTIF'
);

-- EMP-0007 : Bilel CHAOUACHI — Développeur Junior
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, manager_id, hire_date, status
) VALUES (
    NULL, 'EMP-0007', 'Bilel', 'Chaouachi',
    'b.chaouachi@arabsoft.tn', '+216 21 439 506', 'M',
    DATE '2000-01-08', '09943217',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'IT'),
    (SELECT position_id FROM POSITIONS WHERE code = 'IT-003'),
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0003'),
    DATE '2023-09-01', 'ACTIF'
);

-- EMP-0008 : Mohamed Ali SOUISSI — Responsable Finance
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, hire_date, status
) VALUES (
    NULL, 'EMP-0008', 'Mohamed Ali', 'Souissi',
    'm.souissi@arabsoft.tn', '+216 50 128 673', 'M',
    DATE '1985-12-11', '08512390',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'FIN'),
    (SELECT position_id FROM POSITIONS WHERE code = 'FIN-002'),
    DATE '2017-09-01', 'ACTIF'
);

-- EMP-0009 : Asma BELHAJ — Comptable
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, manager_id, hire_date, status
) VALUES (
    NULL, 'EMP-0009', 'Asma', 'Belhaj',
    'a.belhaj@arabsoft.tn', '+216 96 874 201', 'F',
    DATE '1994-08-30', '09467823',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'FIN'),
    (SELECT position_id FROM POSITIONS WHERE code = 'FIN-001'),
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0008'),
    DATE '2022-02-01', 'ACTIF'
);

-- EMP-0010 : Riadh BEN AMOR — Directeur Général
INSERT INTO EMPLOYEES (
    user_id, employee_code, first_name, last_name,
    email, phone, gender, birth_date, national_id,
    dept_id, position_id, hire_date, status
) VALUES (
    NULL, 'EMP-0010', 'Riadh', 'Ben Amor',
    'r.benamor@arabsoft.tn', '+216 71 340 892', 'M',
    DATE '1975-03-17', '07534901',
    (SELECT dept_id FROM DEPARTMENTS WHERE code = 'DG'),
    (SELECT position_id FROM POSITIONS WHERE code = 'DG-001'),
    DATE '2016-01-01', 'ACTIF'
);


-- ─── 5. CONTRATS ─────────────────────────────────────────────────────

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0001'),'CDI',DATE '2019-03-01',5800.00,4900.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0002'),'CDI',DATE '2022-09-01',3200.00,2750.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0003'),'CDI',DATE '2018-06-15',6500.00,5500.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0004'),'CDI',DATE '2020-01-10',4800.00,4100.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0005'),'CDI',DATE '2021-04-01',4500.00,3850.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0006'),'CDI',DATE '2023-07-01',2800.00,2400.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0007'),'CDI',DATE '2023-09-01',2800.00,2400.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0008'),'CDI',DATE '2017-09-01',5200.00,4400.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0009'),'CDI',DATE '2022-02-01',3500.00,3000.00,'ACTIF');

INSERT INTO CONTRACTS (employee_id, type, start_date, gross_salary, net_salary, status)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0010'),'CDI',DATE '2016-01-01',12000.00,10200.00,'ACTIF');


-- ─── 6. DONNÉES SOCIALES ─────────────────────────────────────────────

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0001'),'MARIE',1,'STB');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0002'),'CELIBATAIRE',0,'Attijari Bank');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0003'),'MARIE',2,'BNA');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0004'),'CELIBATAIRE',0,'BIAT');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0005'),'MARIE',1,'BNA');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0006'),'CELIBATAIRE',0,'STB');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0007'),'CELIBATAIRE',0,'Attijari Bank');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0008'),'MARIE',3,'BIAT');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0009'),'MARIE',1,'STB');

INSERT INTO EMPLOYEE_SOCIAL (employee_id, marital_status, dependents_count, bank_name)
VALUES ((SELECT employee_id FROM EMPLOYEES WHERE employee_code='EMP-0010'),'MARIE',2,'BNA');


-- ─── 7. MANAGERS DES DÉPARTEMENTS ────────────────────────────────────

UPDATE DEPARTMENTS SET manager_id =
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0001')
WHERE code = 'RH';

UPDATE DEPARTMENTS SET manager_id =
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0003')
WHERE code = 'IT';

UPDATE DEPARTMENTS SET manager_id =
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0008')
WHERE code = 'FIN';

UPDATE DEPARTMENTS SET manager_id =
    (SELECT employee_id FROM EMPLOYEES WHERE employee_code = 'EMP-0010')
WHERE code = 'DG';


COMMIT;
