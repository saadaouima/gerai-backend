package com.gerai.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration Web MVC du microservice chat-service.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * <p>
 * Responsabilités :
 * <ul>
 *   <li>Configuration CORS pour autoriser le frontend Angular ({@code http://localhost:4200})
 *       à interagir avec l'API REST.</li>
 *   <li>Exposition des fichiers uploadés (images, pièces jointes) via une route HTTP statique
 *       {@code /uploads/**} pointant vers le répertoire physique configuré.</li>
 * </ul>
 *
 * @since 1.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Répertoire physique de stockage des fichiers uploadés (configurable via {@code file.upload-dir}). */
    @Value("${file.upload-dir:C:/GerAI/uploads}")
    private String uploadDir;

    /**
     * Configure les règles CORS (Cross-Origin Resource Sharing) globales.
     * Autorise le frontend Angular ({@code http://localhost:4200}) à effectuer
     * des requêtes GET, POST, PUT, DELETE et OPTIONS avec tous les en-têtes,
     * en incluant les credentials (cookies/sessions).
     *
     * @param registry le registre des mappings CORS à configurer
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Expose le répertoire d'uploads comme ressource statique accessible via HTTP.
     * L'URL {@code /uploads/**} est mappée sur le répertoire physique
     * défini par la propriété {@code file.upload-dir}.
     * <p>
     * Note : ce chemin est également ignoré par Spring Security via
     * {@link SecurityConfig#webSecurityCustomizer()}.
     *
     * @param registry le registre des gestionnaires de ressources
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + uploadDir.replace("\\", "/");
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}