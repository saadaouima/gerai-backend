package com.gerai.tachesservice.client;

import com.gerai.tachesservice.dto.ProjetDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Client Feign déclaratif vers le microservice {@code projets-service}.
 * <p>
 * Permet à {@code taches-service} d'interroger les données de projets sans
 * accéder directement à la base Oracle de {@code projets-service}. Ce client
 * respecte le principe de séparation des données entre microservices.
 * <p>
 * URLs alignées sur les endpoints réels de projets-service :
 * <ul>
 *   <li>{@code GET /api/affectation/projets} → ChefProjetController.getProjets()</li>
 *   <li>{@code GET /api/admin/projets} → AdminProjetController.getAllProjets()</li>
 *   <li>{@code GET /api/projets/{id}} → EmployeProjetController.getProjetById()</li>
 *   <li>{@code GET /api/projets/by-name} → ChefProjetController.findByName()</li>
 * </ul>
 * <p>
 * {@code @FeignClient} : génère une implémentation HTTP proxy automatique.
 * Le JWT Bearer de l'utilisateur connecté est propagé automatiquement
 * via {@code FeignConfig.requestInterceptor()}, garantissant que les droits
 * d'accès sont respectés côté projets-service.
 * <p>
 * {@code taches-service} ne possède aucune entité JPA sur la table PROJECTS —
 * toutes les données de projets transitent exclusivement par ce client.
 *
 * @since 1.0
 */
@FeignClient(
        name          = "projets-service",
        url           = "${app.services.projets-url:http://localhost:8087}",
        configuration = com.gerai.tachesservice.config.FeignConfig.class
)
public interface ProjetClient {

    /**
     * Récupère la liste des projets associés au chef connecté.
     * <p>
     * Le JWT propagé contient le rôle CHEF, ce qui permet à projets-service
     * de filtrer les projets dont l'utilisateur est le créateur ou chef.
     *
     * @return liste des {@link ProjetDTO} du chef connecté
     */
    @GetMapping("/api/affectation/projets")
    List<ProjetDTO> getProjetsChef();

    /**
     * Récupère tous les projets de la plateforme, réservé aux rôles Admin/RH.
     *
     * @return liste complète des {@link ProjetDTO}
     */
    @GetMapping("/api/admin/projets")
    List<ProjetDTO> getAllProjets();

    /**
     * Récupère un projet par son identifiant Oracle.
     * <p>
     * Exposé par {@code EmployeProjetController.getProjetById()} dans projets-service.
     *
     * @param id identifiant Oracle du projet (PROJECTS.project_id)
     * @return le {@link ProjetDTO} correspondant
     */
    @GetMapping("/api/projets/{id}")
    ProjetDTO getProjetById(@PathVariable("id") Long id);

    /**
     * Recherche un projet par son nom exact (insensible à la casse selon l'implémentation serveur).
     * <p>
     * Endpoint : {@code GET /api/projets/by-name?nom={nom}}
     *
     * @param nom nom complet du projet tel qu'enregistré dans PROJECTS.name
     * @return un {@link Optional} contenant le {@link ProjetDTO} si trouvé, vide sinon
     */
    @GetMapping("/api/projets/by-name")
    Optional<ProjetDTO> findByName(@RequestParam("nom") String nom);
}