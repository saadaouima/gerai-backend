package com.gerai.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * Service de stockage physique des fichiers joints dans les conversations chat.
 * <p>
 * {@code @Service} : déclare ce bean comme service Spring géré par le conteneur IoC.
 * <p>
 * Les fichiers (images, documents) téléversés dans le chat sont stockés sur le système
 * de fichiers local dans le répertoire configuré par la propriété {@code file.upload-dir}.
 * Chaque fichier est renommé avec un UUID aléatoire pour éviter les collisions de noms.
 * <p>
 * L'URL retournée ({@code /uploads/{uuid}_{nom}}) est accessible publiquement via le
 * gestionnaire de ressources statiques configuré dans {@link com.gerai.chat.config.WebConfig}
 * et exclu de la chaîne de filtres Spring Security via
 * {@link com.gerai.chat.config.SecurityConfig#webSecurityCustomizer()}.
 *
 * @since 1.0
 */
@Service
public class FileStorageService {

    /** Répertoire physique de stockage des fichiers uploadés (configurable via {@code file.upload-dir}). */
    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * Stocke un fichier multipart sur le disque et retourne son URL relative d'accès.
     * <p>
     * Le nom du fichier est préfixé par un UUID v4 aléatoire pour éviter les collisions.
     * Le répertoire cible est créé automatiquement s'il n'existe pas.
     *
     * @param file le fichier multipart à stocker (image, PDF, document, etc.)
     * @return l'URL relative d'accès au fichier (ex. {@code /uploads/uuid_nom.pdf})
     * @throws RuntimeException si une erreur d'E/S survient lors de la copie du fichier
     */
    public String storeFile(MultipartFile file) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath,
                    StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + fileName; // ✅ FIX

        } catch (IOException e) {
            throw new RuntimeException("Erreur upload fichier", e);
        }
    }
}