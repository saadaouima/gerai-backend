package com.gerai_backend.gerai.controllers;

import com.gerai_backend.gerai.models.Employee;
import com.gerai_backend.gerai.repositories.EmployeeRepository;
import com.gerai_backend.gerai.services.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

/**
 * Contrôleur REST exposant les endpoints de l'espace personnel de l'employé connecté.
 * Gère le profil, les statistiques, les performances, les documents et la photo de l'employé.
 *
 * <p>@RestController : sérialise toutes les réponses en JSON.</p>
 * <p>Route de base : {@code /employe} — avec {@code server.servlet.context-path=/api},
 * les routes répondent sur {@code /api/employe/*} et sont proxifiées depuis Angular
 * via {@code /api/employe → http://localhost:8081}.</p>
 *
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/employe")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProfilEmployeController {

    private final EmployeeRepository employeeRepository;
    private final DocumentService    documentService;

    @Value("${app.upload.dir:C:/gerai/uploads/employe}")
    private String uploadDir;

    /**
     * Synchronise le profil de l'employé connecté avec la base Oracle à partir des claims JWT.
     * Crée un enregistrement {@link com.gerai_backend.gerai.models.Employee} si aucun n'existe,
     * ou rattache le UUID Keycloak à un enregistrement existant par email.
     * Opération idempotente — peut être appelée à chaque chargement de la page profil.
     *
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 (profil existant) ou 201 (profil créé) avec la map de profil,
     *         401 si non authentifié, ou 500 si la création échoue
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncProfil(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String sub       = jwt.getSubject();
        String email     = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName  = jwt.getClaimAsString("family_name");

        // 1. Already linked by Keycloak UUID → return as-is
        Optional<Employee> byKcId = employeeRepository.findByKeycloakUserId(sub);
        if (byKcId.isPresent()) {
            return ResponseEntity.ok(buildProfileMap(byKcId.get()));
        }

        // 2. Employee exists with the same email (created manually) → link the Keycloak UUID
        if (email != null && !email.isBlank()) {
            Optional<Employee> byEmail = employeeRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                Employee emp = byEmail.get();
                emp.setKeycloakUserId(sub);
                emp = employeeRepository.save(emp);
                log.info("Linked existing employee {} to Keycloak sub {}", emp.getId(), sub);
                return ResponseEntity.ok(buildProfileMap(emp));
            }
        }

        // 3. No record at all → create a minimal one from token claims
        long count = employeeRepository.count() + 1;
        String code = String.format("EMP-%04d", count);

        Employee newEmp = Employee.builder()
                .keycloakUserId(sub)
                .employeeCode(code)
                .firstName(firstName != null && !firstName.isBlank() ? firstName : "Utilisateur")
                .lastName(lastName  != null && !lastName.isBlank()  ? lastName  : "")
                .email(email != null && !email.isBlank() ? email : sub + "@synapse.local")
                .deptId(1L)
                .positionId(1L)
                .hireDate(LocalDate.now())
                .status("ACTIF")
                .build();

        try {
            newEmp = employeeRepository.save(newEmp);
            log.info("Auto-created employee {} for Keycloak sub {}", newEmp.getId(), sub);
            return ResponseEntity.status(HttpStatus.CREATED).body(buildProfileMap(newEmp));
        } catch (Exception ex) {
            log.error("sync: could not create employee row: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Impossible de créer le profil: " + ex.getMessage()));
        }
    }

    /**
     * Met à jour les informations modifiables du profil de l'employé connecté
     * (téléphone, adresse, photo).
     *
     * @param updates une map des champs à modifier ({@code telephone}, {@code adresse},
     *                {@code photoUrl}, {@code avatar})
     * @param auth    l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec le profil mis à jour, ou 404 si l'employé n'est pas trouvé
     */
    @PutMapping("/profil")
    public ResponseEntity<Map<String, Object>> updateProfil(
            @RequestBody Map<String, String> updates,
            Authentication auth) {
        Optional<Employee> empOpt = resolveEmployee(auth);
        if (empOpt.isEmpty()) return ResponseEntity.notFound().build();

        Employee emp = empOpt.get();
        if (updates.containsKey("telephone")) emp.setPhone(updates.get("telephone"));
        if (updates.containsKey("phone"))     emp.setPhone(updates.get("phone"));
        if (updates.containsKey("adresse"))   emp.setAddress(updates.get("adresse"));
        if (updates.containsKey("address"))   emp.setAddress(updates.get("address"));
        if (updates.containsKey("photoUrl"))  emp.setPhotoUrl(updates.get("photoUrl"));
        if (updates.containsKey("avatar"))    emp.setPhotoUrl(updates.get("avatar"));

        emp = employeeRepository.save(emp);
        return ResponseEntity.ok(buildProfileMap(emp));
    }

    /**
     * Retourne les informations du profil de l'employé connecté.
     * Si l'employé n'est pas trouvé en base, retourne une map vide avec les champs minimaux.
     *
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec la map de profil de l'employé
     */
    @GetMapping("/profil")
    public ResponseEntity<Map<String, Object>> getProfil(Authentication auth) {
        Optional<Employee> emp = resolveEmployee(auth);
        Map<String, Object> result = emp.isPresent()
                ? buildProfileMap(emp.get())
                : Map.of("nom", "", "prenom", "", "nomComplet", "", "email", "");
        return ResponseEntity.ok(result);
    }

    /**
     * Retourne les statistiques de l'employé connecté (taux de présence, objectifs,
     * formations, tâches, congés). Valeurs par défaut à 0 — à implémenter avec les données réelles.
     *
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec la map des statistiques de l'employé
     */
    @GetMapping("/statistiques")
    public ResponseEntity<Map<String, Object>> getStatistiques(Authentication auth) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("tauxPresence", 0);
        stats.put("objectifsAtteints", 0);
        stats.put("formationsSuivies", 0);
        stats.put("formationsTotal", 0);
        stats.put("tachesCompletes", 0);
        stats.put("tachesTotal", 0);
        stats.put("congesRestants", 0);
        stats.put("congesTotal", 30);
        return ResponseEntity.ok(stats);
    }

    /**
     * Retourne les indicateurs de performance de l'employé connecté (score, taux de complétion,
     * ponctualité, qualité). Valeurs par défaut à 0 — à implémenter avec les données réelles.
     *
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec la map des indicateurs de performance
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformance(Authentication auth) {
        Map<String, Object> perf = new LinkedHashMap<>();
        perf.put("score", 0);
        perf.put("tauxCompletion", 0);
        perf.put("ponctualite", 0);
        perf.put("qualite", 0);
        perf.put("commentaire", "");
        return ResponseEntity.ok(perf);
    }

    /**
     * Retourne la liste des documents personnels de l'employé connecté,
     * triés par date d'ajout décroissante.
     *
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec la liste des documents, ou une liste vide si non authentifié
     */
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> getDocuments(Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        if (empId == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(documentService.getDocuments(empId));
    }

    /**
     * Téléverse un nouveau document personnel pour l'employé connecté.
     * Le fichier est stocké sur le système de fichiers du serveur.
     *
     * @param file le fichier à téléverser (multipart/form-data)
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 201 avec les métadonnées du document créé,
     *         401 si non authentifié, 400 si le fichier est vide, ou 500 en cas d'erreur I/O
     */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        if (empId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(documentService.uploadDocument(empId, file));
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Supprime un document personnel de l'employé connecté (fichier + métadonnée en base).
     *
     * @param id   l'identifiant du document à supprimer ({@code DOC_ID})
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 204 No Content si supprimé, 401 si non authentifié,
     *         ou 404 si le document n'appartient pas à l'employé
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id, Authentication auth) {
        Long empId = resolveEmployeeId(auth);
        if (empId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            documentService.deleteDocument(id, empId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Sert un document en mode consultation (en ligne) pour l'employé connecté.
     * Utilise l'en-tête {@code Content-Disposition: inline}.
     *
     * @param id   l'identifiant du document ({@code DOC_ID})
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec le contenu du fichier en streaming,
     *         401 si non authentifié, ou 404 si le document est introuvable
     */
    @GetMapping("/documents/{id}/view")
    public ResponseEntity<Resource> viewDocument(@PathVariable Long id, Authentication auth) {
        return serveDocument(id, auth, false);
    }

    /**
     * Sert un document en mode téléchargement pour l'employé connecté.
     * Utilise l'en-tête {@code Content-Disposition: attachment}.
     *
     * @param id   l'identifiant du document ({@code DOC_ID})
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec le contenu du fichier en pièce jointe,
     *         401 si non authentifié, ou 404 si le document est introuvable
     */
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id, Authentication auth) {
        return serveDocument(id, auth, true);
    }

    /**
     * Méthode interne pour servir un document en streaming HTTP,
     * avec gestion de l'en-tête {@code Content-Disposition} selon le mode.
     *
     * @param id         l'identifiant du document ({@code DOC_ID})
     * @param auth       l'authentification JWT de l'employé connecté
     * @param attachment {@code true} pour un téléchargement, {@code false} pour une consultation en ligne
     * @return une réponse HTTP avec le contenu du fichier,
     *         401 si non authentifié, 404 si introuvable, ou 500 en cas d'erreur I/O
     */
    private ResponseEntity<Resource> serveDocument(Long id, Authentication auth, boolean attachment) {
        Long empId = resolveEmployeeId(auth);
        if (empId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            Resource resource    = documentService.loadAsResource(id, empId);
            String  contentType  = documentService.getContentType(id, empId);
            String  filename     = documentService.getFilename(id, empId);
            String  disposition  = attachment
                    ? "attachment; filename=\"" + filename + "\""
                    : "inline; filename=\"" + filename + "\"";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("serveDocument failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Téléverse et enregistre la photo de profil de l'employé connecté.
     * Le fichier est sauvegardé sous {@code {uploadDir}/photos/{employeeId}.{ext}}
     * et l'URL publique est mise à jour dans la base Oracle.
     *
     * @param file le fichier image à téléverser (multipart/form-data, champ {@code photo})
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec l'URL de la photo stockée,
     *         401 si non authentifié, 400 si le fichier est vide, ou 500 en cas d'erreur I/O
     */
    @PostMapping(value = "/profil/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadPhoto(
            @RequestParam("photo") MultipartFile file,
            Authentication auth) {
        Optional<Employee> empOpt = resolveEmployee(auth);
        if (empOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (file.isEmpty())   return ResponseEntity.badRequest().build();

        Employee emp = empOpt.get();
        try {
            String ext      = getExtension(file.getOriginalFilename());
            Path   photoDir = Paths.get(uploadDir, "photos");
            Files.createDirectories(photoDir);
            Path dest = photoDir.resolve(emp.getId() + "." + ext);
            Files.write(dest, file.getBytes());

            String photoUrl = "/api/employe/profil/photo/" + emp.getId();
            emp.setPhotoUrl(photoUrl);
            employeeRepository.save(emp);
            log.info("Photo uploaded for employee {}: {}", emp.getId(), dest);
            return ResponseEntity.ok(Map.of("photoUrl", photoUrl));
        } catch (Exception e) {
            log.error("Photo upload failed for employee {}: {}", emp.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Sert la photo de profil d'un employé par son identifiant.
     * Recherche les extensions courantes (jpg, jpeg, png, gif, webp).
     * Accessible sans authentification (route publique configurée dans {@link SecurityConfig}).
     *
     * @param empId l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé
     * @return une réponse HTTP 200 avec l'image en streaming et cache de 24h,
     *         ou 404 si aucune photo n'est trouvée
     */
    @GetMapping("/profil/photo/{empId}")
    public ResponseEntity<Resource> servePhoto(@PathVariable Long empId) {
        for (String ext : List.of("jpg", "jpeg", "png", "gif", "webp")) {
            Path file = Paths.get(uploadDir, "photos", empId + "." + ext);
            if (Files.exists(file)) {
                try {
                    Resource res = new UrlResource(file.toUri());
                    String   ct  = Files.probeContentType(file);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                            .contentType(MediaType.parseMediaType(ct != null ? ct : "image/jpeg"))
                            .body(res);
                } catch (Exception e) {
                    log.error("Could not serve photo {}: {}", empId, e.getMessage());
                }
            }
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Extrait l'extension d'un nom de fichier.
     *
     * @param filename le nom original du fichier (peut être {@code null})
     * @return l'extension en minuscules (ex. {@code jpg}), ou {@code "jpg"} par défaut
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Retourne la liste des activités récentes de l'employé connecté.
     * Retourne une liste vide — à implémenter avec les données réelles d'activité.
     *
     * @param auth l'authentification JWT de l'employé connecté
     * @return une réponse HTTP 200 avec une liste vide
     */
    @GetMapping("/activites")
    public ResponseEntity<List<Object>> getActivites(Authentication auth) {
        return ResponseEntity.ok(List.of());
    }

    /**
     * Construit la map de profil de l'employé au format attendu par le frontend Angular.
     *
     * @param e l'entité {@link com.gerai_backend.gerai.models.Employee} à sérialiser
     * @return une {@code Map} avec les clés Angular ({@code id}, {@code dbId}, {@code nom},
     *         {@code prenom}, {@code email}, {@code matricule}, etc.)
     */
    private Map<String, Object> buildProfileMap(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          e.getKeycloakUserId());
        m.put("dbId",        e.getId());
        m.put("managerId",   e.getManagerId());
        m.put("nom",         e.getLastName());
        m.put("prenom",      e.getFirstName());
        m.put("nomComplet",  e.getFirstName() + " " + e.getLastName());
        m.put("email",       e.getEmail());
        m.put("telephone",   e.getPhone() != null ? e.getPhone() : "");
        m.put("matricule",   e.getEmployeeCode());
        m.put("statut",      e.getStatus());
        m.put("dateEmbauche",e.getHireDate() != null ? e.getHireDate().toString() : "");
        m.put("photo",       e.getPhotoUrl());
        return m;
    }

    /**
     * Résout l'entité {@link com.gerai_backend.gerai.models.Employee} de l'utilisateur connecté
     * à partir du sujet ({@code sub}) du token JWT Keycloak.
     *
     * @param auth l'authentification Spring Security (doit contenir un {@link org.springframework.security.oauth2.jwt.Jwt})
     * @return un {@link Optional} contenant l'employé si trouvé, ou vide sinon
     */
    private Optional<Employee> resolveEmployee(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return Optional.empty();
        try {
            return employeeRepository.findByKeycloakUserId(jwt.getSubject());
        } catch (Exception e) {
            log.warn("Could not resolve employee from JWT sub: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Résout l'identifiant Oracle ({@code EMPLOYEE_ID}) de l'employé connecté
     * à partir de son token JWT.
     *
     * @param auth l'authentification JWT de l'utilisateur connecté
     * @return l'identifiant Oracle de l'employé, ou {@code null} si non trouvé
     */
    private Long resolveEmployeeId(Authentication auth) {
        return resolveEmployee(auth).map(Employee::getId).orElse(null);
    }
}
