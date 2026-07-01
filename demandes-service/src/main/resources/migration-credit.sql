-- =====================================================================
-- Migration : Workflow Crédit avec décision Directeur Général
-- À exécuter sur Oracle AVANT de redémarrer le demandes-service
-- =====================================================================

-- 1. Nouvelles colonnes sur LOAN_REQUESTS
ALTER TABLE GERAI.LOAN_REQUESTS ADD MONTANT_APPROUVE  NUMBER(12,2);
ALTER TABLE GERAI.LOAN_REQUESTS ADD NB_TRANCHES       NUMBER(3);
ALTER TABLE GERAI.LOAN_REQUESTS ADD MONTANT_TRANCHE   NUMBER(10,2);
ALTER TABLE GERAI.LOAN_REQUESTS ADD DG_APPROVED_BY    NUMBER;
ALTER TABLE GERAI.LOAN_REQUESTS ADD DG_DECISION_AT    TIMESTAMP;
ALTER TABLE GERAI.LOAN_REQUESTS ADD DG_COMMENT        VARCHAR2(500);

-- 2. Modifier la contrainte CHECK pour inclure EN_ETUDE_DG et ANNULE
--    Étape A : trouver le nom exact de la contrainte
--    SELECT CONSTRAINT_NAME FROM USER_CONSTRAINTS
--      WHERE TABLE_NAME = 'LOAN_REQUESTS' AND CONSTRAINT_TYPE = 'C';
--
--    Étape B : supprimer la contrainte existante (remplacer SYS_Cxxxx par le vrai nom)
-- ALTER TABLE GERAI.LOAN_REQUESTS DROP CONSTRAINT SYS_Cxxxx;
--
--    Étape C : recréer avec les nouvelles valeurs
ALTER TABLE GERAI.LOAN_REQUESTS ADD CONSTRAINT chk_loan_status
  CHECK (STATUS IN ('EN_ATTENTE','EN_ETUDE','EN_ETUDE_DG','APPROUVE','REFUSE','REMBOURSE','ANNULE'));

COMMIT;
