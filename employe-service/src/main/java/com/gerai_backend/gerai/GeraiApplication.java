package com.gerai_backend.gerai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée principal du microservice {@code employe-service}.
 * Lance le contexte Spring Boot et initialise tous les composants de l'application
 * (sécurité OAuth2/Keycloak, JPA Oracle, Kafka, migrations de schéma, etc.).
 *
 * <p>@SpringBootApplication : active la configuration automatique Spring Boot,
 * le scan des composants et la configuration JPA.</p>
 *
 * @since 1.0
 */
@SpringBootApplication
public class GeraiApplication {

	/**
	 * Démarre l'application Spring Boot.
	 *
	 * @param args arguments de ligne de commande (ex. {@code --spring.profiles.active=dev})
	 */
	public static void main(String[] args) {
		SpringApplication.run(GeraiApplication.class, args);
	}

}
