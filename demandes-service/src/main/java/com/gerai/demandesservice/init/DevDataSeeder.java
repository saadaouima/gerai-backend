package com.gerai.demandesservice.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

/**
 * Initialiseur de données de test pour le profil {@code dev} du demandes-service.
 * <p>
 * Insère des demandes de congé (LEAVE_REQUESTS) et des demandes de formation
 * (TRAINING_REQUESTS) représentatives pour les 8 employés de référence créés
 * par l'employe-service.
 * <p>
 * {@code @Component} : enregistre cette classe comme bean Spring.
 * <p>
 * {@code @Profile("dev")} : ce bean n'est instancié que lorsque le profil Spring
 * {@code dev} est actif, ce qui garantit qu'il ne s'exécute jamais en production.
 * <p>
 * L'opération est idempotente : si des demandes existent déjà pour l'employé de
 * référence {@code alice.martin.seed@synapse.ma}, l'exécution est ignorée.
 * <p>
 * Prérequis : l'employe-service doit être démarré avant ce service afin que les
 * employés de référence soient présents dans la table EMPLOYEES.
 *
 * @since 1.0
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    /** Source de données JDBC Oracle injectée par Spring Boot. */
    private final DataSource dataSource;

    /**
     * Point d'entrée Spring Boot — exécuté au démarrage de l'application.
     * Résout les identifiants des employés et des types de congé, puis insère les données de test.
     *
     * @param args arguments de démarrage Spring Boot (non utilisés)
     * @throws Exception si la connexion JDBC échoue au niveau de l'acquisition initiale
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long idAlice   = empId(conn, "alice.martin.seed@synapse.ma");
                long idCharles = empId(conn, "charles.dupont.seed@synapse.ma");
                long idEmma    = empId(conn, "emma.morin.seed@synapse.ma");
                long idDiana   = empId(conn, "diana.laurent.seed@synapse.ma");
                long idFarid   = empId(conn, "farid.hamdi.seed@synapse.ma");
                long idGhita   = empId(conn, "ghita.lahlou.seed@synapse.ma");
                long idHassan  = empId(conn, "hassan.idrissi.seed@synapse.ma");
                long idInes    = empId(conn, "ines.karimi.seed@synapse.ma");

                if (idAlice == -1) {
                    log.warn("[DevDataSeeder] Seed employees not found — start employe-service first.");
                    conn.rollback();
                    return;
                }

                if (alreadySeeded(conn, idAlice)) {
                    log.info("[DevDataSeeder] Already seeded — skipping.");
                    return;
                }

                // Resolve leave type IDs
                long typeAnnuel   = leaveTypeId(conn, "ANNUEL");
                long typeMaladie  = leaveTypeId(conn, "MALADIE");
                long typeFormation= leaveTypeId(conn, "FORMATION");

                // ── LEAVE_REQUESTS ────────────────────────────────────────────
                // (employeeId, leaveTypeId, startDate, endDate, daysCount, reason, status, approvedBy)
                Object[][] leaves = {
                    // Charles — EN_ATTENTE (future congé annuel)
                    {idCharles, typeAnnuel,  future(10), future(19), "9.0",
                     "Congé annuel planifié - vacances d'été", "EN_ATTENTE", null, null},
                    // Emma — VALIDE_CHEF (chef validated, waiting RH)
                    {idEmma, typeAnnuel,     future(5),  future(9),  "4.0",
                     "Congé familial", "VALIDE_CHEF", idAlice, "Alice Martin"},
                    // Diana — VALIDE_RH (currently on leave)
                    {idDiana, typeAnnuel,    past(14),   future(1),  "15.0",
                     "Congé annuel", "VALIDE_RH", idAlice, "Alice Martin"},
                    // Farid — REFUSE (overloaded period)
                    {idFarid, typeAnnuel,    future(2),  future(11), "9.0",
                     "Congé annuel", "REFUSE", idAlice, "Alice Martin"},
                    // Ghita — ANNULE (self cancelled)
                    {idGhita, typeAnnuel,    future(20), future(29), "9.0",
                     "Congé annuel", "ANNULE", null, null},
                    // Hassan — EN_ATTENTE (sick leave, new employee)
                    {idHassan, typeMaladie,  past(2),    past(1),    "2.0",
                     "Indisposition médicale", "EN_ATTENTE", null, null},
                    // Inès — EN_ATTENTE (formation leave)
                    {idInes, typeFormation,  future(30), future(34), "4.0",
                     "Formation marketing digital", "EN_ATTENTE", null, null},
                    // Alice — historical approved leave
                    {idAlice, typeAnnuel,    past(60),   past(51),   "9.0",
                     "Congé annuel", "VALIDE_RH", idAlice, "Sara Benali"},
                };

                String leaveSql =
                    "INSERT INTO LEAVE_REQUESTS " +
                    "  (employee_id, leave_type_id, start_date, end_date, days_count, reason, " +
                    "   status, approved_by, approved_by_rh_name, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)";

                try (PreparedStatement ps = conn.prepareStatement(leaveSql)) {
                    for (Object[] l : leaves) {
                        ps.setLong(1,   (long)   l[0]);
                        ps.setLong(2,   (long)   l[1]);
                        ps.setDate(3, Date.valueOf((LocalDate) l[2]));
                        ps.setDate(4, Date.valueOf((LocalDate) l[3]));
                        ps.setBigDecimal(5, new BigDecimal((String) l[4]));
                        ps.setString(6, (String) l[5]);
                        ps.setString(7, (String) l[6]);
                        if (l[7] != null) ps.setLong(8, (long) l[7]);
                        else ps.setNull(8, Types.NUMERIC);
                        ps.setString(9, (String) l[8]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    log.info("[DevDataSeeder] Seeded {} leave requests.", leaves.length);
                }

                // ── TRAINING_REQUESTS ─────────────────────────────────────────
                // Scenarios: one pending, one approved, one refused
                Object[][] trainings = {
                    // Inès — EN_ATTENTE
                    {idInes,    "Marketing Digital & Analytics",
                     "Digital Academy Tunisie",  "2500.00", future(45), 5, "DISTANCIEL",
                     "Renforcer les compétences en marketing digital.", "EN_ATTENTE"},
                    // Charles — APPROUVE_CHEF (waiting RH)
                    {idCharles, "Leadership & Management d'équipe",
                     "HEC Executive",            "4800.00", future(30), 3, "PRESENTIEL",
                     "Préparation au rôle de chef de projet.", "APPROUVE_CHEF"},
                    // Hassan — REFUSE (budget insuffisant)
                    {idHassan,  "Formation Oracle Database Admin",
                     "Oracle University",        "6000.00", future(15), 5, "PRESENTIEL",
                     "Améliorer la gestion des bases de données.", "REFUSE"},
                };

                String trainSql =
                    "INSERT INTO TRAINING_REQUESTS " +
                    "  (employee_id, training_title, provider, estimated_cost, planned_date, " +
                    "   duration_days, mode_formation, reason, status, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)";

                try (PreparedStatement ps = conn.prepareStatement(trainSql)) {
                    for (Object[] t : trainings) {
                        ps.setLong(1,         (long)   t[0]);
                        ps.setString(2,       (String) t[1]);
                        ps.setString(3,       (String) t[2]);
                        ps.setBigDecimal(4, new BigDecimal((String) t[3]));
                        ps.setDate(5, Date.valueOf((LocalDate) t[4]));
                        ps.setInt(6,          (int)    t[5]);
                        ps.setString(7,       (String) t[6]);
                        ps.setString(8,       (String) t[7]);
                        ps.setString(9,       (String) t[8]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    log.info("[DevDataSeeder] Seeded {} training requests.", trainings.length);
                }

                conn.commit();
                log.info("[DevDataSeeder] Seed complete.");

            } catch (Exception ex) {
                conn.rollback();
                log.error("[DevDataSeeder] Failed — rolled back: {}", ex.getMessage(), ex);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Résout l'identifiant Oracle d'un employé depuis son adresse email.
     *
     * @param conn  connexion JDBC active
     * @param email adresse email de l'employé seed
     * @return l'identifiant Oracle de l'employé, ou {@code -1L} si aucun employé ne correspond
     * @throws SQLException si la requête SQL échoue
     */
    private long empId(Connection conn, String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT employee_id FROM EMPLOYEES WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    /**
     * Résout l'identifiant Oracle d'un type de congé depuis son code textuel.
     *
     * @param conn connexion JDBC active
     * @param code code du type de congé (ex : {@code ANNUEL}, {@code MALADIE})
     * @return l'identifiant Oracle du type de congé, ou {@code 1L} (ANNUEL) en cas d'absence
     * @throws SQLException si la requête SQL échoue
     */
    private long leaveTypeId(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT type_id FROM REF_TYPES_CONGE WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1L; // fallback to 1
            }
        }
    }

    /**
     * Vérifie si des données de seed existent déjà pour un employé donné.
     *
     * @param conn       connexion JDBC active
     * @param employeeId identifiant Oracle de l'employé de référence
     * @return {@code true} si au moins une demande de congé existe pour cet employé
     * @throws SQLException si la requête SQL échoue
     */
    private boolean alreadySeeded(Connection conn, long employeeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM LEAVE_REQUESTS WHERE employee_id = ?")) {
            ps.setLong(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Retourne une date dans le futur par rapport à aujourd'hui.
     *
     * @param days nombre de jours à ajouter à la date du jour
     * @return la date résultante
     */
    private LocalDate future(int days) { return LocalDate.now().plusDays(days); }

    /**
     * Retourne une date dans le passé par rapport à aujourd'hui.
     *
     * @param days nombre de jours à soustraire à la date du jour
     * @return la date résultante
     */
    private LocalDate past(int days)   { return LocalDate.now().minusDays(days); }
}
