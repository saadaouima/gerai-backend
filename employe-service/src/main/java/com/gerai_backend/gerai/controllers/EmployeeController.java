package com.gerai_backend.gerai.controllers;

import com.gerai_backend.gerai.dto.CreateEmployeeRequest;
import com.gerai_backend.gerai.dto.CreateEmployeeResponse;
import com.gerai_backend.gerai.models.Employee;
import com.gerai_backend.gerai.repositories.EmployeeRepository;
import com.gerai_backend.gerai.services.EmployeeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contrôleur REST gérant toutes les opérations CRUD sur les employés de la plateforme Synapse.
 * Expose les routes {@code /employees} et {@code /employes} (double mapping pour compatibilité Angular).
 *
 * <p>@RestController : combine {@code @Controller} et {@code @ResponseBody}, tous les retours
 * sont sérialisés en JSON.</p>
 * <p>@RequestMapping : préfixe les routes sur {@code /employees} et {@code /employes}.</p>
 *
 * <p>Les requêtes de liste sont effectuées via {@link org.springframework.jdbc.core.JdbcTemplate}
 * natif avec plusieurs niveaux de repli (avec jointures, sans jointures, minimal)
 * pour garantir la résilience face aux évolutions du schéma Oracle.</p>
 *
 * <p>Note : la clé primaire est {@code Long} (NUMBER IDENTITY Oracle), plus UUID.</p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping({"/employees", "/employes"})
public class EmployeeController {

    private final EmployeeService    employeeService;
    private final EmployeeRepository employeeRepository;
    private final JdbcTemplate       jdbcTemplate;

    @Autowired
    public EmployeeController(EmployeeService employeeService,
                              EmployeeRepository employeeRepository,
                              JdbcTemplate jdbcTemplate) {
        this.employeeService    = employeeService;
        this.employeeRepository = employeeRepository;
        this.jdbcTemplate       = jdbcTemplate;
    }

    /**
     * Gère les exceptions {@link IllegalArgumentException} levées lors de conflits métier
     * (ex. email déjà existant) et retourne une réponse HTTP 409 Conflict.
     *
     * @param ex l'exception levée contenant le message d'erreur
     * @return une réponse 409 avec le message d'erreur dans un corps JSON
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    /**
     * Crée un nouvel employé : provisionne un compte Keycloak, insère l'enregistrement
     * en base Oracle et envoie le mot de passe temporaire par email.
     *
     * @param request les données de l'employé à créer (validées avec {@code @Valid})
     * @return une réponse HTTP 201 contenant les informations de l'employé créé
     */
    @PostMapping
    public ResponseEntity<CreateEmployeeResponse> createEmployee(
            @RequestBody @Valid CreateEmployeeRequest request) {
        CreateEmployeeResponse response = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retourne la liste de tous les employés actifs (statut différent de {@code DEMISSION}),
     * enrichie avec le nom du département et le titre du poste via jointures SQL.
     *
     * <p>Utilise une stratégie de repli en 4 niveaux : requête complète avec jointures,
     * puis département seul, puis sans jointures, puis colonnes minimales.</p>
     *
     * @return une réponse HTTP 200 avec la liste des employés au format DTO Angular,
     *         ou une liste vide en cas d'échec de toutes les requêtes
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllEmployees() {
        // Try full query (with dept + position joins), then dept-only, then bare minimum
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, e.EMAIL, e.PHONE, " +
                "       e.PHOTO_URL, e.HIRE_DATE, e.MANAGER_ID, e.STATUS, " +
                "       e.GENDER, e.BIRTH_DATE, e.ADDRESS, e.EMPLOYEE_CODE, " +
                "       d.NAME  AS DEPT_NAME, " +
                "       p.TITLE AS POSITION_TITLE " +
                "FROM EMPLOYEES e " +
                "LEFT JOIN DEPARTMENTS d ON e.DEPT_ID    = d.DEPT_ID " +
                "LEFT JOIN POSITIONS   p ON e.POSITION_ID = p.POSITION_ID " +
                "WHERE e.STATUS <> 'DEMISSION' " +
                "ORDER BY e.EMPLOYEE_ID"
            );
            return ResponseEntity.ok(rows.stream().map(this::toDto).toList());
        } catch (Exception e1) {
            log.warn("Full query failed ({}), trying dept-only...", e1.getMessage());
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, e.EMAIL, e.PHONE, " +
                "       e.PHOTO_URL, e.HIRE_DATE, e.MANAGER_ID, e.STATUS, " +
                "       e.GENDER, e.BIRTH_DATE, e.ADDRESS, e.EMPLOYEE_CODE, " +
                "       d.NAME AS DEPT_NAME " +
                "FROM EMPLOYEES e " +
                "LEFT JOIN DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID " +
                "WHERE e.STATUS <> 'DEMISSION' " +
                "ORDER BY e.EMPLOYEE_ID"
            );
            return ResponseEntity.ok(rows.stream().map(this::toDto).toList());
        } catch (Exception e2) {
            log.warn("Dept-only query failed ({}), trying bare...", e2.getMessage());
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, PHONE, " +
                "       PHOTO_URL, HIRE_DATE, MANAGER_ID, STATUS, " +
                "       GENDER, BIRTH_DATE, ADDRESS, EMPLOYEE_CODE " +
                "FROM EMPLOYEES WHERE STATUS <> 'DEMISSION' ORDER BY EMPLOYEE_ID"
            );
            return ResponseEntity.ok(rows.stream().map(this::toDto).toList());
        } catch (Exception e3) {
            log.warn("Bare query failed ({}), trying minimal...", e3.getMessage());
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, HIRE_DATE, EMPLOYEE_CODE " +
                "FROM EMPLOYEES WHERE STATUS <> 'DEMISSION' ORDER BY EMPLOYEE_ID"
            );
            return ResponseEntity.ok(rows.stream().map(this::toDto).toList());
        } catch (Exception e4) {
            log.error("All employee queries failed: {}", e4.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Retourne le profil complet d'un employé par son identifiant Oracle,
     * avec jointures sur les tables DEPARTMENTS et POSITIONS.
     *
     * <p>Utilise la même stratégie de repli en 4 niveaux que {@link #getAllEmployees()}.</p>
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @return une réponse HTTP 200 avec le DTO de l'employé, 404 si introuvable,
     *         ou 500 si toutes les requêtes échouent
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEmployeeById(@PathVariable Long id) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, e.EMAIL, e.PHONE, " +
                "       e.PHOTO_URL, e.HIRE_DATE, e.MANAGER_ID, e.STATUS, " +
                "       e.GENDER, e.BIRTH_DATE, e.ADDRESS, e.EMPLOYEE_CODE, " +
                "       d.NAME  AS DEPT_NAME, " +
                "       p.TITLE AS POSITION_TITLE " +
                "FROM EMPLOYEES e " +
                "LEFT JOIN DEPARTMENTS d ON e.DEPT_ID     = d.DEPT_ID " +
                "LEFT JOIN POSITIONS   p ON e.POSITION_ID = p.POSITION_ID " +
                "WHERE e.EMPLOYEE_ID = ?", id
            );
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(toDto(rows.get(0)));
        } catch (Exception e1) {
            log.warn("Full query failed for id={}: {}", id, e1.getMessage());
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, e.EMAIL, e.PHONE, " +
                "       e.PHOTO_URL, e.HIRE_DATE, e.MANAGER_ID, e.STATUS, " +
                "       e.GENDER, e.BIRTH_DATE, e.ADDRESS, e.EMPLOYEE_CODE, " +
                "       d.NAME AS DEPT_NAME " +
                "FROM EMPLOYEES e " +
                "LEFT JOIN DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID " +
                "WHERE e.EMPLOYEE_ID = ?", id
            );
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(toDto(rows.get(0)));
        } catch (Exception e2) {
            log.warn("Dept-only query failed for id={}: {}", id, e2.getMessage());
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, PHONE, " +
                "       PHOTO_URL, HIRE_DATE, MANAGER_ID, STATUS, " +
                "       GENDER, BIRTH_DATE, ADDRESS, EMPLOYEE_CODE " +
                "FROM EMPLOYEES WHERE EMPLOYEE_ID = ?", id
            );
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(toDto(rows.get(0)));
        } catch (Exception e3) {
            log.warn("Bare query failed for id={}: {}", id, e3.getMessage());
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, HIRE_DATE, EMPLOYEE_CODE " +
                "FROM EMPLOYEES WHERE EMPLOYEE_ID = ?", id
            );
            if (rows.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(toDto(rows.get(0)));
        } catch (Exception e4) {
            log.error("All queries failed for employee {}: {}", id, e4.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Met à jour les informations d'un employé existant.
     *
     * @param id      l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé à modifier
     * @param request les nouvelles données de l'employé (validées avec {@code @Valid})
     * @return une réponse HTTP 200 contenant l'entité {@link com.gerai_backend.gerai.models.Employee} mise à jour
     */
    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(
            @PathVariable Long id,
            @RequestBody @Valid CreateEmployeeRequest request) {
        Employee updated = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Supprime un employé : désactive son compte Keycloak, puis tente une suppression
     * physique (avec repli en soft-delete {@code DEMISSION} si des contraintes FK bloquent).
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé à supprimer
     * @return une réponse HTTP 204 No Content après suppression réussie
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retourne les statistiques RH globales de la plateforme :
     * nombre total d'employés, employés actifs, nouveaux ce mois-ci,
     * congés en attente et formations actives.
     *
     * @return une réponse HTTP 200 avec les statistiques agrégées (valeurs à 0 en cas d'erreur SQL)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS TOTAL, " +
                    "SUM(CASE WHEN STATUS = 'ACTIF' THEN 1 ELSE 0 END) AS ACTIFS, " +
                    "SUM(CASE WHEN HIRE_DATE >= TRUNC(SYSDATE, 'MM') THEN 1 ELSE 0 END) AS NOUVEAUX " +
                    "FROM GERAI.EMPLOYEES");
            stats.put("totalEmployes",        toInt(row.get("TOTAL")));
            stats.put("emploesActifs",         toInt(row.get("ACTIFS")));
            stats.put("nouveauxEmployesMois",  toInt(row.get("NOUVEAUX")));
        } catch (Exception e) {
            log.warn("Employee count query failed: {}", e.getMessage());
            stats.put("totalEmployes", 0); stats.put("emploesActifs", 0); stats.put("nouveauxEmployesMois", 0);
        }
        try {
            Long pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM GERAI.LEAVE_REQUESTS WHERE STATUS = 'EN_ATTENTE'", Long.class);
            stats.put("congesEnAttente", pending != null ? pending : 0);
        } catch (Exception e) { stats.put("congesEnAttente", 0); }
        try {
            Long trainings = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM GERAI.TRAINING_REQUESTS WHERE STATUS NOT IN ('REFUSE','ANNULE')", Long.class);
            stats.put("formationsActives", trainings != null ? trainings : 0);
        } catch (Exception e) { stats.put("formationsActives", 0); }
        return ResponseEntity.ok(stats);
    }

    /**
     * Retourne les statistiques individuelles d'un employé pour l'année en cours :
     * formations suivies, congés restants, taux de présence et objectifs atteints.
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @return une réponse HTTP 200 avec les statistiques de l'employé
     *         (valeurs par défaut si les données ne sont pas disponibles)
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getEmployeeStats(@PathVariable Long id) {
        Map<String, Object> stats = new LinkedHashMap<>();
        int year = LocalDate.now().getYear();
        try {
            Long formations = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM GERAI.TRAINING_REQUESTS " +
                    "WHERE EMPLOYEE_ID = ? AND STATUS NOT IN ('REFUSE','ANNULE')", Long.class, id);
            stats.put("formationsSuivies", formations != null ? formations : 0);
            stats.put("formationsTotal",   formations != null ? formations : 0);
        } catch (Exception e) { stats.put("formationsSuivies", 0); stats.put("formationsTotal", 0); }
        try {
            BigDecimal pris = jdbcTemplate.queryForObject(
                    "SELECT NVL(SUM(DAYS_COUNT),0) FROM GERAI.LEAVE_REQUESTS " +
                    "WHERE EMPLOYEE_ID = ? AND STATUS = 'VALIDE_RH' " +
                    "AND EXTRACT(YEAR FROM START_DATE) = ?", BigDecimal.class, id, year);
            int joursUtilises = pris != null ? pris.intValue() : 0;
            stats.put("congesRestants", 30 - joursUtilises);
            stats.put("congesTotal",    30);
        } catch (Exception e) { stats.put("congesRestants", 30); stats.put("congesTotal", 30); }
        stats.put("tauxPresence",      0);
        stats.put("objectifsAtteints", 0);
        stats.put("tachesCompletes",   0);
        stats.put("tachesTotal",       0);
        return ResponseEntity.ok(stats);
    }

    /**
     * Convertit une ligne brute de résultat SQL en DTO au format attendu par Angular.
     *
     * @param r la ligne SQL sous forme de {@code Map<String, Object>} (clés en majuscules Oracle)
     * @return un {@code Map} avec les clés en camelCase attendues par le frontend Angular
     */
    private Map<String, Object> toDto(Map<String, Object> r) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id",            r.get("EMPLOYEE_ID"));
        dto.put("nom",           str(r.get("LAST_NAME")));
        dto.put("prenom",        str(r.get("FIRST_NAME")));
        dto.put("email",         str(r.get("EMAIL")));
        dto.put("telephone",     str(r.get("PHONE")));
        dto.put("photo",         str(r.get("PHOTO_URL")));
        dto.put("dateEmbauche",  r.get("HIRE_DATE")  != null ? r.get("HIRE_DATE").toString()  : null);
        dto.put("dateNaissance", r.get("BIRTH_DATE") != null ? r.get("BIRTH_DATE").toString() : null);
        dto.put("poste",         str(r.get("POSITION_TITLE")));
        dto.put("departement",   str(r.get("DEPT_NAME")));
        dto.put("chefId",        r.get("MANAGER_ID"));
        dto.put("statut",        r.get("STATUS") != null ? r.get("STATUS").toString() : "ACTIF");
        dto.put("genre",         str(r.get("GENDER")));
        dto.put("adresse",       str(r.get("ADDRESS")));
        dto.put("employeeCode",  str(r.get("EMPLOYEE_CODE")));
        return dto;
    }

    /**
     * Convertit un objet en {@code String} en gérant la valeur {@code null}.
     *
     * @param val la valeur à convertir
     * @return la représentation en chaîne de caractères, ou une chaîne vide si {@code null}
     */
    private String str(Object val) {
        return val != null ? val.toString() : "";
    }

    /**
     * Convertit un objet {@link Number} en entier primitif.
     *
     * @param val la valeur à convertir (doit implémenter {@link Number})
     * @return la valeur entière, ou {@code 0} si {@code null} ou non numérique
     */
    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    /**
     * Retourne la liste des employés dont le manager direct est l'utilisateur identifié par {@code chefId}.
     *
     * @param chefId l'identifiant Oracle ({@code EMPLOYEE_ID}) du manager
     * @return une réponse HTTP 200 avec la liste des membres de l'équipe, ou une liste vide en cas d'erreur
     */
    @GetMapping("/chef/{chefId}")
    public ResponseEntity<List<Employee>> getByChef(@PathVariable Long chefId) {
        try {
            return ResponseEntity.ok(employeeRepository.findByManagerId(chefId));
        } catch (Exception e) {
            log.error("getByChef failed for chefId={}: {}", chefId, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Retourne la liste de tous les postes disponibles pour alimenter les listes déroulantes
     * des formulaires Angular (id + libellé du poste).
     *
     * @return une réponse HTTP 200 avec la liste des postes triés par titre,
     *         ou une liste vide si la table {@code POSITIONS} est inaccessible
     */
    @GetMapping("/postes")
    public ResponseEntity<List<Map<String, Object>>> getPositions() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT POSITION_ID AS id, TITLE AS title FROM POSITIONS ORDER BY TITLE");
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.warn("Failed to load positions: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Retourne la liste de tous les départements pour alimenter les listes déroulantes
     * des formulaires Angular (id + nom du département).
     *
     * @return une réponse HTTP 200 avec la liste des départements triés par nom,
     *         ou une liste vide si la table {@code DEPARTMENTS} est inaccessible
     */
    @GetMapping("/departements")
    public ResponseEntity<List<Map<String, Object>>> getDepartments() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DEPT_ID AS id, NAME AS name FROM DEPARTMENTS ORDER BY NAME");
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.warn("Failed to load departments: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Recherche des employés actifs par email exact ({@code ?email=}) ou par texte libre
     * ({@code ?q=} sur prénom, nom et email, insensible à la casse).
     *
     * <p>Retourne une liste vide si aucun paramètre n'est fourni.
     * Utilise une stratégie de repli (avec jointures, puis sans jointures).</p>
     *
     * @param email adresse email exacte à rechercher (optionnel)
     * @param q     texte libre à rechercher dans prénom, nom ou email (optionnel)
     * @return une réponse HTTP 200 avec la liste des employés correspondants au format DTO Angular
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String q) {
        if ((email == null || email.isBlank()) && (q == null || q.isBlank())) {
            return ResponseEntity.ok(List.of());
        }
        // Full query with joins, falling back to bare if schema is incomplete
        for (boolean withJoins : new boolean[]{true, false}) {
            try {
                List<Map<String, Object>> rows;
                String base = withJoins
                    ? "SELECT e.EMPLOYEE_ID, e.FIRST_NAME, e.LAST_NAME, e.EMAIL, e.PHONE, " +
                      "       e.PHOTO_URL, e.HIRE_DATE, e.MANAGER_ID, e.STATUS, " +
                      "       e.GENDER, e.BIRTH_DATE, e.ADDRESS, e.EMPLOYEE_CODE, " +
                      "       d.NAME AS DEPT_NAME " +
                      "FROM EMPLOYEES e " +
                      "LEFT JOIN DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID " +
                      "WHERE e.STATUS = 'ACTIF'"
                    : "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, PHONE, " +
                      "       PHOTO_URL, HIRE_DATE, MANAGER_ID, STATUS, " +
                      "       GENDER, BIRTH_DATE, ADDRESS, EMPLOYEE_CODE " +
                      "FROM EMPLOYEES WHERE STATUS = 'ACTIF'";
                if (email != null && !email.isBlank()) {
                    String col = withJoins ? "e.EMAIL" : "EMAIL";
                    rows = jdbcTemplate.queryForList(base + " AND UPPER(" + col + ") = UPPER(?)", email);
                } else {
                    String like = "%" + q.toUpperCase() + "%";
                    String fn = withJoins ? "e.FIRST_NAME" : "FIRST_NAME";
                    String ln = withJoins ? "e.LAST_NAME"  : "LAST_NAME";
                    String em = withJoins ? "e.EMAIL"      : "EMAIL";
                    rows = jdbcTemplate.queryForList(
                        base + " AND (UPPER(" + fn + ") LIKE ? OR UPPER(" + ln + ") LIKE ? OR UPPER(" + em + ") LIKE ?)" +
                        " ORDER BY " + ln + ", " + fn,
                        like, like, like);
                }
                return ResponseEntity.ok(rows.stream().map(this::toDto).toList());
            } catch (Exception e) {
                log.warn("search (withJoins={}) failed q='{}': {}", withJoins, q, e.getMessage());
            }
        }
        return ResponseEntity.ok(List.of());
    }
}