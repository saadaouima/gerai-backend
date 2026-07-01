package com.gerai.projetsservice.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;

/**
 * Initialiseur de données de développement pour les tables de {@code projets-service}.
 * <p>
 * Tables alimentées (dans l'ordre) :
 * {@code ADMIN_DEPARTEMENTS}, {@code CAMPAGNES_EVALUATION}, {@code PERFORMANCE_EVALS},
 * {@code PROJECTS}, {@code TASKS}, {@code PROJECT_MEMBERS}, {@code SOLDES_CONGES_ADMIN}.
 * </p>
 * <p>
 * Les identifiants des employés sont résolus en interrogeant la table {@code EMPLOYEES}
 * par email seed — {@code employe-service} doit être démarré en premier pour que les
 * lignes existent. L'initialisation est idempotente : elle est ignorée si
 * {@code ADMIN_DEPARTEMENTS} contient déjà des données.
 * </p>
 * <p>
 * {@code @Profile("dev")} : ce composant n'est actif qu'avec le profil Spring {@code dev}.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final DataSource dataSource;

    /**
     * Point d'entrée de l'initialisation, appelé par Spring Boot au démarrage.
     *
     * @param args arguments de démarrage (non utilisés)
     * @throws Exception en cas d'erreur SQL irrécupérable
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (alreadySeeded(conn)) {
                    log.info("[DevDataSeeder] Already seeded — skipping.");
                    return;
                }

                // Resolve employee IDs (employe-service must have started first)
                long idAlice   = empId(conn, "alice.martin.seed@synapse.ma");
                long idSara    = empId(conn, "sara.benali.seed@synapse.ma");
                long idCharles = empId(conn, "charles.dupont.seed@synapse.ma");
                long idEmma    = empId(conn, "emma.morin.seed@synapse.ma");
                long idFarid   = empId(conn, "farid.hamdi.seed@synapse.ma");
                long idDiana   = empId(conn, "diana.laurent.seed@synapse.ma");
                long idHassan  = empId(conn, "hassan.idrissi.seed@synapse.ma");
                long idGhita   = empId(conn, "ghita.lahlou.seed@synapse.ma");
                long idInes    = empId(conn, "ines.karimi.seed@synapse.ma");

                if (idAlice == -1) {
                    log.warn("[DevDataSeeder] Seed employees not found — start employe-service first.");
                    conn.rollback();
                    return;
                }

                long itDeptAdminId = seedAdminDepartements(conn);
                seedCampagnes(conn);
                seedPerformanceEvals(conn, idAlice, idCharles, idEmma, idDiana, idHassan, idGhita, idFarid);
                seedSoldesConges(conn);

                // Projects
                long projSynapse   = seedProject(conn, "Plateforme SYNAPSE",           "SYN-2024", idAlice,  "EN_COURS",  65, -180, 60);
                long projAnalytics = seedProject(conn, "Module Analytics RH",           "ANA-2024", idAlice,  "EN_RETARD", 30, -120, -10); // end date in past → retard
                long projSite      = seedProject(conn, "Refonte Site Institutionnel",   "SITE-2023", idSara,  "TERMINE",  100, -365, -30);
                long projMobile    = seedProject(conn, "Application Mobile Employés",   "MOB-2025", idCharles,"EN_COURS",  20, -30,  180);

                // Tasks for SYNAPSE (mix: on-time + overdue)
                seedTask(conn, projSynapse, "Conception architecture backend",      idAlice,   "TERMINEE",   -90,  "HAUTE");
                seedTask(conn, projSynapse, "Développement module authentification", idCharles, "TERMINEE",   -60,  "HAUTE");
                seedTask(conn, projSynapse, "Intégration Keycloak",                 idEmma,    "EN_COURS",    30,  "NORMALE");
                seedTask(conn, projSynapse, "Tests d'intégration",                  idCharles, "A_FAIRE",     20,  "NORMALE");
                seedTask(conn, projSynapse, "Livraison sprint 4",                   idEmma,    "EN_COURS",    -5,  "HAUTE");   // overdue
                seedTask(conn, projSynapse, "Documentation API REST",               idDiana,   "EN_COURS",   -15,  "BASSE");   // overdue

                // Tasks for Analytics (mostly overdue — reason it's EN_RETARD)
                seedTask(conn, projAnalytics, "Maquettes tableaux de bord",         idAlice,   "TERMINEE",   -100, "HAUTE");
                seedTask(conn, projAnalytics, "Pipeline de données ETL",            idFarid,   "EN_COURS",   -20,  "CRITIQUE"); // overdue
                seedTask(conn, projAnalytics, "Intégration graphiques Apache ECharts", idHassan,"A_FAIRE",   -8,   "HAUTE");   // overdue, new employee
                seedTask(conn, projAnalytics, "Rapport de performance mensuel",     idFarid,   "EN_COURS",   -30,  "HAUTE");   // overdue

                // Tasks for Site (all TERMINEE)
                seedTask(conn, projSite, "Audit SEO",                               idGhita,   "TERMINEE",  -300, "NORMALE");
                seedTask(conn, projSite, "Refonte charte graphique",                idInes,    "TERMINEE",  -280, "NORMALE");
                seedTask(conn, projSite, "Mise en ligne",                           idSara,    "TERMINEE",  -200, "HAUTE");

                // Tasks for Mobile (early stage, no retard)
                seedTask(conn, projMobile, "Cahier des charges",                    idCharles, "TERMINEE",   -25, "HAUTE");
                seedTask(conn, projMobile, "Design UI/UX",                          idInes,    "EN_COURS",    60, "NORMALE");

                // Project members
                long[] synapseMembers   = {idAlice, idCharles, idEmma, idDiana};
                long[] analyticsMembers = {idAlice, idFarid, idHassan};
                long[] siteMembers      = {idSara, idGhita, idInes};
                long[] mobileMembers    = {idCharles, idInes};

                seedMembers(conn, projSynapse,   idAlice,   synapseMembers);
                seedMembers(conn, projAnalytics, idAlice,   analyticsMembers);
                seedMembers(conn, projSite,      idSara,    siteMembers);
                seedMembers(conn, projMobile,    idCharles, mobileMembers);

                conn.commit();
                log.info("[DevDataSeeder] Seed complete.");

            } catch (Exception ex) {
                conn.rollback();
                log.error("[DevDataSeeder] Failed — rolled back: {}", ex.getMessage(), ex);
            }
        }
    }

    // ── ADMIN_DEPARTEMENTS ───────────────────────────────────────────────────
    /**
     * Insère les départements de développement dans {@code ADMIN_DEPARTEMENTS}.
     *
     * @param conn connexion JDBC active avec auto-commit désactivé
     * @return l'identifiant généré pour le département Informatique
     * @throws SQLException en cas d'erreur d'insertion
     */
    private long seedAdminDepartements(Connection conn) throws SQLException {
        Object[][] depts = {
            // nom,                         responsable,     telephone,       email,                        capacite, annee
            {"Informatique & Développement", "Alice Martin",  "+21655100001", "it@synapse.ma",               25, 2018},
            {"Ressources Humaines",          "Sara Benali",   "+21655100002", "rh@synapse.ma",               10, 2016},
            {"Finance & Comptabilité",       "Farid Hamdi",   "+21655100003", "finance@synapse.ma",           8, 2017},
            {"Marketing & Communication",    "Inès Karimi",   "+21655100004", "marketing@synapse.ma",        12, 2019},
        };
        String sql =
            "INSERT INTO ADMIN_DEPARTEMENTS (nom, responsable, telephone, email, capacite, annee_creation, total_employes, description) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        long itId = -1;
        try (PreparedStatement ps = conn.prepareStatement(sql, new String[]{"DEPT_ADMIN_ID"})) {
            int[] employes = {10, 3, 2, 3};
            String[] descriptions = {
                "Développement des systèmes d'information et solutions digitales de l'entreprise.",
                "Gestion du capital humain, recrutement, formation et bien-être des employés.",
                "Gestion financière, comptabilité et contrôle de gestion.",
                "Communication interne/externe, marketing digital et gestion de la marque."
            };
            for (int i = 0; i < depts.length; i++) {
                Object[] d = depts[i];
                ps.setString(1, (String) d[0]);
                ps.setString(2, (String) d[1]);
                ps.setString(3, (String) d[2]);
                ps.setString(4, (String) d[3]);
                ps.setInt(5, (int) d[4]);
                ps.setInt(6, (int) d[5]);
                ps.setInt(7, employes[i]);
                ps.setString(8, descriptions[i]);
                ps.executeUpdate();
                if (i == 0) {
                    try (ResultSet gk = ps.getGeneratedKeys()) {
                        if (gk.next()) itId = gk.getLong(1);
                    }
                }
            }
        }
        log.info("[DevDataSeeder] Seeded {} admin departements.", depts.length);
        return itId;
    }

    // ── CAMPAGNES_EVALUATION ─────────────────────────────────────────────────
    /**
     * Insère des campagnes d'évaluation de développement dans {@code CAMPAGNES_EVALUATION}.
     *
     * @param conn connexion JDBC active
     * @throws SQLException en cas d'erreur d'insertion
     */
    private void seedCampagnes(Connection conn) throws SQLException {
        String sql =
            "INSERT INTO CAMPAGNES_EVALUATION (titre, periode, annee, date_debut, date_fin, cree_par, statut) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Object[][] campagnes = {
            {"Évaluation Annuelle 2024",  "ANNUEL", 2024, "2024-12-01", "2024-12-31", "Sara Benali", "CLOTUREE"},
            {"Évaluation T1 2025",        "T1",     2025, "2025-03-01", "2025-03-31", "Sara Benali", "ACTIVE"},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] c : campagnes) {
                for (int i = 0; i < c.length; i++) ps.setObject(i + 1, c[i]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        log.info("[DevDataSeeder] Seeded {} campagnes.", campagnes.length);
    }

    // ── PERFORMANCE_EVALS ────────────────────────────────────────────────────
    /**
     * Insère des évaluations de performance de développement dans {@code PERFORMANCE_EVALS}.
     *
     * @param conn        connexion JDBC active
     * @param evaluatorId identifiant de l'évaluateur principal (Alice)
     * @param idCharles   identifiant de l'employé Charles
     * @param idEmma      identifiant de l'employée Emma
     * @param idDiana     identifiant de l'employée Diana
     * @param idHassan    identifiant de l'employé Hassan
     * @param idGhita     identifiant de l'employée Ghita
     * @param idFarid     identifiant de l'employé Farid
     * @throws SQLException en cas d'erreur d'insertion
     */
    private void seedPerformanceEvals(Connection conn, long evaluatorId,
                                      long idCharles, long idEmma, long idDiana,
                                      long idHassan, long idGhita, long idFarid) throws SQLException {
        // employeeId, evaluatorId, year, quarter, score, status
        Object[][] evals = {
            {idCharles, evaluatorId, 2024, "ANNUEL", 4.2, "PUBLIE"},
            {idEmma,    evaluatorId, 2024, "ANNUEL", 3.8, "PUBLIE"},
            {idDiana,   evaluatorId, 2024, "ANNUEL", 2.0, "PUBLIE"},  // low → attrition signal
            {idFarid,   evaluatorId, 2024, "ANNUEL", 4.5, "PUBLIE"},
            {idGhita,   evaluatorId, 2024, "ANNUEL", 3.5, "PUBLIE"},
            {idHassan,  evaluatorId, 2025, "T1",     1.5, "PUBLIE"},  // very low, new hire → high attrition
        };
        String sql =
            "INSERT INTO PERFORMANCE_EVALS (employee_id, evaluator_id, period_year, period_quarter, score, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, SYSTIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] e : evals) {
                ps.setLong(1,   (long)   e[0]);
                ps.setLong(2,   (long)   e[1]);
                ps.setInt(3,    (int)    e[2]);
                ps.setString(4, (String) e[3]);
                ps.setDouble(5, (double) e[4]);
                ps.setString(6, (String) e[5]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        log.info("[DevDataSeeder] Seeded {} performance evals.", evals.length);
    }

    // ── SOLDES_CONGES_ADMIN ──────────────────────────────────────────────────
    /**
     * Insère les soldes de congés de développement dans {@code SOLDES_CONGES_ADMIN}.
     *
     * @param conn connexion JDBC active
     * @throws SQLException en cas d'erreur d'insertion
     */
    private void seedSoldesConges(Connection conn) throws SQLException {
        // nom, dept, poste, precedent, total, report, actuel, utilises, acceptes, rejetes, expires
        Object[][] soldes = {
            {"Alice Martin",   "Informatique & Développement", "Chef de Projet",  0d, 30d, 2d, 18d, 14d, 14d, 0d, 0d},
            {"Charles Dupont", "Informatique & Développement", "Développeur",      0d, 30d, 0d, 22d, 8d,  8d,  0d, 0d},
            {"Emma Morin",     "Informatique & Développement", "Développeur",      0d, 30d, 3d, 27d, 6d,  6d,  0d, 0d},
            {"Diana Laurent",  "Informatique & Développement", "Développeur",      0d, 30d, 0d,  8d, 22d, 20d, 2d, 0d},
            {"Farid Hamdi",    "Finance & Comptabilité",       "Analyste",         5d, 35d, 5d, 25d, 15d, 15d, 0d, 0d},
            {"Sara Benali",    "Ressources Humaines",          "Responsable RH",   0d, 30d, 0d, 20d, 10d, 10d, 0d, 0d},
            {"Ghita Lahlou",   "Ressources Humaines",          "Responsable RH",   0d, 30d, 0d, 24d, 6d,  6d,  0d, 0d},
            {"Hassan Idrissi", "Informatique & Développement", "Développeur",      0d, 7d,  0d,  7d, 0d,  0d,  0d, 0d}, // new hire, prorated
            {"Inès Karimi",    "Marketing & Communication",    "Chargée Marketing",0d, 30d, 0d, 26d, 4d,  4d,  0d, 0d},
        };
        String sql =
            "INSERT INTO SOLDES_CONGES_ADMIN " +
            "  (employe_nom, departement, poste, solde_precedent, solde_total, report_solde, " +
            "   solde_actuel, conges_utilises, conges_acceptes, conges_rejetes, conges_expires) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] s : soldes) {
                for (int i = 0; i < s.length; i++) ps.setObject(i + 1, s[i]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        log.info("[DevDataSeeder] Seeded {} soldes congés.", soldes.length);
    }

    // ── PROJECTS ─────────────────────────────────────────────────────────────
    /**
     * Insère un projet de développement dans {@code PROJECTS} et retourne son identifiant généré.
     * <p>
     * Les dates sont calculées relativement à la date du jour via des offsets en jours.
     * </p>
     *
     * @param conn             connexion JDBC active
     * @param name             nom du projet
     * @param code             code court du projet
     * @param createdBy        identifiant Oracle du chef de projet
     * @param status           statut initial ({@code EN_COURS}, {@code TERMINE}...)
     * @param progress         pourcentage d'avancement initial (0-100)
     * @param startDaysAgo     décalage de la date de début par rapport à aujourd'hui (négatif = passé)
     * @param endDaysFromNow   décalage de la date de fin par rapport à aujourd'hui (négatif = passé)
     * @return l'identifiant généré du projet ({@code PROJECT_ID})
     * @throws SQLException en cas d'erreur d'insertion
     */
    private long seedProject(Connection conn, String name, String code, long createdBy,
                              String status, int progress, int startDaysAgo, int endDaysFromNow)
            throws SQLException {
        LocalDate start = LocalDate.now().plusDays(startDaysAgo);
        LocalDate end   = LocalDate.now().plusDays(endDaysFromNow);

        String sql =
            "INSERT INTO PROJECTS (name, code, created_by, start_date, end_date, status, progress_pct, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql, new String[]{"PROJECT_ID"})) {
            ps.setString(1, name);
            ps.setString(2, code);
            ps.setLong(3, createdBy);
            ps.setDate(4, Date.valueOf(start));
            ps.setDate(5, Date.valueOf(end));
            ps.setString(6, status);
            ps.setInt(7, progress);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) {
                gk.next();
                long id = gk.getLong(1);
                log.info("[DevDataSeeder] Project '{}' → id={}", name, id);
                return id;
            }
        }
    }

    // ── TASKS ────────────────────────────────────────────────────────────────
    /**
     * Insère une tâche de développement dans {@code TASKS}.
     *
     * @param conn               connexion JDBC active
     * @param projectId          identifiant du projet parent
     * @param title              titre de la tâche
     * @param assignedTo         identifiant Oracle de l'employé assigné
     * @param status             statut de la tâche ({@code TERMINEE}, {@code EN_COURS}, {@code A_FAIRE})
     * @param dueDateOffsetDays  décalage de la date d'échéance (négatif = échéance dépassée)
     * @param priority           priorité ({@code HAUTE}, {@code NORMALE}, {@code BASSE}, {@code CRITIQUE})
     * @throws SQLException en cas d'erreur d'insertion
     */
    private void seedTask(Connection conn, long projectId, String title, long assignedTo,
                           String status, int dueDateOffsetDays, String priority)
            throws SQLException {
        LocalDate due = LocalDate.now().plusDays(dueDateOffsetDays);
        String sql =
            "INSERT INTO TASKS (project_id, title, assigned_to, created_by, status, priority, due_date, progress_pct, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setString(2, title);
            ps.setLong(3, assignedTo);
            ps.setLong(4, assignedTo);
            ps.setString(5, status);
            ps.setString(6, priority);
            ps.setDate(7, Date.valueOf(due));
            ps.setInt(8, "TERMINEE".equals(status) ? 100 : "EN_COURS".equals(status) ? 50 : 0);
            ps.executeUpdate();
        }
    }

    // ── PROJECT_MEMBERS ──────────────────────────────────────────────────────
    /**
     * Insère les membres d'un projet dans {@code PROJECT_MEMBERS}.
     * <p>
     * Le chef de projet est affecté avec le rôle {@code CHEF}, les autres avec {@code MEMBRE}.
     * </p>
     *
     * @param conn      connexion JDBC active
     * @param projectId identifiant du projet
     * @param chefId    identifiant de l'employé qui est chef du projet
     * @param memberIds tableau des identifiants de tous les membres (chef inclus)
     * @throws SQLException en cas d'erreur d'insertion
     */
    private void seedMembers(Connection conn, long projectId, long chefId, long[] memberIds)
            throws SQLException {
        String sql =
            "INSERT INTO PROJECT_MEMBERS (project_id, employee_id, role, is_active, joined_at) " +
            "VALUES (?, ?, ?, 1, SYSTIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (long empId : memberIds) {
                ps.setLong(1, projectId);
                ps.setLong(2, empId);
                ps.setString(3, empId == chefId ? "CHEF" : "MEMBRE");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    /**
     * Résout l'identifiant Oracle d'un employé par son adresse email.
     *
     * @param conn  connexion JDBC active
     * @param email adresse email de l'employé seed
     * @return l'identifiant {@code EMPLOYEE_ID}, ou {@code -1} si non trouvé
     * @throws SQLException en cas d'erreur de requête
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
     * Vérifie si les données de développement ont déjà été insérées.
     *
     * @param conn connexion JDBC active
     * @return {@code true} si {@code ADMIN_DEPARTEMENTS} contient déjà des emails {@code @synapse.ma}
     * @throws SQLException en cas d'erreur de requête
     */
    private boolean alreadySeeded(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM ADMIN_DEPARTEMENTS WHERE email LIKE '%@synapse.ma'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }
}
