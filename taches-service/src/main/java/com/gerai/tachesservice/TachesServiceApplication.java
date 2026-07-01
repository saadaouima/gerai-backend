package com.gerai.tachesservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée du microservice de gestion des tâches (taches-service).
 * <p>
 * Ce service est responsable du cycle de vie des tâches Kanban assignées aux
 * employés dans le cadre des projets SYNAPSE. Il gère les colonnes :
 * À FAIRE, EN_COURS, TERMINÉ et EN_RETARD.
 * <p>
 * Annotations de configuration globale :
 * <ul>
 *   <li>{@code @SpringBootApplication} : active la configuration automatique,
 *       le scan des composants et la configuration Spring Boot.</li>
 *   <li>{@code @EnableFeignClients} : active les clients Feign déclarés dans
 *       le package {@code client} pour les appels inter-services (projets-service).</li>
 *   <li>{@code @EnableScheduling} : active l'exécution des tâches planifiées,
 *       notamment le {@code TacheOverdueScheduler} qui détecte les tâches en retard.</li>
 *   <li>{@code @EnableAsync} : active l'exécution asynchrone des méthodes annotées
 *       {@code @Async}, utilisée par {@code TacheNotificationProducer} pour envoyer
 *       les notifications Kafka sans bloquer les transactions HTTP.</li>
 * </ul>
 *
 * @since 1.0
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableAsync
public class TachesServiceApplication {

    /**
     * Point d'entrée principal — démarre le contexte Spring Boot du microservice taches-service.
     *
     * @param args arguments de ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        SpringApplication.run(TachesServiceApplication.class, args);
    }

}
