package com.gerai.demandesservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée du microservice {@code demandes-service} de la plateforme SYNAPSE.
 * <p>
 * Ce service centralise la gestion de toutes les demandes RH :
 * congés, formations, prêts/crédits, documents administratifs et autorisations d'absence.
 * <p>
 * {@code @SpringBootApplication} : active la configuration automatique Spring Boot,
 * le scan des composants et la configuration des beans.
 * <p>
 * {@code @EnableScheduling} : active le moteur de planification Spring permettant
 * l'exécution des tâches planifiées ({@link com.gerai.demandesservice.scheduler.SlaBreachScheduler}
 * et {@link com.gerai.demandesservice.scheduler.ContratExpiryScheduler}).
 *
 * @since 1.0
 */
@SpringBootApplication
@EnableScheduling
public class DemandesServiceApplication {

    /**
     * Méthode principale — démarre le contexte Spring Boot.
     *
     * @param args arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        SpringApplication.run(DemandesServiceApplication.class, args);
    }

}
