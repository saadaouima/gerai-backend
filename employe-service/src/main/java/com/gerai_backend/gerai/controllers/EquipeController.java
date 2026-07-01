package com.gerai_backend.gerai.controllers;

import com.gerai_backend.gerai.dto.MembreEquipeDTO;
import com.gerai_backend.gerai.models.Employee;
import com.gerai_backend.gerai.repositories.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Contrôleur REST exposant les opérations CRUD sur les membres de l'équipe du chef connecté.
 * Permet au chef d'équipe de consulter, ajouter, modifier et retirer des membres de son équipe.
 *
 * <p>@RestController : sérialise toutes les réponses en JSON.</p>
 * <p>Route de base : {@code /equipe} — avec {@code server.servlet.context-path=/api},
 * les routes répondent sur {@code /api/equipe} et sont proxifiées depuis Angular
 * via {@code /api/equipe → http://localhost:8081}.</p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/equipe")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EquipeController {

    private final EmployeeRepository employeeRepository;
    private final JdbcTemplate       jdbcTemplate;

    /**
     * Retourne la liste des membres de l'équipe du chef connecté.
     * Si l'utilisateur authentifié n'est pas trouvé en base, retourne tous les employés.
     *
     * @param auth l'authentification JWT de l'utilisateur connecté
     * @return une réponse HTTP 200 avec la liste des membres de l'équipe au format DTO
     */
    @GetMapping("/membres")
    public ResponseEntity<List<MembreEquipeDTO>> getMembres(Authentication auth) {
        try {
            Long managerId = resolveManagerId(auth);
            List<Employee> membres = List.of();
            if (managerId != null) {
                membres = employeeRepository.findByManagerId(managerId);
            }
            if (membres.isEmpty()) {
                membres = employeeRepository.findAll();
            }
            Map<Long, String> positionTitles = loadPositionTitles();
            final List<Employee> finalMembres = membres;
            return ResponseEntity.ok(finalMembres.stream()
                    .map(e -> toDTO(e, positionTitles))
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("Failed to fetch team members: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    private Map<Long, String> loadPositionTitles() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT POSITION_ID, TITLE FROM POSITIONS");
            Map<Long, String> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Object id    = row.get("POSITION_ID");
                Object title = row.get("TITLE");
                if (id != null && title != null) {
                    map.put(((Number) id).longValue(), title.toString());
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("Could not load POSITIONS table: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Retourne les informations d'un membre de l'équipe par son identifiant.
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) du membre
     * @return une réponse HTTP 200 avec le DTO du membre, ou 404 si introuvable
     */
    @GetMapping("/membres/{id}")
    public ResponseEntity<MembreEquipeDTO> getMembreById(@PathVariable Long id) {
        Map<Long, String> positionTitles = loadPositionTitles();
        return employeeRepository.findById(id)
                .map(e -> ResponseEntity.ok(toDTO(e, positionTitles)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Ajoute un nouveau membre à l'équipe du chef connecté.
     * Le département et le poste sont hérités du manager authentifié.
     *
     * @param body les données du nouveau membre ({@code email}, {@code prenom}, {@code nom} obligatoires)
     * @param auth l'authentification JWT du chef d'équipe connecté
     * @return une réponse HTTP 201 avec le DTO du membre créé,
     *         400 si des champs requis manquent, ou 409 si l'email existe déjà
     */
    @PostMapping("/membres")
    public ResponseEntity<MembreEquipeDTO> addMembre(
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        String email     = getStr(body, "email");
        String firstName = getStr(body, "prenom");
        String lastName  = getStr(body, "nom");

        if (email == null || firstName == null || lastName == null) {
            return ResponseEntity.badRequest().build();
        }
        if (employeeRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Employee manager = resolveManagerEntity(auth);
        Long managerId   = manager != null ? manager.getId()        : null;
        Long deptId      = manager != null ? manager.getDeptId()    : 1L;
        Long positionId  = manager != null ? manager.getPositionId(): 1L;
        if (deptId     == null) deptId     = 1L;
        if (positionId == null) positionId = 1L;

        String avatar = getStr(body, "avatar") != null ? getStr(body, "avatar") : getStr(body, "photo");

        Employee emp = Employee.builder()
                .keycloakUserId(UUID.randomUUID().toString())
                .employeeCode(generateCode())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(getStr(body, "telephone"))
                .photoUrl(avatar)
                .deptId(deptId)
                .positionId(positionId)
                .managerId(managerId)
                .hireDate(LocalDate.now())
                .status("ACTIF")
                .build();

        try {
            emp = employeeRepository.save(emp);
            return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(emp, loadPositionTitles()));
        } catch (Exception ex) {
            log.error("addMembre failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Met à jour partiellement les informations d'un membre de l'équipe.
     * Seuls les champs présents dans le corps de la requête sont modifiés.
     *
     * @param id   l'identifiant Oracle ({@code EMPLOYEE_ID}) du membre à modifier
     * @param body les champs à modifier ({@code nom}, {@code prenom}, {@code telephone},
     *             {@code avatar}, {@code statut}, etc.)
     * @return une réponse HTTP 200 avec le DTO mis à jour, ou 404 si le membre est introuvable
     */
    @PatchMapping("/membres/{id}")
    public ResponseEntity<MembreEquipeDTO> updateMembre(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return employeeRepository.findById(id)
                .map(emp -> {
                    if (body.containsKey("nom"))       emp.setLastName(getStr(body, "nom"));
                    if (body.containsKey("prenom"))    emp.setFirstName(getStr(body, "prenom"));
                    if (body.containsKey("telephone")) emp.setPhone(getStr(body, "telephone"));
                    if (body.containsKey("phone"))     emp.setPhone(getStr(body, "phone"));
                    if (body.containsKey("avatar"))    emp.setPhotoUrl(getStr(body, "avatar"));
                    if (body.containsKey("photoUrl"))  emp.setPhotoUrl(getStr(body, "photoUrl"));
                    if (body.containsKey("statut")) {
                        String s = getStr(body, "statut");
                        emp.setStatus("ACTIF".equals(s) ? "ACTIF" : "INACTIF");
                    }
                    return ResponseEntity.ok(toDTO(employeeRepository.save(emp), loadPositionTitles()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retire un membre de l'équipe (soft-delete) : passe son statut à {@code INACTIF}
     * et supprime le lien avec le manager.
     *
     * @param id l'identifiant Oracle ({@code EMPLOYEE_ID}) du membre à retirer
     * @return une réponse HTTP 204 No Content si réussi, ou 404 si le membre est introuvable
     */
    @DeleteMapping("/membres/{id}")
    public ResponseEntity<Void> removeMembre(@PathVariable Long id) {
        return employeeRepository.findById(id)
                .map(emp -> {
                    emp.setStatus("INACTIF");
                    emp.setManagerId(null);
                    employeeRepository.save(emp);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Résout l'identifiant Oracle du manager à partir du token JWT de l'utilisateur connecté.
     *
     * @param auth l'authentification Spring Security (doit contenir un {@link org.springframework.security.oauth2.jwt.Jwt})
     * @return l'identifiant Oracle du manager, ou {@code null} si non trouvé
     */
    private Long resolveManagerId(Authentication auth) {
        Employee mgr = resolveManagerEntity(auth);
        return mgr != null ? mgr.getId() : null;
    }

    /**
     * Résout l'entité {@link com.gerai_backend.gerai.models.Employee} du manager
     * à partir du sujet ({@code sub}) du token JWT Keycloak.
     *
     * @param auth l'authentification Spring Security (doit contenir un {@link org.springframework.security.oauth2.jwt.Jwt})
     * @return l'entité {@link com.gerai_backend.gerai.models.Employee} correspondant
     *         au manager connecté, ou {@code null} si non trouvé ou non authentifié
     */
    private Employee resolveManagerEntity(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return null;
        try {
            return employeeRepository.findByKeycloakUserId(jwt.getSubject()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Génère un code matricule unique pour un nouveau membre de l'équipe
     * au format {@code EMP-XXXX}, en s'assurant de l'absence de doublon en base.
     *
     * @return le code matricule généré et garanti unique
     */
    private String generateCode() {
        long count = employeeRepository.count() + 1;
        String code;
        int attempt = 0;
        do {
            code = String.format("EMP-%04d", count + attempt);
            attempt++;
        } while (employeeRepository.existsByEmployeeCode(code) && attempt < 1000);
        return code;
    }

    /**
     * Extrait une valeur de type {@code String} depuis un corps de requête JSON.
     *
     * @param body le corps de la requête sous forme de {@code Map}
     * @param key  la clé à rechercher
     * @return la valeur en {@code String} si elle existe et est une chaîne, sinon {@code null}
     */
    private String getStr(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val instanceof String s ? s : null;
    }

    /**
     * Convertit une entité {@link com.gerai_backend.gerai.models.Employee}
     * en {@link MembreEquipeDTO} pour l'envoi au frontend Angular.
     *
     * @param e l'entité employé à convertir
     * @return le DTO correspondant avec les informations formatées pour l'affichage
     */
    private MembreEquipeDTO toDTO(Employee e, Map<Long, String> positionTitles) {
        String poste = e.getPositionId() != null
                ? positionTitles.getOrDefault(e.getPositionId(), "—")
                : "—";
        return MembreEquipeDTO.builder()
                .id(e.getId())
                .keycloakId(e.getKeycloakUserId())
                .nom(e.getLastName())
                .prenom(e.getFirstName())
                .email(e.getEmail())
                .poste(poste)
                .departement(e.getDeptId() != null ? "Dept " + e.getDeptId() : null)
                .telephone(e.getPhone())
                .statut(mapStatut(e.getStatus()))
                .dateEmbauche(e.getHireDate() != null ? e.getHireDate().toString() : null)
                .present("ACTIF".equals(e.getStatus()))
                .avatar(e.getPhotoUrl())
                .build();
    }

    /**
     * Mappe le statut Oracle d'un employé vers les valeurs attendues par le frontend Angular.
     *
     * @param status le statut Oracle ({@code ACTIF}, {@code INACTIF}, {@code SUSPENDU}, {@code DEMISSION})
     * @return {@code "ACTIF"} si l'employé est actif, {@code "INACTIF"} dans tous les autres cas
     */
    private String mapStatut(String status) {
        if (status == null) return "INACTIF";
        return switch (status) {
            case "ACTIF"     -> "ACTIF";
            case "CONGE"     -> "CONGE";
            case "INACTIF",
                 "SUSPENDU",
                 "DEMISSION" -> "INACTIF";
            default           -> "INACTIF";
        };
    }
}
