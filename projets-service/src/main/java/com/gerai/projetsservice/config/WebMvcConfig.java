package com.gerai.projetsservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration Spring MVC pour la diffusion statique des CVs téléversés.
 * <p>
 * Expose le répertoire de stockage des CVs sous le chemin URL {@code /cvs/**},
 * ce qui permet aux administrateurs d'accéder directement aux fichiers PDF
 * via leur URL sans passer par une ressource de téléchargement dédiée.
 * </p>
 * <p>
 * {@code @Configuration} : enregistre cette configuration dans le contexte Spring MVC.
 * </p>
 *
 * @since 1.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Répertoire du système de fichiers où sont stockés les CVs téléversés. */
    @Value("${app.cv.upload-dir:${user.home}/synapse-cvs}")
    private String uploadDir;

    /**
     * Enregistre un gestionnaire de ressources statiques pour les CVs.
     * <p>
     * Les requêtes vers {@code /cvs/<nom-fichier>} sont servies depuis
     * le répertoire {@code uploadDir} configuré.
     * </p>
     *
     * @param registry le registre des gestionnaires de ressources Spring MVC
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/cvs/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
