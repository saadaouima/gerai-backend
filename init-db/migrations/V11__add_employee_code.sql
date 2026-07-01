-- ═══════════════════════════════════════════════════════════════════
--  V11__add_employee_code.sql
--  Adds the EMPLOYEE_CODE column that was missing from the live table
--  (Flyway had already applied V1 before the column was added to it).
-- ═══════════════════════════════════════════════════════════════════

-- 1. Add the column as nullable first (existing rows can't satisfy NOT NULL yet)
ALTER TABLE EMPLOYEES ADD (employee_code VARCHAR2(30));

-- 2. Back-fill existing rows with generated codes
DECLARE
    v_counter NUMBER := 1;
BEGIN
    FOR r IN (SELECT employee_id FROM EMPLOYEES ORDER BY employee_id) LOOP
        UPDATE EMPLOYEES
        SET    employee_code = 'EMP-' || LPAD(v_counter, 4, '0')
        WHERE  employee_id   = r.employee_id;
        v_counter := v_counter + 1;
    END LOOP;
    COMMIT;
END;
/

-- 3. Enforce NOT NULL and UNIQUE once every row has a code
ALTER TABLE EMPLOYEES MODIFY (employee_code VARCHAR2(30) NOT NULL);
ALTER TABLE EMPLOYEES ADD CONSTRAINT uk_emp_code UNIQUE (employee_code);
