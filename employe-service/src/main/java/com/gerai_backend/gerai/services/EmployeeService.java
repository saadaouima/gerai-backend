package com.gerai_backend.gerai.services;

import com.gerai_backend.gerai.dto.CreateEmployeeResponse;
import com.gerai_backend.gerai.models.Employee;
import com.gerai_backend.gerai.repositories.EmployeeRepository;
import com.gerai_backend.gerai.dto.CreateEmployeeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository         employeeRepository;
    private final KeycloakUserService        keycloakUserService;
    private final PasswordGeneratorService   passwordGeneratorService;
    private final EmailService               emailService;

    // ─────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────
    @Transactional
    public CreateEmployeeResponse createEmployee(CreateEmployeeRequest request) {

        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Employee with email " + request.getEmail() + " already exists"
            );
        }

        // 1. Generate secure temporary password
        String tempPassword = passwordGeneratorService.generate();

        // 2. Create Keycloak user first
        String keycloakUserId;
        try {
            keycloakUserId = keycloakUserService.createKeycloakUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    tempPassword
            );
            log.info("Keycloak user created with id: {}", keycloakUserId);
        } catch (Exception ex) {
            log.error("Failed to create Keycloak user: {}", ex.getMessage());
            throw new RuntimeException("Keycloak user creation failed: " + ex.getMessage(), ex);
        }

        // 3. Build and save employee in Oracle DB
        //    CORRECTION : id est Long (IDENTITY), plus UUID
        //    CORRECTION : keycloakUserId → user_id (colonne USER_ID VARCHAR2(255))
        //    CORRECTION : pas de jobTitle/salary — champs dans POSITIONS/CONTRACTS
        //    CORRECTION : employeeCode généré automatiquement
        //    CORRECTION : deptId et positionId obligatoires (FKs dans EMPLOYEES)
        Employee employee = Employee.builder()
                .keycloakUserId(keycloakUserId)                           // → USER_ID
                .employeeCode(generateEmployeeCode())                     // → EMPLOYEE_CODE
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .birthDate(request.getBirthDate())
                .nationalId(request.getNationalId())
                .gender(request.getGender())
                .address(request.getAddress())
                .photoUrl(request.getPhotoUrl())
                .deptId(request.getDeptId())                              // → DEPT_ID FK
                .positionId(request.getPositionId())                      // → POSITION_ID FK
                .managerId(request.getManagerId())                        // → MANAGER_ID FK
                .hireDate(request.getHireDate() != null
                        ? request.getHireDate() : LocalDate.now())
                .status("ACTIF")
                .build();

        try {
            Employee saved = employeeRepository.save(employee);
            log.info("Employee saved in DB with id: {}", saved.getId());

            // Send temp password via email — never store it
            emailService.sendTemporaryPassword(
                    saved.getEmail(),
                    saved.getFirstName(),
                    request.getUsername(),
                    tempPassword
            );

            return CreateEmployeeResponse.builder()
                    .id(saved.getId())                   // Long Oracle IDENTITY
                    .employeeCode(saved.getEmployeeCode())
                    .firstName(saved.getFirstName())
                    .lastName(saved.getLastName())
                    .email(saved.getEmail())
                    .hireDate(saved.getHireDate())
                    .deptId(saved.getDeptId())
                    .positionId(saved.getPositionId())
                    .managerId(saved.getManagerId())
                    .status(saved.getStatus())
                    .keycloakUserId(saved.getKeycloakUserId())
                    .temporaryPassword(tempPassword)     // affiché une seule fois
                    .createdAt(saved.getCreatedAt())
                    .build();

        } catch (Exception ex) {
            // DB failed → rollback Keycloak user to avoid orphan
            log.error("DB save failed, rolling back Keycloak user: {}", keycloakUserId);
            keycloakUserService.deleteKeycloakUser(keycloakUserId);
            throw new RuntimeException(
                    "Employee save failed, Keycloak user rolled back: " + ex.getMessage(), ex
            );
        }
    }

    // ─────────────────────────────────────────
    // READ ALL
    // ─────────────────────────────────────────
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    // ─────────────────────────────────────────
    // READ ONE — CORRECTION : UUID → Long
    // ─────────────────────────────────────────
    public Employee getEmployeeById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
    }

    // ─────────────────────────────────────────
    // UPDATE — CORRECTION : UUID → Long
    //          champs jobTitle/salary → deptId/positionId
    // ─────────────────────────────────────────
    @Transactional
    public Employee updateEmployee(Long id, CreateEmployeeRequest request) {
        Employee employee = getEmployeeById(id);

        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(request.getEmail());
        employee.setHireDate(request.getHireDate());
        employee.setPhone(request.getPhone());
        employee.setBirthDate(request.getBirthDate());
        employee.setNationalId(request.getNationalId());
        employee.setGender(request.getGender());
        employee.setAddress(request.getAddress());
        employee.setPhotoUrl(request.getPhotoUrl());

        if (request.getDeptId()     != null) employee.setDeptId(request.getDeptId());
        if (request.getPositionId() != null) employee.setPositionId(request.getPositionId());
        if (request.getManagerId()  != null) employee.setManagerId(request.getManagerId());

        // keycloakUserId n'est jamais modifié ici
        return employeeRepository.save(employee);
    }

    // ─────────────────────────────────────────
    // DELETE — CORRECTION : UUID → Long
    // ─────────────────────────────────────────
    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = getEmployeeById(id);

        try {
            keycloakUserService.deleteKeycloakUser(employee.getKeycloakUserId());
            log.info("Keycloak user deleted: {}", employee.getKeycloakUserId());
        } catch (Exception ex) {
            log.error("Failed to delete Keycloak user: {}", ex.getMessage());
            throw new RuntimeException("Keycloak deletion failed: " + ex.getMessage(), ex);
        }

        employeeRepository.delete(employee);
        log.info("Employee deleted from DB: {}", id);
    }

    // ─────────────────────────────────────────
    // SEARCH BY EMAIL
    // ─────────────────────────────────────────
    public Optional<Employee> getEmployeeByEmail(String email) {
        return employeeRepository.findByEmail(email);
    }

    // ─────────────────────────────────────────
    // HELPER — Génération du matricule employé
    // Format : EMP-XXXX (padé sur 4 chiffres)
    // ─────────────────────────────────────────
    private String generateEmployeeCode() {
        long count = employeeRepository.count() + 1;
        String code;
        int attempt = 0;
        do {
            code = String.format("EMP-%04d", count + attempt);
            attempt++;
        } while (employeeRepository.existsByEmployeeCode(code) && attempt < 1000);
        return code;
    }
}