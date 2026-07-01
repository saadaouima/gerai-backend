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
 * Client REST vers {@code employee-service} avec cache en mémoire et repli Oracle.
 * <p>
 * Stratégie de résolution des données employé (dans l'ordre) :
 * <ol>
 *   <li>Cache en mémoire ({@link ConcurrentHashMap}) — évite les N+1 appels lors du mapping des membres.</li>
 *   <li>Appel REST à {@code employee-service} via {@link RestTemplate}.</li>
 *   <li>Requête Oracle directe (même base de données partagée) si {@code employee-service} est indisponible.</li>
 *   <li>Placeholder {@code "Employé #id"} en dernier recours.</li>
 * </ol>
 * </p>
 * <p>
 * {@code @Service} : composant Spring géré par le conteneur IoC.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeService {

    /** Template JDBC pour le repli sur une requête Oracle directe. */
    private final JdbcTemplate jdbcTemplate;

    /** URL de base de l'employee-service (configurable via {@code app.services.employee-url}). */
    @Value("${app.services.employee-url:http://localhost:8081}")
    private String employeeServiceUrl;

    /** Client HTTP pour les appels REST vers employee-service. */
    private final RestTemplate          restTemplate = new RestTemplate();
    /** Cache en mémoire des DTOs d'employés indexés par identifiant Oracle. */
    private final Map<Long, EmployeDTO> cache        = new ConcurrentHashMap<>();

    /**
     * Récupère tous les employés depuis {@code employee-service} et les met en cache.
     * <p>
     * En cas d'indisponibilité du service, retourne une liste vide sans lever d'exception.
     * </p>
     *
     * @return liste complète des employés, vide si le service est inaccessible
     */
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

    /**
     * Résout le DTO d'un employé par son identifiant Oracle, avec cache et replis multiples.
     * <p>
     * Ne lève jamais d'exception : retourne un placeholder {@code "Employé #id"} en dernier recours.
     * </p>
     *
     * @param employeeId identifiant Oracle de l'employé ({@code EMPLOYEES.EMPLOYEE_ID})
     * @return le DTO de l'employé, ou un placeholder si toutes les stratégies échouent
     */
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