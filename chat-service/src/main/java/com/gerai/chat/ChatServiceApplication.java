package com.gerai.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée principal du microservice de messagerie SYNAPSE (chat-service).
 * <p>
 * Ce service gère la messagerie P2P et de groupe entre employés via STOMP/WebSocket (SockJS),
 * ainsi que le chatbot IA basé sur l'API Groq (llama-3.3-70b-versatile).
 * <p>
 * {@code @SpringBootApplication} : active l'auto-configuration Spring Boot, la détection
 * des composants et la configuration automatique du contexte applicatif.
 * <br>
 * {@code @EnableScheduling} : active le moteur de tâches planifiées de Spring,
 * nécessaire pour la synchronisation périodique des présences via Keycloak
 * (voir {@link com.gerai.chat.service.PresenceService#refreshFromKeycloak()}).
 *
 * @since 1.0
 */
@SpringBootApplication
@EnableScheduling
public class ChatServiceApplication {

    /**
     * Démarre le contexte Spring Boot du microservice chat-service.
     *
     * @param args arguments de ligne de commande (transmis au contexte Spring)
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}