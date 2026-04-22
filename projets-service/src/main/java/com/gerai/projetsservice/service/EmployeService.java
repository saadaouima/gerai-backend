package com.gerai.projetsservice.service;

import com.gerai.projetsservice.dto.EmployeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * Si employee-service n'est pas disponible, retourne des EmployeDTO vides
 * plutôt que de faire échouer la requête projet.
 */
@Slf4j
@Service
class EmployeService {

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
        try {
            EmployeDTO emp = restTemplate.getForObject(
                    employeeServiceUrl + "/api/employees/" + employeeId, EmployeDTO.class);
            if (emp != null) cache.put(employeeId, emp);
            return emp;
        } catch (Exception e) {
            log.warn("[EmployeService] Employé {} introuvable : {}", employeeId, e.getMessage());
            return EmployeDTO.builder()
                    .id(employeeId)
                    .prenom("Emp")
                    .nom("#" + employeeId)
                    .nomComplet("Employé #" + employeeId)
                    .build();
        }
    }
}