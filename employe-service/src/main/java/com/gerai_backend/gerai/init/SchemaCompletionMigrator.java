package com.gerai_backend.gerai.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;

/**
 * Migration de schéma Oracle exécutée au démarrage pour compléter la structure des tables
 * {@code DEPARTMENTS}, {@code POSITIONS} et {@code EMPLOYEES}.
 *
 * <p>@Component : enregistré comme bean Spring et exécuté en tant que {@link ApplicationRunner}.</p>
 * <p>@Order(2) : s'exécute après {@link EmployeeCodeColumnMigrator} (@Order 1)
 * et avant {@link DevDataSeeder} (@Order 3).</p>
 *
 * <p>Opérations effectuées :</p>
 * <ul>
 *   <li>Crée les tables {@code DEPARTMENTS} et {@code POSITIONS} si elles sont absentes
 *       et les peuple avec des données de référence</li>
 *   <li>Ajoute les colonnes manquantes à {@code EMPLOYEES} ({@code DEPT_ID}, {@code POSITION_ID},
 *       {@code NATIONAL_ID}, {@code USER_ID}, {@code CREATED_AT}, {@code UPDATED_AT})</li>
 *   <li>Corrige la colonne legacy {@code ID VARCHAR2(36)} pour éviter les erreurs d'insertion</li>
 *   <li>Remplit les colonnes {@code dept_id}/{@code position_id} des employés existants</li>
 *   <li>Génère des numéros de téléphone tunisiens aléatoires pour les employés sans téléphone</li>
 * </ul>
 *
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class SchemaCompletionMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    /**
     * Exécute toutes les migrations de schéma au démarrage de l'application.
     * Toutes les opérations sont exécutées dans une transaction unique avec rollback en cas d'échec.
     *
     * @param args arguments de démarrage Spring Boot (non utilisés)
     * @throws Exception si la connexion JDBC échoue
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                createDepartmentsIfMissing(conn);
                createPositionsIfMissing(conn);
                addColumnIfMissing(conn, "EMPLOYEES", "DEPT_ID",     "NUMBER");
                addColumnIfMissing(conn, "EMPLOYEES", "POSITION_ID", "NUMBER");
                addColumnIfMissing(conn, "EMPLOYEES", "NATIONAL_ID", "VARCHAR2(50)");
                addColumnIfMissing(conn, "EMPLOYEES", "USER_ID",
                        "VARCHAR2(255) DEFAULT 'KC-UNKNOWN' NOT NULL");
                addColumnIfMissing(conn, "EMPLOYEES", "CREATED_AT",
                        "TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL");
                addColumnIfMissing(conn, "EMPLOYEES", "UPDATED_AT",  "TIMESTAMP");
                fixLegacyIdColumn(conn);
                backfillEmployeeDeptAndPosition(conn);
                backfillPhoneNumbers(conn);
                conn.commit();
                log.info("SchemaCompletionMigrator finished.");
            } catch (Exception ex) {
                conn.rollback();
                log.error("SchemaCompletionMigrator failed — rolled back: {}", ex.getMessage());
            }
        }
    }

    /**
     * Crée la table {@code DEPARTMENTS} avec ses données de référence si elle n'existe pas encore.
     * La table est peuplée avec 8 départements de base (DG, RH, IT, FIN, MKT, DES, DAT, QUA).
     *
     * @param conn la connexion JDBC active
     * @throws SQLException en cas d'erreur DDL Oracle
     */
    private void createDepartmentsIfMissing(Connection conn) throws SQLException {
        if (tableExists(conn, "DEPARTMENTS")) {
            log.info("DEPARTMENTS table already exists — skipping.");
            return;
        }
        log.info("Creating DEPARTMENTS table...");
        try (Statement s = conn.createStatement()) {
            s.execute(
                "CREATE TABLE DEPARTMENTS (" +
                "  dept_id     NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY," +
                "  name        VARCHAR2(100)  NOT NULL," +
                "  code        VARCHAR2(20)   NOT NULL," +
                "  description VARCHAR2(500)," +
                "  is_active   NUMBER(1)      DEFAULT 1 NOT NULL," +
                "  created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL," +
                "  CONSTRAINT uk_dept_code UNIQUE (code)" +
                ")"
            );
        }
        // Seed baseline departments
        String[] names = {"Direction Générale", "Ressources Humaines", "Informatique",
                          "Finance", "Marketing", "Design", "Data", "Qualité"};
        String[] codes = {"DG", "RH", "IT", "FIN", "MKT", "DES", "DAT", "QUA"};
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO DEPARTMENTS (name, code) VALUES (?, ?)")) {
            for (int i = 0; i < names.length; i++) {
                ps.setString(1, names[i]);
                ps.setString(2, codes[i]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        log.info("DEPARTMENTS created and seeded with {} rows.", names.length);
    }

    /**
     * Crée la table {@code POSITIONS} avec ses données de référence si elle n'existe pas encore.
     * La table est peuplée avec un poste par défaut par département.
     *
     * @param conn la connexion JDBC active
     * @throws SQLException en cas d'erreur DDL Oracle
     */
    private void createPositionsIfMissing(Connection conn) throws SQLException {
        if (tableExists(conn, "POSITIONS")) {
            log.info("POSITIONS table already exists — skipping.");
            return;
        }
        log.info("Creating POSITIONS table...");
        try (Statement s = conn.createStatement()) {
            s.execute(
                "CREATE TABLE POSITIONS (" +
                "  position_id NUMBER        GENERATED ALWAYS AS IDENTITY PRIMARY KEY," +
                "  title       VARCHAR2(150) NOT NULL," +
                "  code        VARCHAR2(30)  NOT NULL," +
                "  dept_id     NUMBER        NOT NULL," +
                "  grade       VARCHAR2(20)," +
                "  pos_level   VARCHAR2(30)," +
                "  is_active   NUMBER(1)     DEFAULT 1 NOT NULL," +
                "  CONSTRAINT uk_pos_code UNIQUE (code)" +
                ")"
            );
        }
        // Seed one default position per department
        String[][] positions = {
            {"Directeur Général",    "DG-001",  "DG",  "N5", "EXECUTIVE"},
            {"Responsable RH",       "RH-001",  "RH",  "N4", "MANAGER"},
            {"Développeur",          "IT-001",  "IT",  "N2", "JUNIOR"},
            {"Chef de Projet",       "IT-002",  "IT",  "N3", "SENIOR"},
            {"Analyste Financier",   "FIN-001", "FIN", "N3", "SENIOR"},
            {"Chargé Marketing",     "MKT-001", "MKT", "N2", "JUNIOR"},
            {"Designer UX",          "DES-001", "DES", "N2", "JUNIOR"},
            {"Data Analyst",         "DAT-001", "DAT", "N2", "JUNIOR"},
            {"Responsable Qualité",  "QUA-001", "QUA", "N3", "SENIOR"}
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO POSITIONS (title, code, dept_id, grade, pos_level) " +
                "VALUES (?, ?, (SELECT dept_id FROM DEPARTMENTS WHERE code = ?), ?, ?)")) {
            for (String[] p : positions) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setString(4, p[3]);
                ps.setString(5, p[4]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        log.info("POSITIONS created and seeded with {} rows.", positions.length);
    }

    /**
     * Remplit les colonnes {@code DEPT_ID} et {@code POSITION_ID} des employés existants
     * à partir d'une liste d'emails connus, puis assigne le département IT par défaut
     * aux employés dont les valeurs sont encore nulles.
     *
     * @param conn la connexion JDBC active
     * @throws SQLException en cas d'erreur SQL lors de la mise à jour
     */
    private void backfillEmployeeDeptAndPosition(Connection conn) throws SQLException {
        // Known employees from demo seed — update by email
        String[][] known = {
            {"imed.gerai@gmail.com",          "RH",  "RH-001"},
            {"mariemsaadaoui@gmail.com",       "IT",  "IT-001"},
            {"nourboussaidi009@gmail.com",     "IT",  "IT-002"},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE EMPLOYEES SET " +
                "  dept_id     = (SELECT dept_id     FROM DEPARTMENTS WHERE code = ?), " +
                "  position_id = (SELECT position_id FROM POSITIONS   WHERE code = ?) " +
                "WHERE email = ?")) {
            for (String[] row : known) {
                ps.setString(1, row[1]);
                ps.setString(2, row[2]);
                ps.setString(3, row[0]);
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            int updated = 0;
            for (int c : counts) if (c > 0) updated++;
            if (updated > 0) log.info("Backfilled dept/position for {} known employee(s).", updated);
        }
        // Any remaining employees with null dept_id → assign IT department + Développeur
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE EMPLOYEES SET " +
                "  dept_id     = (SELECT dept_id     FROM DEPARTMENTS WHERE code = 'IT'), " +
                "  position_id = (SELECT position_id FROM POSITIONS   WHERE code = 'IT-001') " +
                "WHERE dept_id IS NULL OR position_id IS NULL")) {
            int n = ps.executeUpdate();
            if (n > 0) log.info("Assigned default dept/position to {} employee(s) with null values.", n);
        }
    }

    /**
     * Corrige la colonne legacy {@code EMPLOYEES.ID VARCHAR2(36)} (ancienne PK UUID)
     * en lui ajoutant un DEFAULT {@code RAWTOHEX(SYS_GUID())} pour éviter les erreurs
     * lors des INSERTs (la vraie PK est désormais {@code EMPLOYEE_ID NUMBER IDENTITY}).
     *
     * @param conn la connexion JDBC active
     * @throws SQLException en cas d'erreur DDL Oracle non récupérable
     */
    private void fixLegacyIdColumn(Connection conn) throws SQLException {
        // The old schema had id VARCHAR2(36) PRIMARY KEY. Hibernate no longer fills it
        // (EMPLOYEE_ID IDENTITY is the real PK now). Set a DEFAULT so INSERTs don't fail.
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "EMPLOYEES", "ID")) {
            if (!rs.next()) return; // Legacy column gone, nothing to do
            String defaultVal = rs.getString("COLUMN_DEF");
            if (defaultVal != null && !defaultVal.isBlank()) {
                log.info("EMPLOYEES.ID already has a DEFAULT — skipping.");
                return;
            }
        }
        log.info("Adding DEFAULT to legacy EMPLOYEES.ID column...");
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE EMPLOYEES MODIFY (id DEFAULT RAWTOHEX(SYS_GUID()))");
            log.info("EMPLOYEES.ID DEFAULT set to RAWTOHEX(SYS_GUID()).");
        } catch (Exception ex) {
            log.warn("Could not set DEFAULT on EMPLOYEES.ID ({}), trying nullable...", ex.getMessage());
            // Fallback: find and drop the PK constraint, then make id nullable
            try (ResultSet pks = conn.getMetaData().getPrimaryKeys(null, null, "EMPLOYEES")) {
                while (pks.next()) {
                    String colName = pks.getString("COLUMN_NAME");
                    String pkName  = pks.getString("PK_NAME");
                    if ("ID".equalsIgnoreCase(colName) && pkName != null) {
                        try (Statement s2 = conn.createStatement()) {
                            s2.execute("ALTER TABLE EMPLOYEES DROP CONSTRAINT " + pkName);
                            s2.execute("ALTER TABLE EMPLOYEES MODIFY (id NULL)");
                            log.info("Dropped PK {} and made EMPLOYEES.ID nullable.", pkName);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Génère et assigne des numéros de téléphone tunisiens aléatoires (+216 + 8 chiffres)
     * aux employés dont la colonne {@code PHONE} est {@code NULL}.
     *
     * @param conn la connexion JDBC active
     * @throws SQLException en cas d'erreur SQL lors de la mise à jour
     */
    private void backfillPhoneNumbers(Connection conn) throws SQLException {
        // Assign random Tunisian phone numbers (+216 + 8 digits) to employees without one
        try (Statement s = conn.createStatement()) {
            int n = s.executeUpdate(
                "UPDATE EMPLOYEES SET phone = " +
                "  '+216' || TRIM(TO_CHAR(TRUNC(DBMS_RANDOM.VALUE(20000000, 99999999)))) " +
                "WHERE phone IS NULL"
            );
            if (n > 0) log.info("Assigned random Tunisian phone numbers to {} employee(s).", n);
        }
    }

    /**
     * Ajoute une colonne à une table Oracle si elle n'existe pas déjà.
     * Opération idempotente — sans effet si la colonne est déjà présente.
     *
     * @param conn   la connexion JDBC active
     * @param table  le nom de la table Oracle (en majuscules)
     * @param column le nom de la colonne à ajouter (en majuscules)
     * @param type   le type SQL complet de la colonne (ex. {@code VARCHAR2(255) NOT NULL})
     * @throws SQLException en cas d'erreur DDL Oracle
     */
    private void addColumnIfMissing(Connection conn, String table, String column, String type)
            throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) {
                log.info("{}.{} already exists — skipping.", table, column);
                return;
            }
        }
        log.info("Adding {}.{} ({})...", table, column, type);
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE " + table + " ADD (" + column + " " + type + ")");
        }
        log.info("{}.{} added.", table, column);
    }

    /**
     * Vérifie l'existence d'une table Oracle dans le schéma courant.
     *
     * @param conn      la connexion JDBC active
     * @param tableName le nom de la table à vérifier (en majuscules Oracle)
     * @return {@code true} si la table existe, {@code false} sinon
     * @throws SQLException en cas d'erreur d'accès aux métadonnées
     */
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
