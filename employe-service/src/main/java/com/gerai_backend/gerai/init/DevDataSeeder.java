package com.gerai_backend.gerai.init;

import com.gerai_backend.gerai.services.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Insère des données de test réalistes dans la table {@code EMPLOYEES} au démarrage de l'application.
 *
 * <p>@Component + @Profile("dev") : exécuté uniquement avec le profil {@code dev}
 * ({@code --spring.profiles.active=dev}).</p>
 * <p>@Order(3) : s'exécute après {@link SchemaCompletionMigrator} (@Order 2) qui crée
 * préalablement les tables {@code DEPARTMENTS} et {@code POSITIONS}.</p>
 *
 * <p>Idempotence : détectée via le suffixe d'email {@code .seed@synapse.ma} —
 * si des lignes avec ce suffixe existent déjà, le seeder est ignoré.</p>
 *
 * @since 1.0
 */
@Slf4j
@Component
@Profile("dev")
@Order(3)
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    static final String SEED_PASSWORD = "Synapse2024!";

    private final DataSource          dataSource;
    private final KeycloakUserService keycloakUserService;

    // email → realm role — used to self-heal role assignments on every startup
    private static final Map<String, String> SEED_EMAIL_ROLES = Map.of(
        "alice.martin.seed@synapse.ma",   "CHEF",
        "sara.benali.seed@synapse.ma",    "ADMIN_RH",
        "charles.dupont.seed@synapse.ma", "EMPLOYE",
        "emma.morin.seed@synapse.ma",     "EMPLOYE",
        "farid.hamdi.seed@synapse.ma",    "EMPLOYE",
        "ghita.lahlou.seed@synapse.ma",   "EMPLOYE",
        "ines.karimi.seed@synapse.ma",    "EMPLOYE",
        "diana.laurent.seed@synapse.ma",  "EMPLOYE",
        "hassan.idrissi.seed@synapse.ma", "EMPLOYE",
        "lina.rahali.seed@synapse.ma",    "EMPLOYE"
    );

    /**
     * Point d'entrée du seeder, exécuté au démarrage de l'application.
     * Effectue d'abord la remise en état des rôles Keycloak (auto-guérison),
     * puis insère les employés de test si la base n'est pas encore peuplée.
     *
     * @param args arguments de démarrage Spring Boot (non utilisés)
     * @throws Exception en cas d'erreur SQL non récupérable
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Step 0: Keycloak self-healing — runs on every startup before the idempotency check.
        try {
            keycloakUserService.ensureRealmRolesExist(
                    List.of("EMPLOYE", "CHEF", "ADMIN_RH", "RH", "ADMIN"));
            keycloakUserService.ensureRolesScopeOnClient("gerai");
            keycloakUserService.ensureRealmRolesMapper("gerai");
            ensureSeedRoles();   // re-apply role assignments in case they were missed
        } catch (Exception e) {
            log.warn("[DevDataSeeder] Keycloak realm setup skipped (Keycloak unavailable?): {}", e.getMessage());
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (alreadySeeded(conn)) {
                    log.info("[DevDataSeeder] EMPLOYEES already seeded — skipping.");
                    return;
                }
                seedRefTypesConge(conn);
                seedEmployees(conn);
                conn.commit();
                log.info("[DevDataSeeder] Seed complete.");
            } catch (Exception ex) {
                conn.rollback();
                log.error("[DevDataSeeder] Failed — rolled back: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Vérifie si les données de test ont déjà été insérées en cherchant
     * des emails avec le suffixe {@code .seed@synapse.ma} dans la table EMPLOYEES.
     *
     * @param conn la connexion JDBC active
     * @return {@code true} si le seeding a déjà été effectué, {@code false} sinon
     * @throws SQLException en cas d'erreur SQL
     */
    private boolean alreadySeeded(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM EMPLOYEES WHERE email LIKE '%.seed@synapse.ma'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Insère les types de congés de référence dans {@code REF_TYPES_CONGE} si la table est vide.
     * Ces données sont nécessaires pour que le {@code demandes-service} puisse référencer
     * les identifiants de types de congé depuis la même base Oracle.
     *
     * @param conn la connexion JDBC active
     * @throws SQLException en cas d'erreur SQL
     */
    private void seedRefTypesConge(Connection conn) throws SQLException {
        if (tableExists(conn, "REF_TYPES_CONGE")) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM REF_TYPES_CONGE")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) return;
                }
            }
        } else {
            log.info("[DevDataSeeder] REF_TYPES_CONGE not found — skipping type seed.");
            return;
        }

        Object[][] types = {
            // code,          libelle,             jours, paye
            {"ANNUEL",        "Congé annuel",       30,    1},
            {"MALADIE",       "Congé maladie",      15,    1},
            {"SANS_SOLDE",    "Congé sans solde",    0,    0},
            {"FORMATION",     "Congé formation",    10,    1},
            {"MATERNITE",     "Congé maternité",    98,    1},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO REF_TYPES_CONGE (code, libelle, nombre_jours, paye, actif, couleur, icone) " +
                "VALUES (?, ?, ?, ?, 1, '#6366F1', 'ti ti-beach')")) {
            for (Object[] t : types) {
                ps.setString(1, (String) t[0]);
                ps.setString(2, (String) t[1]);
                ps.setInt(3, (int) t[2]);
                ps.setInt(4, (int) t[3]);
                ps.addBatch();
            }
            ps.executeBatch();
            log.info("[DevDataSeeder] Seeded {} leave types.", types.length);
        }
    }

    /**
     * Provisionne les comptes Keycloak pour les employés de test, puis les insère
     * dans la table {@code EMPLOYEES} avec les UUIDs Keycloak réels.
     * En cas d'indisponibilité Keycloak, un UUID aléatoire est utilisé comme fallback.
     *
     * @param conn la connexion JDBC active (transaction déjà ouverte)
     * @throws SQLException en cas d'erreur lors de l'insertion en base Oracle
     */
    private void seedEmployees(Connection conn) throws SQLException {
        // firstName  lastName    email(seed)                        dept    pos       months  status   kcRole
        Object[][] rows = {
            {"Alice",   "Martin",  "alice.martin.seed@synapse.ma",   "IT",  "IT-002",  36, "ACTIF",  "CHEF"},
            {"Sara",    "Benali",  "sara.benali.seed@synapse.ma",    "RH",  "RH-001",  48, "ACTIF",  "ADMIN_RH"},
            {"Charles", "Dupont",  "charles.dupont.seed@synapse.ma", "IT",  "IT-001",  24, "ACTIF",  "EMPLOYE"},
            {"Emma",    "Morin",   "emma.morin.seed@synapse.ma",     "IT",  "IT-001",  30, "ACTIF",  "EMPLOYE"},
            {"Farid",   "Hamdi",   "farid.hamdi.seed@synapse.ma",    "FIN", "FIN-001", 60, "ACTIF",  "EMPLOYE"},
            {"Ghita",   "Lahlou",  "ghita.lahlou.seed@synapse.ma",   "RH",  "RH-001",  18, "ACTIF",  "EMPLOYE"},
            {"Ines",    "Karimi",  "ines.karimi.seed@synapse.ma",    "MKT", "MKT-001", 14, "ACTIF",  "EMPLOYE"},
            {"Diana",   "Laurent", "diana.laurent.seed@synapse.ma",  "IT",  "IT-001",  10, "CONGE",  "EMPLOYE"},
            {"Hassan",  "Idrissi", "hassan.idrissi.seed@synapse.ma", "IT",  "IT-001",   3, "ACTIF",  "EMPLOYE"},
            {"Lina",    "Rahali",  "lina.rahali.seed@synapse.ma",    "MKT", "MKT-001",  2, "ACTIF",  "EMPLOYE"},
        };

        // Step 1 — provision Keycloak accounts and collect real UUIDs
        Map<String, String> emailToKeycloakId = new HashMap<>();
        for (Object[] r : rows) {
            String firstName = (String) r[0];
            String lastName  = (String) r[1];
            String email     = (String) r[2];
            String kcRole    = (String) r[7];
            String username  = (firstName + "." + lastName).toLowerCase();
            try {
                String kcId = keycloakUserService.provisionSeedUser(
                        username, email, firstName, lastName,
                        SEED_PASSWORD, List.of(kcRole));
                emailToKeycloakId.put(email, kcId);
                log.info("[DevDataSeeder] Keycloak user ready: {} ({})", username, kcRole);
            } catch (Exception e) {
                log.warn("[DevDataSeeder] Keycloak unavailable for {} — using random UUID: {}",
                        email, e.getMessage());
                emailToKeycloakId.put(email, UUID.randomUUID().toString());
            }
        }

        // Step 2 — insert into Oracle using the Keycloak UUIDs
        String sql =
            "INSERT INTO EMPLOYEES " +
            "  (user_id, employee_code, first_name, last_name, email, phone, hire_date, status, dept_id, position_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, " +
            "  (SELECT dept_id FROM DEPARTMENTS WHERE code = ?), " +
            "  (SELECT position_id FROM POSITIONS WHERE code = ?))";

        int codeSeq = 20;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : rows) {
                String firstName = (String) r[0];
                String lastName  = (String) r[1];
                String email     = (String) r[2];
                String deptCode  = (String) r[3];
                String posCode   = (String) r[4];
                int    monthsAgo = (int)    r[5];
                String status    = (String) r[6];

                LocalDate hireDate = LocalDate.now().minusMonths(monthsAgo);
                String userId = emailToKeycloakId.getOrDefault(email, UUID.randomUUID().toString());

                ps.setString(1, userId);
                ps.setString(2, String.format("EMP-%04d", codeSeq++));
                ps.setString(3, firstName);
                ps.setString(4, lastName);
                ps.setString(5, email);
                ps.setString(6, "+21650" + String.format("%06d", codeSeq * 7919 % 1000000));
                ps.setDate(7, Date.valueOf(hireDate));
                ps.setString(8, status);
                ps.setString(9, deptCode);
                ps.setString(10, posCode);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            log.info("[DevDataSeeder] Inserted {} employees.", results.length);
        }
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

    /**
     * Réapplique les attributions de rôles pour chaque utilisateur de test dont le compte
     * Keycloak existe déjà. L'opération est idempotente : ajouter un rôle déjà assigné
     * est un no-op dans Keycloak.
     */
    private void ensureSeedRoles() {
        SEED_EMAIL_ROLES.forEach((email, role) -> {
            try {
                String kcId = keycloakUserService.findUserIdByEmail(email);
                if (kcId != null) {
                    keycloakUserService.assignRealmRoles(kcId, List.of(role));
                    log.info("[DevDataSeeder] Role {} ensured for {}", role, email);
                }
            } catch (Exception ex) {
                log.warn("[DevDataSeeder] Could not assign role {} for {}: {}", role, email, ex.getMessage());
            }
        });
    }
}
