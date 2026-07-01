package com.gerai.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Point d'entrée principal du microservice de notifications Synapse.
 * <p>
 * {@code @SpringBootApplication} : active la configuration automatique Spring Boot,
 * le scan des composants et la configuration des beans.<br>
 * {@code @EnableKafka} : active le traitement des messages Kafka via les listeners
 * annotés {@code @KafkaListener}.<br>
 * {@code @EnableAsync} : permet l'exécution asynchrone des méthodes annotées
 * {@code @Async} (utilisé notamment par {@code EmailService}).
 * </p>
 *
 * @since 1.0
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
public class NotificationServiceApplication {

    /**
     * Démarre le contexte Spring Boot du microservice notification-service.
     *
     * @param args arguments de ligne de commande passés au démarrage de l'application
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}