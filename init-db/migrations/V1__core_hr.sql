-- ═══════════════════════════════════════════════════════════
--  GerAI — V1__core_hr.sql
--  Domaine : Core HR
--  Tables  : DEPARTMENTS, POSITIONS, EMPLOYEES,
--             CONTRACTS, EMPLOYEE_SOCIAL, HR_PARAMETERS
-- ═══════════════════════════════════════════════════════════

-- ─── DEPARTMENTS ────────────────────────────────────────────
CREATE TABLE DEPARTMENTS (
                             dept_id        NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             name           VARCHAR2(100)  NOT NULL,
                             code           VARCHAR2(20)   NOT NULL CONSTRAINT uk_dept_code UNIQUE,
                             manager_id     NUMBER,                          -- FK vers EMPLOYEES (ajoutée via ALTER plus bas)
                             parent_dept_id NUMBER,                         -- auto-référence
                             description    VARCHAR2(500),
                             is_active      NUMBER(1)      DEFAULT 1 NOT NULL
        CONSTRAINT ck_dept_active CHECK (is_active IN (0,1)),
                             created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
);

-- ─── POSITIONS ──────────────────────────────────────────────
CREATE TABLE POSITIONS (
                           position_id   NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           title         VARCHAR2(150)  NOT NULL,
                           code          VARCHAR2(30)   NOT NULL CONSTRAINT uk_pos_code UNIQUE,
                           dept_id       NUMBER         NOT NULL,
                           grade         VARCHAR2(20),
                           pos_level     VARCHAR2(30),                    -- CHANGÉ: 'level' remplacé par 'pos_level' (mot réservé Oracle)
                           min_salary    NUMBER(12,2),
                           max_salary    NUMBER(12,2),
                           is_active     NUMBER(1)      DEFAULT 1 NOT NULL
        CONSTRAINT ck_pos_active CHECK (is_active IN (0,1)),
                           CONSTRAINT fk_pos_dept FOREIGN KEY (dept_id) REFERENCES DEPARTMENTS(dept_id)
);

-- ─── EMPLOYEES ──────────────────────────────────────────────
CREATE TABLE EMPLOYEES (
                           employee_id    NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           user_id        VARCHAR2(255)  NOT NULL CONSTRAINT uk_emp_user_id UNIQUE, -- Keycloak UUID
                           employee_code  VARCHAR2(30)   NOT NULL CONSTRAINT uk_emp_code UNIQUE,
                           first_name     VARCHAR2(100)  NOT NULL,
                           last_name      VARCHAR2(100)  NOT NULL,
                           email          VARCHAR2(255)  NOT NULL CONSTRAINT uk_emp_email UNIQUE,
                           phone          VARCHAR2(30),
                           birth_date     DATE,
                           national_id    VARCHAR2(50)   CONSTRAINT uk_emp_national_id UNIQUE,
                           gender         VARCHAR2(10)   CONSTRAINT ck_emp_gender CHECK (gender IN ('M','F','AUTRE')),
                           address        VARCHAR2(500),
                           photo_url      VARCHAR2(500),
                           dept_id        NUMBER         NOT NULL,
                           position_id    NUMBER         NOT NULL,
                           manager_id     NUMBER,                         -- auto-référence
                           hire_date      DATE           NOT NULL,
                           status         VARCHAR2(20)   DEFAULT 'ACTIF' NOT NULL
        CONSTRAINT ck_emp_status CHECK (status IN ('ACTIF','INACTIF','SUSPENDU','DEMISSION')),
                           created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                           updated_at     TIMESTAMP,
                           CONSTRAINT fk_emp_dept     FOREIGN KEY (dept_id)     REFERENCES DEPARTMENTS(dept_id),
                           CONSTRAINT fk_emp_position FOREIGN KEY (position_id) REFERENCES POSITIONS(position_id),
                           CONSTRAINT fk_emp_manager  FOREIGN KEY (manager_id)  REFERENCES EMPLOYEES(employee_id)
);

-- ─── CONTRAINTES CIRCULAIRES DEPARTMENTS ─────────────────────
ALTER TABLE DEPARTMENTS
    ADD CONSTRAINT fk_dept_manager
        FOREIGN KEY (manager_id) REFERENCES EMPLOYEES(employee_id);

ALTER TABLE DEPARTMENTS
    ADD CONSTRAINT fk_dept_parent
        FOREIGN KEY (parent_dept_id) REFERENCES DEPARTMENTS(dept_id);

-- ─── CONTRACTS ──────────────────────────────────────────────
CREATE TABLE CONTRACTS (
                           contract_id   NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           employee_id   NUMBER         NOT NULL,
                           type          VARCHAR2(50)   NOT NULL
        CONSTRAINT ck_contract_type CHECK (type IN ('CDI','CDD','STAGE','FREELANCE','INTERIM')),
                           start_date    DATE           NOT NULL,
                           end_date      DATE,
                           gross_salary  NUMBER(12,2)   NOT NULL,
                           net_salary    NUMBER(12,2),
                           currency      VARCHAR2(10)   DEFAULT 'TND' NOT NULL,
                           document_url  VARCHAR2(500),
                           status        VARCHAR2(20)   DEFAULT 'ACTIF' NOT NULL
        CONSTRAINT ck_contract_status CHECK (status IN ('ACTIF','EXPIRE','RESILIE')),
                           created_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                           CONSTRAINT fk_contract_emp FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id)
);

-- ─── EMPLOYEE_SOCIAL ────────────────────────────────────────
CREATE TABLE EMPLOYEE_SOCIAL (
                                 social_id           NUMBER        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 employee_id         NUMBER        NOT NULL CONSTRAINT uk_social_emp UNIQUE,
                                 marital_status      VARCHAR2(30)
        CONSTRAINT ck_marital CHECK (marital_status IN ('CELIBATAIRE','MARIE','DIVORCE','VEUF')),
                                 children_count      NUMBER(2)     DEFAULT 0,
                                 dependents_count    NUMBER(2)     DEFAULT 0,
                                 social_security_num VARCHAR2(50)  CONSTRAINT uk_social_sec UNIQUE,
                                 bank_name           VARCHAR2(100),
                                 bank_account        VARCHAR2(50),
                                 bank_rib            VARCHAR2(30),
                                 emergency_contact   VARCHAR2(150),
                                 emergency_phone     VARCHAR2(30),
                                 CONSTRAINT fk_social_emp FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id)
);

-- ─── HR_PARAMETERS ──────────────────────────────────────────
CREATE TABLE HR_PARAMETERS (
                               param_id    NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               category    VARCHAR2(50)   NOT NULL,
                               code        VARCHAR2(50)   NOT NULL CONSTRAINT uk_param_code UNIQUE,
                               label       VARCHAR2(150)  NOT NULL,
                               value       VARCHAR2(500),
                               is_active   NUMBER(1)      DEFAULT 1 NOT NULL
        CONSTRAINT ck_param_active CHECK (is_active IN (0,1)),
                               sort_order  NUMBER(3)      DEFAULT 0,
                               created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
);

-- ─── INDEX ──────────────────────────────────────────────────
CREATE INDEX idx_emp_dept     ON EMPLOYEES(dept_id);
CREATE INDEX idx_emp_manager  ON EMPLOYEES(manager_id);
CREATE INDEX idx_emp_status   ON EMPLOYEES(status);
--CREATE INDEX idx_emp_user_id  ON EMPLOYEES(user_id);
CREATE INDEX idx_contract_emp ON CONTRACTS(employee_id);
CREATE INDEX idx_pos_dept     ON POSITIONS(dept_id);