-- ═══════════════════════════════════════════════════════════
--  GerAI — V3__hr_requests.sql
--  Domaine : HR Requests (toutes les demandes RH)
--  Tables  : LEAVE_REQUESTS, LEAVE_BALANCES,
--             DOCUMENT_REQUESTS, LOAN_REQUESTS,
--             AUTHORIZATION_REQUESTS,
--             TRAINING_REQUESTS, TRAINING_SESSIONS,
--             TRAINING_ENROLLMENTS
-- ═══════════════════════════════════════════════════════════

-- ─── LEAVE_REQUESTS ─────────────────────────────────────────
CREATE TABLE LEAVE_REQUESTS (
                                request_id      NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                employee_id     NUMBER         NOT NULL,
                                leave_type_id   NUMBER         NOT NULL,       -- FK vers HR_PARAMETERS
                                start_date      DATE           NOT NULL,
                                end_date        DATE           NOT NULL,
                                days_count      NUMBER(4,1)    NOT NULL,
                                reason          VARCHAR2(500),
                                status          VARCHAR2(20)   DEFAULT 'EN_ATTENTE' NOT NULL
       CONSTRAINT ck_leave_status
       CHECK (status IN ('EN_ATTENTE','VALIDE_CHEF',
                         'VALIDE_RH','REFUSE','ANNULE')),
                                approved_by     NUMBER,
                                approved_at     TIMESTAMP,
                                rejection_reason VARCHAR2(500),
                                attachment_url  VARCHAR2(500),
                                created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                CONSTRAINT fk_leave_emp       FOREIGN KEY (employee_id)   REFERENCES EMPLOYEES(employee_id),
                                CONSTRAINT fk_leave_type      FOREIGN KEY (leave_type_id) REFERENCES HR_PARAMETERS(param_id),
                                CONSTRAINT fk_leave_approver  FOREIGN KEY (approved_by)   REFERENCES EMPLOYEES(employee_id),
                                CONSTRAINT ck_leave_dates     CHECK (end_date >= start_date)
);

-- ─── LEAVE_BALANCES ─────────────────────────────────────────
CREATE TABLE LEAVE_BALANCES (
                                balance_id      NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                employee_id     NUMBER         NOT NULL,
                                leave_type_id   NUMBER         NOT NULL,
                                year            NUMBER(4)      NOT NULL,
                                total_days      NUMBER(4,1)    NOT NULL,
                                used_days       NUMBER(4,1)    DEFAULT 0 NOT NULL,
                                remaining_days  NUMBER(4,1)    GENERATED ALWAYS AS (total_days - used_days) VIRTUAL,
                                CONSTRAINT fk_lb_emp      FOREIGN KEY (employee_id)   REFERENCES EMPLOYEES(employee_id),
                                CONSTRAINT fk_lb_type     FOREIGN KEY (leave_type_id) REFERENCES HR_PARAMETERS(param_id),
                                CONSTRAINT uk_lb_emp_type_year UNIQUE (employee_id, leave_type_id, year)
);

-- ─── DOCUMENT_REQUESTS ──────────────────────────────────────
CREATE TABLE DOCUMENT_REQUESTS (
                                   request_id    NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   employee_id   NUMBER         NOT NULL,
                                   doc_type_id   NUMBER         NOT NULL,         -- FK vers HR_PARAMETERS
                                   reason        VARCHAR2(500),
                                   copies_count  NUMBER(2)      DEFAULT 1,
                                   language      VARCHAR2(20)   DEFAULT 'FR',
                                   status        VARCHAR2(20)   DEFAULT 'EN_ATTENTE' NOT NULL
     CONSTRAINT ck_doc_status
     CHECK (status IN ('EN_ATTENTE','EN_COURS','PRET','LIVRE','REFUSE')),
                                   processed_by  NUMBER,
                                   document_url  VARCHAR2(500),
                                   processed_at  TIMESTAMP,
                                   created_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                   CONSTRAINT fk_doc_emp       FOREIGN KEY (employee_id)  REFERENCES EMPLOYEES(employee_id),
                                   CONSTRAINT fk_doc_type      FOREIGN KEY (doc_type_id)  REFERENCES HR_PARAMETERS(param_id),
                                   CONSTRAINT fk_doc_processor FOREIGN KEY (processed_by) REFERENCES EMPLOYEES(employee_id)
);

-- ─── LOAN_REQUESTS ──────────────────────────────────────────
CREATE TABLE LOAN_REQUESTS (
                               request_id       NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               employee_id      NUMBER         NOT NULL,
                               amount           NUMBER(12,2)   NOT NULL,
                               currency         VARCHAR2(10)   DEFAULT 'TND' NOT NULL,
                               duration_months  NUMBER(3)      NOT NULL,
                               monthly_payment  NUMBER(10,2),
                               reason           VARCHAR2(500),
                               status           VARCHAR2(20)   DEFAULT 'EN_ATTENTE' NOT NULL
        CONSTRAINT ck_loan_status
        CHECK (status IN ('EN_ATTENTE','EN_ETUDE','APPROUVE','REFUSE','REMBOURSE')),
                               approved_by      NUMBER,
                               approved_at      TIMESTAMP,
                               rejection_reason VARCHAR2(500),
                               created_at       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                               CONSTRAINT fk_loan_emp      FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id),
                               CONSTRAINT fk_loan_approver FOREIGN KEY (approved_by) REFERENCES EMPLOYEES(employee_id)
);

-- ─── AUTHORIZATION_REQUESTS ─────────────────────────────────
CREATE TABLE AUTHORIZATION_REQUESTS (
                                        request_id      NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                        employee_id     NUMBER         NOT NULL,
                                        start_datetime  TIMESTAMP      NOT NULL,
                                        end_datetime    TIMESTAMP      NOT NULL,
                                        duration_hours  NUMBER(4,2),
                                        reason          VARCHAR2(500)  NOT NULL,
                                        status          VARCHAR2(20)   DEFAULT 'EN_ATTENTE' NOT NULL
       CONSTRAINT ck_auth_status
       CHECK (status IN ('EN_ATTENTE','APPROUVE','REFUSE')),
                                        approved_by     NUMBER,
                                        approved_at     TIMESTAMP,
                                        created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                        CONSTRAINT fk_auth_emp      FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id),
                                        CONSTRAINT fk_auth_approver FOREIGN KEY (approved_by) REFERENCES EMPLOYEES(employee_id)
);

-- ─── TRAINING_SESSIONS ──────────────────────────────────────
CREATE TABLE TRAINING_SESSIONS (
                                   session_id       NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   title            VARCHAR2(200)  NOT NULL,
                                   provider         VARCHAR2(150),
                                   description      CLOB,
                                   start_date       DATE           NOT NULL,
                                   end_date         DATE           NOT NULL,
                                   location         VARCHAR2(200),
                                   training_mode    VARCHAR2(30)   DEFAULT 'PRESENTIEL' -- Corrigé : mode -> training_mode
     CONSTRAINT ck_train_mode
     CHECK (training_mode IN ('PRESENTIEL','DISTANCIEL','HYBRIDE')),
                                   max_participants NUMBER(3),
                                   cost             NUMBER(10,2),
                                   status           VARCHAR2(20)   DEFAULT 'PLANIFIE' NOT NULL
     CONSTRAINT ck_sess_status
     CHECK (status IN ('PLANIFIE','EN_COURS','TERMINE','ANNULE'))
);

-- ─── TRAINING_REQUESTS ──────────────────────────────────────
CREATE TABLE TRAINING_REQUESTS (
                                   request_id      NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   employee_id     NUMBER         NOT NULL,
                                   training_title  VARCHAR2(200)  NOT NULL,
                                   provider        VARCHAR2(150),
                                   estimated_cost  NUMBER(10,2),
                                   planned_date    DATE,
                                   duration_days   NUMBER(3),
                                   reason          VARCHAR2(500),
                                   status          VARCHAR2(20)   DEFAULT 'EN_ATTENTE' NOT NULL
       CONSTRAINT ck_train_req_status
       CHECK (status IN ('EN_ATTENTE','APPROUVE_CHEF',
                         'APPROUVE_RH','REFUSE','ANNULE')),
                                   approved_by     NUMBER,
                                   created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                   CONSTRAINT fk_train_req_emp      FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id),
                                   CONSTRAINT fk_train_req_approver FOREIGN KEY (approved_by) REFERENCES EMPLOYEES(employee_id)
);

-- ─── TRAINING_ENROLLMENTS ───────────────────────────────────
CREATE TABLE TRAINING_ENROLLMENTS (
                                      enrollment_id   NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      session_id      NUMBER         NOT NULL,
                                      employee_id     NUMBER         NOT NULL,
                                      status          VARCHAR2(20)   DEFAULT 'INSCRIT' NOT NULL
       CONSTRAINT ck_enroll_status
       CHECK (status IN ('INSCRIT','PRESENT','ABSENT','ABANDONNE')),
                                      score           NUMBER(5,2),
                                      result          VARCHAR2(30),
                                      certificate_url VARCHAR2(500),
                                      enrolled_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                      CONSTRAINT fk_enroll_session FOREIGN KEY (session_id)  REFERENCES TRAINING_SESSIONS(session_id),
                                      CONSTRAINT fk_enroll_emp     FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id),
                                      CONSTRAINT uk_enroll_sess_emp UNIQUE (session_id, employee_id)
);

-- ─── INDEX ──────────────────────────────────────────────────
CREATE INDEX idx_leave_emp_status ON LEAVE_REQUESTS(employee_id, status);
CREATE INDEX idx_leave_dates      ON LEAVE_REQUESTS(start_date, end_date);
CREATE INDEX idx_lb_emp_year      ON LEAVE_BALANCES(employee_id, year);
CREATE INDEX idx_doc_emp_status   ON DOCUMENT_REQUESTS(employee_id, status);
CREATE INDEX idx_loan_emp_status  ON LOAN_REQUESTS(employee_id, status);
CREATE INDEX idx_train_req_emp    ON TRAINING_REQUESTS(employee_id, status);
CREATE INDEX idx_enroll_session   ON TRAINING_ENROLLMENTS(session_id);