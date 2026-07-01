package com.gerai.projetsservice.controller;

import com.gerai.projetsservice.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Contrôleur REST pour le téléversement de CV des candidats.
 * <p>
 * Expose l'endpoint {@code POST /api/admin/candidates/{id}/cv} permettant
 * d'envoyer un fichier CV (PDF ou autre) pour un candidat. Le fichier est
 * sauvegardé dans le répertoire configuré et l'URL d'accès est persistée
 * sur l'entité {@link com.gerai.projetsservice.model.Candidate}.
 * </p>
 * <p>
 * {@code @RestController} : contrôleur REST retournant du JSON.
 * </p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/admin/candidates")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class CvUploadController {

    /** Repository JPA pour la mise à jour de l'URL du CV du candidat. */
    private final CandidateRepository repo;

    /** Répertoire du système de fichiers où les CVs téléversés sont stockés. */
    @Value("${app.cv.upload-dir:${user.home}/synapse-cvs}")
    private String uploadDir;

    /** URL de base publique pour accéder aux CVs téléversés. */
    @Value("${app.cv.base-url:http://localhost:8087/cvs}")
    private String baseUrl;

    /**
     * Téléverse un CV pour un candidat et met à jour son URL dans la base de données.
     * <p>
     * Le nom du fichier est généré de façon unique : {@code cv_{id}_{uuid8}{extension}}.
     * </p>
     *
     * @param id   identifiant du candidat
     * @param file fichier CV à téléverser (multipart/form-data, champ {@code file})
     * @return map {@code {url}} avec l'URL publique du CV (HTTP 200), HTTP 404 si candidat introuvable
     * @throws IOException en cas d'erreur lors de la création du répertoire ou de l'écriture du fichier
     */
    @PostMapping("/{id}/cv")
    public ResponseEntity<Map<String, String>> uploadCv(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {

        return repo.findById(id).map(c -> {
            try {
                Path dir = Paths.get(uploadDir);
                Files.createDirectories(dir);

                String ext      = getExtension(file.getOriginalFilename());
                String filename = "cv_" + id + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
                Path   target   = dir.resolve(filename);
                file.transferTo(target);

                String url = baseUrl + "/" + filename;
                c.setCvUrl(url);
                repo.save(c);

                return ResponseEntity.ok(Map.of("url", url));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Extrait l'extension d'un nom de fichier.
     *
     * @param filename nom du fichier original (peut être {@code null})
     * @return l'extension avec le point (ex. {@code .pdf}), ou {@code .pdf} par défaut
     */
    private String getExtension(String filename) {
        if (filename == null) return ".pdf";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".pdf";
    }
}
