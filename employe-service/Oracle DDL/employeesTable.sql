CREATE TABLE employees (
    first_name       VARCHAR2(50 BYTE)   NOT NULL,
    last_name        VARCHAR2(50 BYTE)   NOT NULL,
    email            VARCHAR2(100 BYTE)  NOT NULL,
    hire_date        DATE                DEFAULT SYSDATE,
    job_title        VARCHAR2(100 BYTE),
    salary           NUMBER(10, 2),
    id               VARCHAR2(36 BYTE)   NOT NULL,
    keycloak_user_id VARCHAR2(36 BYTE),

    CONSTRAINT pk_employees       PRIMARY KEY (id),
    CONSTRAINT uq_employees_email UNIQUE (email),
    CONSTRAINT uq_employees_kc_id UNIQUE (keycloak_user_id)
);