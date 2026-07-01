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

/**
 * Service métier principal du microservice {@code employe-service}.
 * Orchestre la création, la mise à jour et la suppression des employés en combinant
 * la persistance Oracle (via {@link EmployeeRepository}), le provisionnement Keycloak
 * (via {@link KeycloakUserService}) et la publication d'événements Kafka
 * (via {@link EmployeeEventProducer}).
 *
 * <p>@Service : enregistré comme bean Spring et injecté dans {@link com.gerai_backend.gerai.controllers.EmployeeController}.</p>
 * <p>@Transactional : les opérations de modification sont exécutées dans une transaction JPA
 * avec rollback automatique sur exception.</p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository         employeeRepository;
    private final KeycloakUserService        keycloakUserService;
    private final PasswordGeneratorService   passwordGeneratorService;
    private final EmailService               emailService;
    private final EmployeeEventProducer      employeeEventProducer;

    /**
     * Crée un nouvel employé en base Oracle et provisionne son compte Keycloak.
     * Séquence : génération du mot de passe → création Keycloak → sauvegarde Oracle
     * → publication Kafka → envoi email.
     *
     * <p>Gestion des cas particuliers :</p>
     * <ul>
     *   <li>Si l'email existe déjà en base ET dans Keycloak : {@link IllegalArgumentException} (doublon)</li>
     *   <li>Si l'email existe en base mais pas dans Keycloak (compte orphelin) :
     *       re-provisionnement Keycloak et mise à jour du lien.</li>
     *   <li>Si la sauvegarde Oracle échoue après création Keycloak : rollback Keycloak.</li>
     * </ul>
     *
     * @param request les données de l'employé à créer
     * @return le DTO de réponse contenant l'employé créé et le mot de passe temporaire
     * @throws IllegalArgumentException si un employé avec le même email existe déjà
     * @throws RuntimeException         si le provisionnement Keycloak ou la sauvegarde Oracle échoue
     */
    @Transactional
    public CreateEmployeeResponse createEmployee(CreateEmployeeRequest request) {

        // Check for email conflict
        Optional<Employee> existingOpt = employeeRepository.findByEmail(request.getEmail());
        if (existingOpt.isPresent()) {
            Employee existing = existingOpt.get();

            // Verify whether a Keycloak user actually exists for this email
            String kcId = null;
            try {
                kcId = keycloakUserService.findUserIdByEmail(request.getEmail());
            } catch (Exception e) {
                log.warn("Could not verify Keycloak for {}: {}", request.getEmail(), e.getMessage());
            }

            if (kcId != null) {
                // Fully set up — reject as a real duplicate
                throw new IllegalArgumentException(
                        "Employee with email " + request.getEmail() + " already exists");
            }

            // Orphaned: DB record exists but Keycloak user is gone — re-provision KC
            log.info("Orphaned employee {} (id={}) — re-provisioning Keycloak account",
                     request.getEmail(), existing.getId());
            String tempPwd = passwordGeneratorService.generate();
            String username = (request.getUsername() != null && !request.getUsername().isBlank())
                    ? request.getUsername()
                    : buildUsername(existing.getFirstName(), existing.getLastName());
            try {
                var result = keycloakUserService.provisionKeycloakUser(
                        username, existing.getEmail(),
                        existing.getFirstName(), existing.getLastName(), tempPwd);

                if (request.getRoles() != null && !request.getRoles().isEmpty()) {
                    try { keycloakUserService.assignRealmRoles(result.userId(), request.getRoles()); }
                    catch (Exception roleEx) {
                        log.warn("Role assignment failed for orphan {}: {}", result.userId(), roleEx.getMessage());
                    }
                }

                existing.setKeycloakUserId(result.userId());
                Employee saved = employeeRepository.save(existing);
                log.info("Orphaned employee {} linked to new Keycloak user {}", saved.getId(), result.userId());

                try { employeeEventProducer.notifierNouvelEmploye(saved); } catch (Exception ignored) {}

                emailService.sendTemporaryPassword(
                        saved.getEmail(), saved.getFirstName(), result.username(), tempPwd);

                return CreateEmployeeResponse.builder()
                        .id(saved.getId())
                        .employeeCode(saved.getEmployeeCode())
                        .username(result.username())
                        .firstName(saved.getFirstName())
                        .lastName(saved.getLastName())
                        .email(saved.getEmail())
                        .hireDate(saved.getHireDate())
                        .deptId(saved.getDeptId())
                        .positionId(saved.getPositionId())
                        .managerId(saved.getManagerId())
                        .status(saved.getStatus())
                        .keycloakUserId(saved.getKeycloakUserId())
                        .temporaryPassword(tempPwd)
                        .createdAt(saved.getCreatedAt())
                        .build();

            } catch (Exception ex) {
                log.error("KC re-provisioning failed for orphaned employee {}: {}",
                          request.getEmail(), ex.getMessage());
                throw new RuntimeException("Keycloak provisioning failed: " + ex.getMessage(), ex);
            }
        }

        // 1. Generate secure temporary password
        String tempPassword = passwordGeneratorService.generate();

        // 2. Provision Keycloak user:
        //    - If the email already has a Keycloak account → reuse their ID and reset password
        //    - Otherwise → create a new account
        String  keycloakUserId;
        boolean keycloakIsNew;
        String  keycloakUsername;
        try {
            var result = keycloakUserService.provisionKeycloakUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    tempPassword
            );
            keycloakUserId   = result.userId();
            keycloakIsNew    = result.isNew();
            keycloakUsername = result.username();
            log.info("Keycloak user {} (newly created: {})", keycloakUserId, keycloakIsNew);

            // Assign only the roles explicitly selected during creation
            if (request.getRoles() != null && !request.getRoles().isEmpty()) {
                try {
                    keycloakUserService.assignRealmRoles(keycloakUserId, request.getRoles());
                } catch (Exception roleEx) {
                    log.warn("Role assignment failed for {} — user created but may lack roles: {}",
                            keycloakUserId, roleEx.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Keycloak provisioning failed for {}: {}", request.getEmail(), ex.getMessage());
            throw new RuntimeException("Keycloak provisioning failed: " + ex.getMessage(), ex);
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

            // Notify admins of the new employee (fire-and-forget)
            try { employeeEventProducer.notifierNouvelEmploye(saved); } catch (Exception ignored) {}

            // Send temp password via email — never store it
            emailService.sendTemporaryPassword(
                    saved.getEmail(),
                    saved.getFirstName(),
                    keycloakUsername,
                    tempPassword
            );

            return CreateEmployeeResponse.builder()
                    .id(saved.getId())                   // Long Oracle IDENTITY
                    .employeeCode(saved.getEmployeeCode())
                    .username(keycloakUsername)
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
            if (keycloakIsNew) {
                log.error("DB save failed, rolling back newly created Keycloak user {}: {}", keycloakUserId, ex.getMessage());
                try {
                    keycloakUserService.deleteKeycloakUser(keycloakUserId);
                    log.info("Keycloak user {} rolled back successfully", keycloakUserId);
                } catch (Exception rollbackEx) {
                    log.error("Keycloak rollback failed for {}: {}", keycloakUserId, rollbackEx.getMessage());
                }
                throw new RuntimeException("Employee save failed, Keycloak user rolled back: " + ex.getMessage(), ex);
            }
            log.error("DB save failed for employee {} (pre-existing Keycloak user kept): {}", request.getEmail(), ex.getMessage());
            throw new RuntimeException("Employee save failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Retourne la liste de tous les employés (tous statuts confondus).
     *
     * @return la liste complète des employés en base Oracle
     */
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    /**
     * Retourne un employé par son identifiant Oracle.
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @return l'entité {@link Employee} correspondante
     * @throws RuntimeException si aucun employé n'est trouvé avec cet identifiant
     */
    public Employee getEmployeeById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
    }

    /**
     * Met à jour les informations d'un employé existant en base Oracle.
     * Le UUID Keycloak ({@code keycloakUserId}) n'est jamais modifié par cette méthode.
     *
     * @param id      l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé à modifier
     * @param request les nouvelles valeurs des champs de l'employé
     * @return l'entité {@link Employee} mise à jour et persistée
     * @throws RuntimeException si l'employé est introuvable
     */
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

    /**
     * Supprime un employé : désactive d'abord son compte Keycloak, puis tente une suppression
     * physique en base Oracle. En cas d'échec dû à des contraintes de clé étrangère,
     * effectue un soft-delete en passant le statut à {@code DEMISSION}.
     * Publie un événement Kafka dans les deux cas.
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé à supprimer
     * @throws RuntimeException si l'employé est introuvable
     */
    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = getEmployeeById(id);

        // 1. Disable the Keycloak account — don't fail if Keycloak is unreachable
        //    or the user was already removed
        String kcId = employee.getKeycloakUserId();
        if (kcId != null && !kcId.isBlank()) {
            try {
                keycloakUserService.deleteKeycloakUser(kcId);
                log.info("Keycloak user deleted: {}", kcId);
            } catch (Exception ex) {
                log.warn("Keycloak deletion skipped for {} ({}): {}", kcId, id, ex.getMessage());
            }
        }

        // 2. Try hard delete; fall back to soft delete if FK constraints block it
        try {
            employeeRepository.delete(employee);
            log.info("Employee hard-deleted from DB: {}", id);
            try { employeeEventProducer.notifierDepartEmploye(employee); } catch (Exception ignored) {}
        } catch (Exception ex) {
            log.warn("Hard delete blocked for employee {} (FK constraint?), soft-deleting: {}", id, ex.getMessage());
            employee.setStatus("DEMISSION");
            employeeRepository.save(employee);
            log.info("Employee soft-deleted (STATUS=DEMISSION): {}", id);
            try { employeeEventProducer.notifierDepartEmploye(employee); } catch (Exception ignored) {}
        }
    }

    /**
     * Recherche un employé par son adresse email exacte.
     *
     * @param email l'adresse email à rechercher
     * @return un {@link Optional} contenant l'employé si trouvé, ou vide sinon
     */
    public Optional<Employee> getEmployeeByEmail(String email) {
        return employeeRepository.findByEmail(email);
    }

    /**
     * Recherche des employés par texte libre (prénom, nom ou email, insensible à la casse).
     *
     * @param q le texte à rechercher
     * @return la liste des employés correspondants
     */
    public List<Employee> searchByQuery(String q) {
        return employeeRepository.searchByQuery(q);
    }

    /**
     * Construit un nom d'utilisateur Keycloak normalisé au format {@code prenom.nom} :
     * suppression des accents, passage en minuscules, remplacement des espaces par des points.
     *
     * @param firstName le prénom de l'employé (peut être {@code null})
     * @param lastName  le nom de famille de l'employé (peut être {@code null})
     * @return le nom d'utilisateur Keycloak normalisé
     */
    private String buildUsername(String firstName, String lastName) {
        java.util.function.Function<String, String> norm = s ->
            java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase()
                .replaceAll("\\s+", ".");
        return norm.apply(firstName != null ? firstName : "") + "." +
               norm.apply(lastName  != null ? lastName  : "");
    }

    /**
     * Génère un matricule employé unique au format {@code EMP-XXXX} (padé sur 4 chiffres).
     * Vérifie l'unicité en base et tente jusqu'à 1000 codes différents en cas de collision.
     *
     * @return un matricule employé garanti unique en base Oracle
     */
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