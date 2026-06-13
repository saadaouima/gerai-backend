package com.gerai.projetsservice.service;

import com.gerai.projetsservice.dto.EmployeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client REST vers employee-service.
 * Cache en mémoire pour éviter N appels lors du mapping des membres.
 *
 * Si employee-service n'est pas disponible, retombe sur une requête Oracle directe
 * pour garantir que les noms apparaissent correctement dans les notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.services.employee-url:http://localhost:8081}")
    private String employeeServiceUrl;

    private final RestTemplate          restTemplate = new RestTemplate();
    private final Map<Long, EmployeDTO> cache        = new ConcurrentHashMap<>();

    public List<EmployeDTO> getAllEmployes() {
        try {
            EmployeDTO[] arr = restTemplate.getForObject(
                    employeeServiceUrl + "/api/employees", EmployeDTO[].class);
            if (arr != null) {
                Arrays.stream(arr).forEach(e -> cache.put(e.getId(), e));
                return Arrays.asList(arr);
            }
        } catch (Exception e) {
            log.warn("[EmployeService] Impossible de contacter employee-service : {}", e.getMessage());
        }
        return List.of();
    }

    public EmployeDTO getEmployeById(Long employeeId) {
        if (cache.containsKey(employeeId)) return cache.get(employeeId);

        // 1. Try employee-service REST API
        try {
            EmployeDTO emp = restTemplate.getForObject(
                    employeeServiceUrl + "/api/employees/" + employeeId, EmployeDTO.class);
            if (emp != null && (emp.getNomComplet() != null || emp.getNom() != null)) {
                cache.put(employeeId, emp);
                return emp;
            }
        } catch (Exception e) {
            log.warn("[EmployeService] HTTP fallback for employee_id={}: {}", employeeId, e.getMessage());
        }

        // 2. Fallback: query Oracle directly (same DB shared by all services)
        try {
            EmployeDTO emp = jdbcTemplate.queryForObject(
                    "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL FROM GERAI.EMPLOYEES WHERE EMPLOYEE_ID = ?",
                    (rs, row) -> {
                        String prenom = rs.getString("FIRST_NAME");
                        String nom    = rs.getString("LAST_NAME");
                        String full   = ((prenom != null ? prenom : "") + " " + (nom != null ? nom : "")).trim();
                        return EmployeDTO.builder()
                                .id(rs.getLong("EMPLOYEE_ID"))
                                .prenom(prenom)
                                .nom(nom)
                                .email(rs.getString("EMAIL"))
                                .nomComplet(full.isEmpty() ? "Employé #" + employeeId : full)
                                .build();
                    },
                    employeeId);
            if (emp != null) {
                log.info("[EmployeService] DB resolved name for employee_id={}: {}", employeeId, emp.getNomComplet());
                cache.put(employeeId, emp);
                return emp;
            }
        } catch (Exception dbEx) {
            log.warn("[EmployeService] DB fallback failed for employee_id={}: {}", employeeId, dbEx.getMessage());
        }

        // 3. Last resort placeholder
        return EmployeDTO.builder()
                .id(employeeId)
                .nomComplet("Employé #" + employeeId)
                .build();
    }
}