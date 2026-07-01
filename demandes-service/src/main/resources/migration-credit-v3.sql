-- Migration v3 : ajout du statut REFUSE_COMMISSION pour distinguer le refus du comité du refus DG
-- À exécuter en tant que gerai sur FREEPDB1

-- Supprimer l'ancienne contrainte CHECK v2
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE GERAI.LOAN_REQUESTS DROP CONSTRAINT chk_loan_status_v2';
EXCEPTION WHEN OTHERS THEN
  IF SQLCODE != -2443 THEN RAISE; END IF;
END;
/

-- Créer la nouvelle contrainte incluant REFUSE_COMMISSION
ALTER TABLE GERAI.LOAN_REQUESTS ADD CONSTRAINT chk_loan_status_v3
  CHECK (STATUS IN (
    'EN_ATTENTE', 'EN_ETUDE', 'EN_ETUDE_DG',
    'VALIDEE_DG', 'APPROUVE', 'REFUSE', 'REFUSE_COMMISSION',
    'REMBOURSE', 'ANNULE'
  ));

COMMIT;
