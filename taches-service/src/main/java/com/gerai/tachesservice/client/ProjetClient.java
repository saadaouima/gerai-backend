package com.gerai.tachesservice.client;

import com.gerai.tachesservice.dto.ProjetDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Client Feign vers projets-service.
 *
 * URLs alignées sur les VRAIS endpoints de projets-service :
 *
 *   GET /api/affectation/projets   → ChefProjetController.getProjets()
 *   GET /api/admin/projets         → AdminProjetController.getAllProjets()
 *   GET /api/projets/{id}          → EmployeProjetController.getProjetById()
 *   GET /api/projets/by-name       → ChefProjetController.findByName()  (à ajouter)
 *
 * Le JWT est propagé automatiquement par FeignConfig.requestInterceptor().
 * taches-service n'a aucun JPA sur PROJECTS — tout passe par ce client.
 */
@FeignClient(
        name          = "projets-service",
        url           = "${app.services.projets-url:http://localhost:8087}",
        configuration = com.gerai.tachesservice.config.FeignConfig.class
)
public interface ProjetClient {

    /**
     * Projets du chef connecté.
     * CORRECTION : /api/chef/projets n'existe pas → c'est /api/affectation/projets
     * Le JWT propagé contient le rôle CHEF, donc projets-service filtre correctement.
     */
    @GetMapping("/api/affectation/projets")
    List<ProjetDTO> getProjetsChef();

    /**
     * Tous les projets (Admin/RH).
     */
    @GetMapping("/api/admin/projets")
    List<ProjetDTO> getAllProjets();

    /**
     * Projet par ID.
     * Exposé par EmployeProjetController.getProjetById().
     */
    @GetMapping("/api/projets/{id}")
    ProjetDTO getProjetById(@PathVariable("id") Long id);

    /**
     * Projet par nom exact.
     * DOIT être ajouté dans ChefProjetController de projets-service (voir ci-dessous).
     * Endpoint : GET /api/projets/by-name?nom={nom}
     */
    @GetMapping("/api/projets/by-name")
    Optional<ProjetDTO> findByName(@RequestParam("nom") String nom);
}