package com.gerai_backend.gerai.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;

import org.springframework.core.annotation.Order;

/**
 * Migration de schéma Oracle exécutée au démarrage pour ajouter la colonne
 * {@code EMPLOYEE_CODE} à la table {@code EMPLOYEES} si elle est absente.
 *
 * <p>@Component : enregistré comme bean Spring et exécuté en tant que {@link ApplicationRunner}.</p>
 * <p>@Order(1) : s'exécute en premier, avant {@link SchemaCompletionMigrator} (@Order 2)
 * et {@link DevDataSeeder} (@Order 3).</p>
 *
 * <p>Séquence d'opérations :</p>
 * <ol>
 *   <li>Ajoute la colonne {@code EMPLOYEE_CODE VARCHAR2(30)} comme nullable</li>
 *   <li>Remplit les lignes existantes avec des codes {@code EMP-XXXX} séquentiels</li>
 *   <li>Ajoute les contraintes {@code NOT NULL} et {@code UNIQUE} après le remplissage</li>
 * </ol>
 *
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class EmployeeCodeColumnMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    /**
     * Exécute la migration au démarrage de l'application.
     * Si la colonne {@code EMPLOYEE_CODE} existe déjà, la migration est ignorée (idempotente).
     *
     * @param args arguments de démarrage Spring Boot (non utilisés)
     * @throws Exception si la migration échoue après rollback de transaction
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {

            // Oracle stores names in UPPERCASE
            try (ResultSet rs = conn.getMetaData()
                    .getColumns(null, null, "EMPLOYEES", "EMPLOYEE_CODE")) {
                if (rs.next()) {
                    log.info("EMPLOYEE_CODE column already exists — skipping migration.");
                    return;
                }
            }

            log.info("EMPLOYEE_CODE column missing — running one-time migration...");
            conn.setAutoCommit(false);
            try {
                try (Statement stmt = conn.createStatement()) {
                    // 1. Add column as nullable (Oracle cannot add NOT NULL to a non-empty table directly)
                    stmt.execute("ALTER TABLE EMPLOYEES ADD (employee_code VARCHAR2(30))");
                    log.info("Column added (nullable).");
                }

                // 2. Back-fill existing rows with generated codes
                try (Statement q = conn.createStatement();
                     ResultSet rows = q.executeQuery(
                             "SELECT employee_id FROM EMPLOYEES ORDER BY employee_id");
                     PreparedStatement upd = conn.prepareStatement(
                             "UPDATE EMPLOYEES SET employee_code = ? WHERE employee_id = ?")) {

                    int counter = 1;
                    while (rows.next()) {
                        upd.setString(1, String.format("EMP-%04d", counter++));
                        upd.setLong(2, rows.getLong(1));
                        upd.addBatch();
                    }
                    int[] counts = upd.executeBatch();
                    log.info("Back-filled {} existing rows.", counts.length);
                }

                conn.commit();

                // 3. Enforce NOT NULL and UNIQUE now that every row has a value
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE EMPLOYEES MODIFY (employee_code NOT NULL)");
                    stmt.execute(
                            "ALTER TABLE EMPLOYEES ADD CONSTRAINT uk_emp_code UNIQUE (employee_code)");
                }

                log.info("EMPLOYEE_CODE migration complete.");

            } catch (Exception ex) {
                conn.rollback();
                log.error("EMPLOYEE_CODE migration failed — rolled back: {}", ex.getMessage());
                throw ex;
            }
        }
    }
}
