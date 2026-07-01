package com.gerai_backend.gerai.services;

import com.gerai_backend.gerai.models.EmployeeDocument;
import com.gerai_backend.gerai.repositories.EmployeeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service gérant les opérations sur les documents personnels des employés :
 * téléversement, récupération, suppression et streaming HTTP.
 *
 * <p>@Service : enregistré comme bean Spring et injecté dans
 * {@link com.gerai_backend.gerai.controllers.ProfilEmployeController}.</p>
 * <p>Les fichiers sont stockés sur le système de fichiers du serveur
 * dans le répertoire configuré par {@code app.upload.dir}.</p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final EmployeeDocumentRepository documentRepo;

    /** Répertoire racine de stockage des fichiers téléversés. */
    @Value("${app.upload.dir:C:/gerai/uploads/employe}")
    private String uploadDir;

    /**
     * Retourne la liste des documents d'un employé triés par date d'ajout décroissante.
     *
     * @param employeeId l'identifiant Oracle de l'employé
     * @return une liste de maps DTO ({@code id}, {@code nom}, {@code type}, {@code taille}, {@code dateAjout})
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDocuments(Long employeeId) {
        return documentRepo.findByEmployeeIdOrderByDateAjoutDesc(employeeId)
                .stream().map(this::toMap).toList();
    }

    /**
     * Téléverse un fichier sur le système de fichiers du serveur et persiste
     * les métadonnées du document en base Oracle.
     * Le fichier est stocké sous un nom unique (UUID + nom original sanitisé) dans
     * un sous-répertoire {@code {uploadDir}/{employeeId}/}.
     *
     * @param employeeId l'identifiant Oracle de l'employé propriétaire
     * @param file       le fichier téléversé (multipart/form-data)
     * @return une map DTO avec les métadonnées du document créé
     * @throws IOException en cas d'erreur d'écriture sur le système de fichiers
     */
    @Transactional
    public Map<String, Object> uploadDocument(Long employeeId, MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "document";

        // Unique filename on disk to avoid collisions
        String stored = UUID.randomUUID() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path dir = Paths.get(uploadDir, String.valueOf(employeeId));
        Files.createDirectories(dir);
        Path dest = dir.resolve(stored);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        EmployeeDocument doc = EmployeeDocument.builder()
                .employeeId(employeeId)
                .nom(originalName)
                .typeFichier(file.getContentType())
                .taille(file.getSize())
                .cheminFichier(dest.toAbsolutePath().toString())
                .build();

        doc = documentRepo.save(doc);
        log.info("Document uploaded: {} → {}", originalName, dest);
        return toMap(doc);
    }

    /**
     * Supprime le fichier physique et la métadonnée en base Oracle.
     * Vérifie que l'employé est bien le propriétaire du document avant suppression.
     *
     * @param docId      l'identifiant Oracle du document ({@code DOC_ID})
     * @param employeeId l'identifiant Oracle de l'employé propriétaire
     * @throws IllegalArgumentException si le document n'existe pas ou n'appartient pas à cet employé
     */
    @Transactional
    public void deleteDocument(Long docId, Long employeeId) {
        EmployeeDocument doc = documentRepo.findByIdAndEmployeeId(docId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable : " + docId));

        try {
            Files.deleteIfExists(Paths.get(doc.getCheminFichier()));
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", doc.getCheminFichier(), e.getMessage());
        }
        documentRepo.delete(doc);
        log.info("Document deleted: id={}", docId);
    }

    /**
     * Charge le fichier comme {@link Resource} Spring pour streaming HTTP.
     *
     * @param docId      l'identifiant Oracle du document ({@code DOC_ID})
     * @param employeeId l'identifiant Oracle de l'employé propriétaire
     * @return la ressource Spring représentant le fichier sur le système de fichiers
     * @throws MalformedURLException    si le chemin du fichier est invalide
     * @throws IllegalArgumentException si le document n'appartient pas à cet employé
     * @throws IllegalStateException    si le fichier existe en base mais est inaccessible sur disque
     */
    public Resource loadAsResource(Long docId, Long employeeId) throws MalformedURLException {
        EmployeeDocument doc = documentRepo.findByIdAndEmployeeId(docId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable : " + docId));

        Resource resource = new UrlResource(Paths.get(doc.getCheminFichier()).toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("Fichier inaccessible : " + doc.getCheminFichier());
        }
        return resource;
    }

    /**
     * Retourne le type MIME du document tel que stocké en base,
     * ou {@code application/octet-stream} par défaut si absent.
     *
     * @param docId      l'identifiant Oracle du document ({@code DOC_ID})
     * @param employeeId l'identifiant Oracle de l'employé propriétaire
     * @return le type MIME du document
     */
    public String getContentType(Long docId, Long employeeId) {
        return documentRepo.findByIdAndEmployeeId(docId, employeeId)
                .map(EmployeeDocument::getTypeFichier)
                .filter(t -> t != null && !t.isBlank())
                .orElse("application/octet-stream");
    }

    /**
     * Retourne le nom original du fichier tel que fourni lors du téléversement,
     * ou {@code "document"} par défaut si le document est introuvable.
     *
     * @param docId      l'identifiant Oracle du document ({@code DOC_ID})
     * @param employeeId l'identifiant Oracle de l'employé propriétaire
     * @return le nom original du fichier
     */
    public String getFilename(Long docId, Long employeeId) {
        return documentRepo.findByIdAndEmployeeId(docId, employeeId)
                .map(EmployeeDocument::getNom)
                .orElse("document");
    }

    /**
     * Convertit une entité {@link EmployeeDocument} en map DTO pour la réponse JSON.
     *
     * @param d l'entité document à convertir
     * @return une map avec les clés {@code id}, {@code nom}, {@code type}, {@code taille}, {@code dateAjout}
     */
    private Map<String, Object> toMap(EmployeeDocument d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",        d.getId());
        m.put("nom",       d.getNom());
        m.put("type",      d.getTypeFichier());
        m.put("taille",    d.getTaille());
        m.put("dateAjout", d.getDateAjout() != null ? d.getDateAjout().toLocalDate().toString() : null);
        return m;
    }
}
