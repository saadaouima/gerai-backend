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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final KeycloakUserService keycloakUserService;
    private final PasswordGeneratorService passwordGeneratorService;
    private final EmailService emailService;

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


        // 2. Generate secure temporary password
        String tempPassword = passwordGeneratorService.generate();

        // 3. Create Keycloak user first
        String keycloakUserId;
        try {
            keycloakUserId = keycloakUserService.createKeycloakUser(
                    request.getUsername(),   // used only in Keycloak
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

        // 4. Build and save employee in Oracle DB
        Employee employee = Employee.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .hireDate(request.getHireDate() != null
                        ? request.getHireDate()
                        : LocalDate.now())
                .jobTitle(request.getJobTitle())
                .salary(request.getSalary())
                .keycloakUserId(keycloakUserId)
                // ← NO temporaryPassword here — Employee is a DB entity
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

            // 5. TODO: Send tempPassword to employee via email — never store it
            return CreateEmployeeResponse.builder()
                    .id(saved.getId())
                    .firstName(saved.getFirstName())
                    .lastName(saved.getLastName())
                    .email(saved.getEmail())
                    .hireDate(saved.getHireDate())
                    .jobTitle(saved.getJobTitle())
                    .salary(saved.getSalary())
                    .keycloakUserId(saved.getKeycloakUserId())
                    .temporaryPassword(tempPassword)
                    .build();

        } catch (Exception ex) {
            // 6. DB failed → rollback Keycloak user to avoid orphan
            log.error("DB save failed, rolling back Keycloak user: {}", keycloakUserId);
            keycloakUserService.deleteKeycloakUser(keycloakUserId);
            throw new RuntimeException("Employee save failed, Keycloak user rolled back: " + ex.getMessage(), ex);
        }
    }


    //SAVE EMPLOYEE (private — DB only)
    private Employee saveEmployee(CreateEmployeeRequest request, String keycloakUserId) {
        Employee employee = Employee.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .hireDate(request.getHireDate() != null
                        ? request.getHireDate()
                        : LocalDate.now())
                .jobTitle(request.getJobTitle())
                .salary(request.getSalary())
                .keycloakUserId(keycloakUserId)
                .build();

        try {
            Employee saved = employeeRepository.save(employee);
            log.info("Employee saved in DB with id: {}", saved.getId());
            return saved;
        } catch (Exception ex) {
            // DB failed → rollback Keycloak user to avoid orphan
            log.error("DB save failed, rolling back Keycloak user: {}", keycloakUserId);
            keycloakUserService.deleteKeycloakUser(keycloakUserId);
            throw new RuntimeException(
                    "Employee save failed, Keycloak user rolled back: " + ex.getMessage(), ex
            );
        }
    }




    // READ ALL
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }


    // READ ONE
    public Employee getEmployeeById(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
    }

    // ─────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────
    @Transactional
    public Employee updateEmployee(UUID id, CreateEmployeeRequest request) {
        Employee employee = getEmployeeById(id);

        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(request.getEmail());
        employee.setHireDate(request.getHireDate());
        employee.setJobTitle(request.getJobTitle());
        employee.setSalary(request.getSalary());

        // Note: keycloakUserId is never updated here
        // Identity changes (username/email) go through KeycloakUserService separately

        return employeeRepository.save(employee);
    }


    // DELETE
    @Transactional
    public void deleteEmployee(UUID id) {
        Employee employee = getEmployeeById(id);

        // 1. Delete from Keycloak first
        try {
            keycloakUserService.deleteKeycloakUser(employee.getKeycloakUserId());
            log.info("Keycloak user deleted: {}", employee.getKeycloakUserId());
        } catch (Exception ex) {
            log.error("Failed to delete Keycloak user: {}", ex.getMessage());
            throw new RuntimeException("Keycloak deletion failed: " + ex.getMessage(), ex);
        }

        // 2. Then delete from DB
        employeeRepository.delete(employee);
        log.info("Employee deleted from DB: {}", id);
    }

    // ─────────────────────────────────────────
// SEARCH BY EMAIL
// ─────────────────────────────────────────
    public Optional<Employee> getEmployeeByEmail(String email) {
        return employeeRepository.findByEmail(email);
    }
}