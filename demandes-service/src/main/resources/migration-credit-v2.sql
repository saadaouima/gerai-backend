-- =====================================================================
-- Migration v2 : Compléter le schéma LOAN_REQUESTS pour le workflow DG
-- À exécuter sur Oracle AVANT de redémarrer le demandes-service
-- Exécuter APRÈS migration-credit.sql
-- =====================================================================

-- 1. Colonnes manquantes pour la décision du comité et du DG
ALTER TABLE GERAI.LOAN_REQUESTS ADD DG_APPROVED_BY_NAME  VARCHAR2(200);
ALTER TABLE GERAI.LOAN_REQUESTS ADD APPROVED_BY_RH       NUMBER;
ALTER TABLE GERAI.LOAN_REQUESTS ADD APPROVED_AT_RH       TIMESTAMP;
ALTER TABLE GERAI.LOAN_REQUESTS ADD APPROVED_BY_RH_NAME  VARCHAR2(200);
ALTER TABLE GERAI.LOAN_REQUESTS ADD FINAL_APPROVED_BY    NUMBER;
ALTER TABLE GERAI.LOAN_REQUESTS ADD FINAL_APPROVED_AT    TIMESTAMP;
ALTER TABLE GERAI.LOAN_REQUESTS ADD FINAL_APPROVED_BY_NAME VARCHAR2(200);
ALTER TABLE GERAI.LOAN_REQUESTS ADD NEEDS_COMMISSION     NUMBER(1) DEFAULT 1 NOT NULL;

-- 2. Supprimer l'ancienne contrainte CHECK (ck_loan_status) qui ne contient pas VALIDEE_DG
--    Remplacer SYS_Cxxx par le nom réel si différent (vérifier avec la requête ci-dessous)
--    SELECT CONSTRAINT_NAME FROM USER_CONSTRAINTS WHERE TABLE_NAME = 'LOAN_REQUESTS' AND CONSTRAINT_TYPE = 'C';
ALTER TABLE GERAI.LOAN_REQUESTS DROP CONSTRAINT ck_loan_status;

-- 3. Supprimer aussi la contrainte ajoutée par migration-credit.sql si elle existe
--    (elle n'inclut pas VALIDEE_DG non plus)
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE GERAI.LOAN_REQUESTS DROP CONSTRAINT chk_loan_status';
EXCEPTION
  WHEN OTHERS THEN NULL; -- ignore si elle n'existe pas
END;
/

-- 4. Recréer une contrainte CHECK complète incluant tous les statuts valides
ALTER TABLE GERAI.LOAN_REQUESTS ADD CONSTRAINT chk_loan_status_v2
  CHECK (STATUS IN (
    'EN_ATTENTE',
    'EN_ETUDE',
    'EN_ETUDE_DG',
    'VALIDEE_DG',
    'APPROUVE',
    'REFUSE',
    'REMBOURSE',
    'ANNULE'
  ));

COMMIT;
