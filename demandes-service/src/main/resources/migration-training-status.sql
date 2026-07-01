-- Migration: extend TRAINING_REQUESTS STATUS check constraint
-- Run this on Oracle BEFORE restarting the demandes-service.
-- The endpoints /planifier, /demarrer, /completer, /annuler set statuses
-- not in the original constraint, causing ORA-02290 violations.

-- Step 1: find and drop the existing check constraint on STATUS
-- (Oracle does not support ALTER TABLE ... MODIFY CONSTRAINT directly)
DECLARE
  v_constraint VARCHAR2(128);
BEGIN
  SELECT constraint_name
    INTO v_constraint
    FROM all_constraints
   WHERE owner           = 'GERAI'
     AND table_name      = 'TRAINING_REQUESTS'
     AND constraint_type = 'C'
     AND search_condition LIKE '%STATUS%'
     AND ROWNUM = 1;
  EXECUTE IMMEDIATE 'ALTER TABLE GERAI.TRAINING_REQUESTS DROP CONSTRAINT ' || v_constraint;
EXCEPTION
  WHEN NO_DATA_FOUND THEN NULL;  -- no constraint to drop, proceed
END;
/

-- Step 2: add the new constraint with the full lifecycle value set
ALTER TABLE GERAI.TRAINING_REQUESTS
  ADD CONSTRAINT chk_training_status
  CHECK (STATUS IN (
    'EN_ATTENTE',
    'APPROUVE_CHEF',
    'APPROUVE_RH',
    'PLANIFIEE',
    'EN_COURS',
    'COMPLETEE',
    'REFUSE',
    'REJETE_RH',
    'ANNULE'
  ));

COMMIT;
