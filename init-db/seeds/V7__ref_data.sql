-- ═══════════════════════════════════════════════════════════
--  GerAI — S1__ref_data.sql
--  Données de référence obligatoires pour que l'application
--  fonctionne (types de congé, documents, absences...)
-- ═══════════════════════════════════════════════════════════

-- ─── TYPES DE CONGÉ ──────────────────────────────────────────
INSERT INTO HR_PARAMETERS (category, code, label, value, sort_order) VALUES
    ('LEAVE_TYPE', 'CONGE_ANNUEL',    'Congé annuel',           '30', 1);
INSERT INTO HR_PARAMETERS (category, code, label, value, sort_order) VALUES
    ('LEAVE_TYPE', 'CONGE_MALADIE',   'Congé maladie',          '15', 2);
INSERT INTO HR_PARAMETERS (category, code, label, value, sort_order) VALUES
    ('LEAVE_TYPE', 'CONGE_MATERNITE', 'Congé maternité',        '90', 3);
INSERT INTO HR_PARAMETERS (category, code, label, value, sort_order) VALUES
    ('LEAVE_TYPE', 'CONGE_PATERNITE', 'Congé paternité',        '3',  4);
INSERT INTO HR_PARAMETERS (category, code, label, value, sort_order) VALUES
    ('LEAVE_TYPE', 'CONGE_EXCEPTIONNEL', 'Congé exceptionnel',  '5',  5);
INSERT INTO HR_PARAMETERS (category, code, label, value, sort_order) VALUES
    ('LEAVE_TYPE', 'CONGE_SANS_SOLDE', 'Congé sans solde',      '0',  6);

-- ─── TYPES DE DOCUMENT ───────────────────────────────────────
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('DOC_TYPE', 'ATTESTATION_TRAVAIL',  'Attestation de travail',  1);
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('DOC_TYPE', 'ATTESTATION_SALAIRE',  'Attestation de salaire',  2);
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('DOC_TYPE', 'BULLETIN_PAIE',        'Bulletin de paie',        3);
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('DOC_TYPE', 'ORDRE_MISSION',        'Ordre de mission',        4);
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('DOC_TYPE', 'ATTESTATION_CONGE',    'Attestation de congé',    5);
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('DOC_TYPE', 'LETTRE_RECOMMANDATION','Lettre de recommandation',6);

-- ─── TYPES D'ABSENCE ──────────────────────────────────────────
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('ABSENCE_TYPE', 'ABSENCE_INJUSTIFIEE', 'Absence injustifiée',   1);
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('ABSENCE_TYPE', 'ABSENCE_MALADIE',     'Absence maladie',        2);
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('ABSENCE_TYPE', 'RETARD',              'Retard',                  3);
INSERT INTO HR_PARAMETERS (category, code, label, sort_order) VALUES
    ('ABSENCE_TYPE', 'DEPART_ANTICIPE',     'Départ anticipé',         4);

-- ─── PARAMÈTRES GLOBAUX RH ────────────────────────────────────
INSERT INTO HR_PARAMETERS (category, code, label, value) VALUES
    ('GLOBAL', 'JOURS_OUVRES_MOIS',  'Jours ouvrés par mois',  '22');
INSERT INTO HR_PARAMETERS (category, code, label, value) VALUES
    ('GLOBAL', 'HEURE_DEBUT_TRAVAIL','Heure début travail',     '08:30');
INSERT INTO HR_PARAMETERS (category, code, label, value) VALUES
    ('GLOBAL', 'HEURE_FIN_TRAVAIL',  'Heure fin travail',       '17:30');
INSERT INTO HR_PARAMETERS (category, code, label, value) VALUES
    ('GLOBAL', 'DEVISE',             'Devise',                  'TND');
INSERT INTO HR_PARAMETERS (category, code, label, value) VALUES
    ('GLOBAL', 'PAYS',               'Pays',                    'Tunisie');

-- ─── DÉPARTEMENT RACINE ───────────────────────────────────────
INSERT INTO DEPARTMENTS (name, code, description) VALUES
    ('Direction Générale', 'DG', 'Direction Générale de GerAI');
INSERT INTO DEPARTMENTS (name, code, description) VALUES
    ('Ressources Humaines', 'RH', 'Service des Ressources Humaines');
INSERT INTO DEPARTMENTS (name, code, description) VALUES
    ('Informatique', 'IT', 'Direction des Systèmes d''Information');
INSERT INTO DEPARTMENTS (name, code, description) VALUES
    ('Finance', 'FIN', 'Direction Financière');

-- ─── POSTES DE BASE ──────────────────────────────────────────
-- Note : Utilisation de pos_level au lieu de level
INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Directeur Général', 'DG-001',
     (SELECT dept_id FROM DEPARTMENTS WHERE code='DG'), 'N5', 'EXECUTIVE');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Responsable RH', 'RH-001',
     (SELECT dept_id FROM DEPARTMENTS WHERE code='RH'), 'N4', 'MANAGER');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Chef de Projet', 'IT-001',
     (SELECT dept_id FROM DEPARTMENTS WHERE code='IT'), 'N3', 'SENIOR');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Développeur Senior', 'IT-002',
     (SELECT dept_id FROM DEPARTMENTS WHERE code='IT'), 'N2', 'SENIOR');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Développeur Junior', 'IT-003',
     (SELECT dept_id FROM DEPARTMENTS WHERE code='IT'), 'N1', 'JUNIOR');

INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) VALUES
    ('Comptable', 'FIN-001',
     (SELECT dept_id FROM DEPARTMENTS WHERE code='FIN'), 'N2', 'CONFIRME');

COMMIT;