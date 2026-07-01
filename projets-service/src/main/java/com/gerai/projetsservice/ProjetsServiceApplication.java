package com.gerai.projetsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée principal du microservice {@code projets-service}.
 * <p>
 * Ce service est le plus riche de la plateforme SYNAPSE : il gère les projets,
 * les tâches, les membres d'équipe, le recrutement complet (offres, candidats,
 * entretiens, questions de présélection), les évaluations de performance,
 * le référentiel RH et les soldes de congés administratifs.
 * </p>
 * <p>
 * {@code @SpringBootApplication} active l'auto-configuration Spring Boot,
 * le scan des composants et la configuration automatique du contexte applicatif.
 * </p>
 *
 * @since 1.0
 */
@SpringBootApplication
public class ProjetsServiceApplication {

    /**
     * Démarre le microservice projets-service.
     *
     * @param args arguments de la ligne de commande (transmis à Spring Boot)
     */
    public static void main(String[] args) {
        SpringApplication.run(ProjetsServiceApplication.class, args);
    }
}
