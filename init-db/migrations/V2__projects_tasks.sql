-- ═══════════════════════════════════════════════════════════
--  GerAI — V2__projects_tasks.sql
--  Domaine : Projects & Tasks (espace Chef)
--  Tables  : PROJECTS, PROJECT_MEMBERS, TASKS,
--             TASK_COMMENTS, PERFORMANCE_EVALS
-- ═══════════════════════════════════════════════════════════

-- ─── PROJECTS ───────────────────────────────────────────────
CREATE TABLE PROJECTS (
                          project_id    NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          name          VARCHAR2(200)   NOT NULL,
                          description   CLOB,
                          code          VARCHAR2(30),
                          created_by    NUMBER          NOT NULL,
                          dept_id       NUMBER,
                          start_date    DATE,
                          end_date      DATE,
                          priority      VARCHAR2(20)    DEFAULT 'NORMALE'
                                  CONSTRAINT ck_proj_priority
                                  CHECK (priority IN ('FAIBLE','NORMALE','HAUTE','CRITIQUE')),
                          status        VARCHAR2(30)    DEFAULT 'EN_COURS' NOT NULL
                                  CONSTRAINT ck_proj_status
                                  CHECK (status IN ('PLANIFIE','EN_COURS','EN_PAUSE','TERMINE','ANNULE')),
                          progress_pct  NUMBER(3)       DEFAULT 0
                                  CONSTRAINT ck_proj_progress CHECK (progress_pct BETWEEN 0 AND 100),
                          created_at    TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
                          updated_at    TIMESTAMP,
                          CONSTRAINT fk_proj_creator FOREIGN KEY (created_by) REFERENCES EMPLOYEES(employee_id),
                          CONSTRAINT fk_proj_dept    FOREIGN KEY (dept_id)    REFERENCES DEPARTMENTS(dept_id)
);

-- ─── PROJECT_MEMBERS ────────────────────────────────────────
CREATE TABLE PROJECT_MEMBERS (
                                 member_id    NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 project_id   NUMBER         NOT NULL,
                                 employee_id  NUMBER         NOT NULL,
                                 role         VARCHAR2(50)   DEFAULT 'MEMBRE' NOT NULL
                                CONSTRAINT ck_pm_role
                                CHECK (role IN ('CHEF','LEAD','MEMBRE','OBSERVATEUR')),
                                 joined_at    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                 left_at      TIMESTAMP,
                                 is_active    NUMBER(1)      DEFAULT 1 NOT NULL
                                CONSTRAINT ck_pm_active CHECK (is_active IN (0,1)),
                                 CONSTRAINT fk_pm_project  FOREIGN KEY (project_id)  REFERENCES PROJECTS(project_id),
                                 CONSTRAINT fk_pm_employee FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id),
                                 CONSTRAINT uk_pm_proj_emp UNIQUE (project_id, employee_id)
);

-- ─── TASKS ──────────────────────────────────────────────────
CREATE TABLE TASKS (
                       task_id         NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       project_id      NUMBER         NOT NULL,
                       title           VARCHAR2(300)  NOT NULL,
                       description     CLOB,
                       assigned_to     NUMBER,
                       created_by      NUMBER         NOT NULL,
                       parent_task_id  NUMBER,                        -- sous-tâche
                       priority        VARCHAR2(20)   DEFAULT 'NORMALE'
                                   CONSTRAINT ck_task_priority
                                   CHECK (priority IN ('FAIBLE','NORMALE','HAUTE','CRITIQUE')),
                       status          VARCHAR2(30)   DEFAULT 'A_FAIRE' NOT NULL
                                   CONSTRAINT ck_task_status
                                   CHECK (status IN ('A_FAIRE','EN_COURS','EN_REVUE','TERMINE','BLOQUE')),
                       progress_pct    NUMBER(3)      DEFAULT 0
                                   CONSTRAINT ck_task_progress CHECK (progress_pct BETWEEN 0 AND 100),
                       due_date        DATE,
                       estimated_hours NUMBER(6,2),
                       actual_hours    NUMBER(6,2),
                       created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                       updated_at      TIMESTAMP,
                       CONSTRAINT fk_task_project  FOREIGN KEY (project_id)     REFERENCES PROJECTS(project_id),
                       CONSTRAINT fk_task_assigned FOREIGN KEY (assigned_to)    REFERENCES EMPLOYEES(employee_id),
                       CONSTRAINT fk_task_creator  FOREIGN KEY (created_by)     REFERENCES EMPLOYEES(employee_id),
                       CONSTRAINT fk_task_parent   FOREIGN KEY (parent_task_id) REFERENCES TASKS(task_id)
);

-- ─── TASK_COMMENTS ──────────────────────────────────────────
CREATE TABLE TASK_COMMENTS (
                               comment_id   NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               task_id      NUMBER         NOT NULL,
                               employee_id  NUMBER         NOT NULL,
                               content      CLOB           NOT NULL,
                               created_at   TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                               CONSTRAINT fk_tc_task FOREIGN KEY (task_id)     REFERENCES TASKS(task_id),
                               CONSTRAINT fk_tc_emp  FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id)
);

-- ─── PERFORMANCE_EVALS ──────────────────────────────────────
CREATE TABLE PERFORMANCE_EVALS (
                                   eval_id        NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   employee_id    NUMBER         NOT NULL,
                                   evaluator_id   NUMBER         NOT NULL,
                                   period_year    NUMBER(4)      NOT NULL,
                                   period_quarter VARCHAR2(5),
                                   score          NUMBER(4,2),
                                   criteria_scores CLOB,                          -- JSON
                                   strengths      CLOB,
                                   improvements   CLOB,
                                   comments       CLOB,
                                   status         VARCHAR2(20)   DEFAULT 'BROUILLON' NOT NULL
                                  CONSTRAINT ck_eval_status
                                  CHECK (status IN ('BROUILLON','SOUMIS','VALIDE')),
                                   created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                   CONSTRAINT fk_eval_emp       FOREIGN KEY (employee_id)  REFERENCES EMPLOYEES(employee_id),
                                   CONSTRAINT fk_eval_evaluator FOREIGN KEY (evaluator_id) REFERENCES EMPLOYEES(employee_id)
);

-- ─── INDEX ──────────────────────────────────────────────────
CREATE INDEX idx_tasks_project   ON TASKS(project_id);
CREATE INDEX idx_tasks_assigned  ON TASKS(assigned_to);
CREATE INDEX idx_tasks_status    ON TASKS(status);
CREATE INDEX idx_pm_project      ON PROJECT_MEMBERS(project_id);
CREATE INDEX idx_pm_employee     ON PROJECT_MEMBERS(employee_id);
CREATE INDEX idx_eval_emp_year   ON PERFORMANCE_EVALS(employee_id, period_year);