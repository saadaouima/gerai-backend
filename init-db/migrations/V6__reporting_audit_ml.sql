-- ═══════════════════════════════════════════════════════════
--  GerAI — V6__reporting_audit_ml.sql
--  Domaine : Reporting & Audit & ML
--  Tables  : REPORT_LOGS, BI_QUERY_LOGS,
--             AUDIT_LOGS, ML_PREDICTIONS,
--             ABSENCE_STATS (vue agrégée pour analytics)
-- ═══════════════════════════════════════════════════════════

-- ─── REPORT_LOGS ─────────────────────────────────────────────
CREATE TABLE REPORT_LOGS (
                             log_id          NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             generated_by    NUMBER         NOT NULL,
                             report_type     VARCHAR2(60)   NOT NULL
                                   CONSTRAINT ck_rlog_type
                                   CHECK (report_type IN ('CONGES','FORMATIONS','DASHBOARD',
                                                          'FICHE_EMPLOYE','PAIE','PERSONNALISE')),
                             report_name     VARCHAR2(200),
                             parameters_json CLOB,                          -- filtres appliqués en JSON
                             format          VARCHAR2(10)   DEFAULT 'PDF' NOT NULL
                                   CONSTRAINT ck_rlog_format
                                   CHECK (format IN ('PDF','EXCEL','CSV')),
                             file_url        VARCHAR2(500),
                             rows_count      NUMBER,
                             duration_ms     NUMBER,
                             status          VARCHAR2(20)   DEFAULT 'SUCCES' NOT NULL
                                   CONSTRAINT ck_rlog_status
                                   CHECK (status IN ('SUCCES','ECHEC','EN_COURS')),
                             generated_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                             CONSTRAINT fk_rlog_emp FOREIGN KEY (generated_by) REFERENCES EMPLOYEES(employee_id)
);

-- ─── BI_QUERY_LOGS ───────────────────────────────────────────
CREATE TABLE BI_QUERY_LOGS (
                               query_id       NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               employee_id    NUMBER         NOT NULL,
                               dashboard_name VARCHAR2(150),
                               query_hash     VARCHAR2(64),
                               kpi_name       VARCHAR2(100),
                               duration_ms    NUMBER,
                               rows_returned  NUMBER,
                               executed_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                               CONSTRAINT fk_bi_emp FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id)
);

-- ─── AUDIT_LOGS ──────────────────────────────────────────────
CREATE TABLE AUDIT_LOGS (
                            log_id       NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            user_id      VARCHAR2(255)  NOT NULL,          -- Keycloak UUID
                            employee_id  NUMBER,
                            action       VARCHAR2(30)   NOT NULL
                                CONSTRAINT ck_audit_action
                                CHECK (action IN ('CREATE','UPDATE','DELETE',
                                                  'READ','LOGIN','LOGOUT','EXPORT')),
                            entity_type  VARCHAR2(60)   NOT NULL,          -- EMPLOYEE, DEMANDE, CONTRAT...
                            entity_id    NUMBER,
                            entity_code  VARCHAR2(100),
                            old_values   CLOB,                             -- JSON avant modification
                            new_values   CLOB,                             -- JSON après modification
                            ip_address   VARCHAR2(45),
                            user_agent   VARCHAR2(500),
                            module       VARCHAR2(60),
                            created_at   TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                            CONSTRAINT fk_audit_emp FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id)
);

-- ─── ML_PREDICTIONS ──────────────────────────────────────────
CREATE TABLE ML_PREDICTIONS (
                                prediction_id    NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                employee_id      NUMBER         NOT NULL,
                                model_name       VARCHAR2(100)  NOT NULL,
                                model_version    VARCHAR2(20),
                                prediction_type  VARCHAR2(60)   NOT NULL
                                    CONSTRAINT ck_ml_type
                                    CHECK (prediction_type IN ('ABSENTEISME',
                                                               'TURNOVER',
                                                               'PERFORMANCE',
                                                               'CONGE_PREDIT')),
                                score            NUMBER(6,4),                  -- ex: 0.7823 = 78.23% de risque
                                risk_level       VARCHAR2(20)
                                    CONSTRAINT ck_ml_risk
                                    CHECK (risk_level IN ('FAIBLE','MOYEN','ELEVE','CRITIQUE')),
                                features_json    CLOB,                         -- variables d'entrée du modèle
                                explanation      CLOB,                         -- SHAP values ou texte d'explication
                                is_acknowledged  NUMBER(1)      DEFAULT 0
                                    CONSTRAINT ck_ml_ack CHECK (is_acknowledged IN (0,1)),
                                predicted_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                CONSTRAINT fk_ml_emp FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id)
);

-- ─── ABSENCE_STATS ───────────────────────────────────────────
-- Table agrégée alimentée par le Kafka consumer d'analytics-service
-- Sert de source pour Power BI et le module ML
CREATE TABLE ABSENCE_STATS (
                               stat_id          NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               employee_id      VARCHAR2(100)  NOT NULL,      -- Keycloak user_id
                               employe_nom      VARCHAR2(150),
                               annee            NUMBER(4)      NOT NULL,
                               mois             NUMBER(2)      NOT NULL
                                    CONSTRAINT ck_as_mois CHECK (mois BETWEEN 1 AND 12),
                               nb_jours_conge   NUMBER         DEFAULT 0,
                               nb_demandes      NUMBER         DEFAULT 0,
                               nb_validees      NUMBER         DEFAULT 0,
                               nb_refusees      NUMBER         DEFAULT 0,
                               date_mise_a_jour TIMESTAMP      DEFAULT SYSTIMESTAMP,
                               CONSTRAINT uk_absence UNIQUE (employee_id, annee, mois)
);

-- ─── INDEX ──────────────────────────────────────────────────
CREATE INDEX idx_audit_user_id     ON AUDIT_LOGS(user_id, created_at DESC);
CREATE INDEX idx_audit_entity      ON AUDIT_LOGS(entity_type, entity_id);
CREATE INDEX idx_audit_action      ON AUDIT_LOGS(action, created_at DESC);
CREATE INDEX idx_ml_emp_type       ON ML_PREDICTIONS(employee_id, prediction_type);
CREATE INDEX idx_ml_risk           ON ML_PREDICTIONS(risk_level, predicted_at DESC);
CREATE INDEX idx_rlog_emp          ON REPORT_LOGS(generated_by, generated_at DESC);
--CREATE INDEX idx_as_emp_year_mois  ON ABSENCE_STATS(employee_id, annee, mois);