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
 * Correction : tous les @PathVariable UUID id → Long id
 * (la PK est maintenant NUMBER IDENTITY Oracle, pas UUID).
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    // Create new employee
    @PostMapping
    public ResponseEntity<CreateEmployeeResponse> createEmployee(
            @RequestBody @Valid CreateEmployeeRequest request) {
        CreateEmployeeResponse response = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Get all employees — native query mapped to Angular Employe shape
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

    // Get employee by ID — native JDBC with fallback
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

    // Update employee — CORRECTION : UUID → Long
    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(
            @PathVariable Long id,
            @RequestBody @Valid CreateEmployeeRequest request) {
        Employee updated = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(updated);
    }

    // Delete employee — CORRECTION : UUID → Long
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    // Global HR stats — GET /api/employes/stats
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

    // Per-employee stats — GET /api/employes/{id}/stats
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

    private String str(Object val) {
        return val != null ? val.toString() : "";
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    // Employees managed by a specific chef — GET /api/employes/chef/{chefId}
    @GetMapping("/chef/{chefId}")
    public ResponseEntity<List<Employee>> getByChef(@PathVariable Long chefId) {
        try {
            return ResponseEntity.ok(employeeRepository.findByManagerId(chefId));
        } catch (Exception e) {
            log.error("getByChef failed for chefId={}: {}", chefId, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // List all positions for form dropdowns — GET /api/employes/postes
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

    // List all departments for form dropdowns — GET /api/employes/departements
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

    // Search by email (?email=) or free text (?q=)
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