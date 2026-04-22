-- ═══════════════════════════════════════════════════════════
--  GerAI — scripts/reset.sql
--  Drop toutes les tables dans l'ordre inverse des FK,
--  puis relance Flyway pour tout recréer proprement.
--
--  Usage SQL Developer / SQLPlus :
--    @reset.sql
-- ═══════════════════════════════════════════════════════════

-- Désactiver les contraintes FK le temps du drop
BEGIN
FOR c IN (SELECT constraint_name, table_name
              FROM user_constraints
              WHERE constraint_type = 'R') LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE ' || c.table_name ||
                          ' DISABLE CONSTRAINT ' || c.constraint_name;
END LOOP;
END;
/

-- Drop tables (ordre : dépendantes d'abord)
BEGIN
FOR t IN (SELECT table_name FROM user_tables ORDER BY table_name) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
END LOOP;
END;
/

-- Drop séquences
BEGIN
FOR s IN (SELECT sequence_name FROM user_sequences) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
END LOOP;
END;
/

-- Drop la table de versioning Flyway pour forcer une réinitialisation complète
BEGIN
EXECUTE IMMEDIATE 'DROP TABLE flyway_schema_history CASCADE CONSTRAINTS PURGE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

COMMIT;
