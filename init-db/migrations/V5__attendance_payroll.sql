-- ═══════════════════════════════════════════════════════════
--  GerAI — V5__attendance_payroll.sql
--  Domaine : Attendance & Payroll
--  Tables  : ATTENDANCE, ABSENCES,
--             PAYROLL_PERIODS, PAYSLIPS
-- ═══════════════════════════════════════════════════════════

-- ─── ATTENDANCE ──────────────────────────────────────────────
CREATE TABLE ATTENDANCE (
                            attendance_id   NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            employee_id     NUMBER         NOT NULL,
                            attendance_date DATE           NOT NULL,
                            check_in        TIMESTAMP,
                            check_out       TIMESTAMP,
                            worked_hours    NUMBER(10,2)   GENERATED ALWAYS AS (
        CASE
            WHEN check_in IS NOT NULL AND check_out IS NOT NULL THEN
                (EXTRACT(DAY FROM (check_out - check_in)) * 24) +
                (EXTRACT(HOUR FROM (check_out - check_in))) +
                (EXTRACT(MINUTE FROM (check_out - check_in)) / 60) +
                (EXTRACT(SECOND FROM (check_out - check_in)) / 3600)
            ELSE NULL
        END
    ) VIRTUAL,
                            overtime_hours  NUMBER(4,2)    DEFAULT 0,
                            status          VARCHAR2(30)   DEFAULT 'PRESENT' NOT NULL
        CONSTRAINT ck_att_status
        CHECK (status IN ('PRESENT','ABSENT','RETARD','CONGE','FERIE','WEEKEND')),
                            source          VARCHAR2(30)   DEFAULT 'MANUEL'
        CONSTRAINT ck_att_source
        CHECK (source IN ('MANUEL','BADGEUSE','API')),
                            notes           VARCHAR2(500),
                            CONSTRAINT fk_att_emp   FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id),
                            CONSTRAINT uk_att_emp_date UNIQUE (employee_id, attendance_date)
);

-- ─── ABSENCES ────────────────────────────────────────────────
CREATE TABLE ABSENCES (
                          absence_id     NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          employee_id    NUMBER         NOT NULL,
                          absence_date   DATE           NOT NULL,
                          reason_type_id NUMBER,                         -- FK HR_PARAMETERS (type absence)
                          request_id     NUMBER,                         -- FK LEAVE_REQUESTS si congé validé
                          request_type   VARCHAR2(30),                   -- CONGE, AUTORISATION, MALADIE...
                          is_justified   NUMBER(1)      DEFAULT 0 NOT NULL
                                  CONSTRAINT ck_abs_justified CHECK (is_justified IN (0,1)),
                          notes          VARCHAR2(500),
                          created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                          CONSTRAINT fk_abs_emp    FOREIGN KEY (employee_id)    REFERENCES EMPLOYEES(employee_id),
                          CONSTRAINT fk_abs_reason FOREIGN KEY (reason_type_id) REFERENCES HR_PARAMETERS(param_id),
                          CONSTRAINT uk_abs_emp_date UNIQUE (employee_id, absence_date)
);

-- ─── PAYROLL_PERIODS ─────────────────────────────────────────
CREATE TABLE PAYROLL_PERIODS (
                                 period_id   NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 year        NUMBER(4)      NOT NULL,
                                 month       NUMBER(2)      NOT NULL
                               CONSTRAINT ck_pp_month CHECK (month BETWEEN 1 AND 12),
                                 start_date  DATE           NOT NULL,
                                 end_date    DATE           NOT NULL,
                                 status      VARCHAR2(20)   DEFAULT 'OUVERT' NOT NULL
                               CONSTRAINT ck_pp_status
                               CHECK (status IN ('OUVERT','EN_CALCUL','CLOTURE')),
                                 closed_by   NUMBER,
                                 closed_at   TIMESTAMP,
                                 CONSTRAINT fk_pp_closer FOREIGN KEY (closed_by) REFERENCES EMPLOYEES(employee_id),
                                 CONSTRAINT uk_pp_year_month UNIQUE (year, month)
);

-- ─── PAYSLIPS ────────────────────────────────────────────────
CREATE TABLE PAYSLIPS (
                          payslip_id        NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          period_id         NUMBER         NOT NULL,
                          employee_id       NUMBER         NOT NULL,
                          gross_salary      NUMBER(12,2)   NOT NULL,
                          net_salary        NUMBER(12,2)   NOT NULL,
                          deductions        NUMBER(10,2)   DEFAULT 0,
                          bonuses           NUMBER(10,2)   DEFAULT 0,
                          overtime_pay      NUMBER(10,2)   DEFAULT 0,
                          absence_deduction NUMBER(10,2)   DEFAULT 0,
                          details_json      CLOB,                        -- détail ligne par ligne en JSON
                          document_url      VARCHAR2(500),
                          status            VARCHAR2(20)   DEFAULT 'BROUILLON' NOT NULL
                                     CONSTRAINT ck_ps_status
                                     CHECK (status IN ('BROUILLON','VALIDE','ENVOYE')),
                          generated_at      TIMESTAMP,
                          CONSTRAINT fk_ps_period FOREIGN KEY (period_id)   REFERENCES PAYROLL_PERIODS(period_id),
                          CONSTRAINT fk_ps_emp    FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id),
                          CONSTRAINT uk_ps_period_emp UNIQUE (period_id, employee_id)
);

-- ─── INDEX ──────────────────────────────────────────────────
CREATE INDEX idx_att_emp_date   ON ATTENDANCE(employee_id, attendance_date DESC);
CREATE INDEX idx_att_status     ON ATTENDANCE(status, attendance_date);
CREATE INDEX idx_abs_emp_date   ON ABSENCES(employee_id, absence_date DESC);
CREATE INDEX idx_abs_justified  ON ABSENCES(is_justified);
CREATE INDEX idx_ps_emp_period  ON PAYSLIPS(employee_id, period_id);
CREATE INDEX idx_ps_status      ON PAYSLIPS(status);