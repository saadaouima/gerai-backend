package com.gerai.analyticsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée principal du microservice Analytics.
 * Ce service calcule les prédictions d'attrition des employés,
 * génère des rapports RH (PDF via JasperReports, Excel) et expose
 * des statistiques analytiques via des endpoints REST sécurisés.
 *
 * {@code @SpringBootApplication} : active la configuration automatique Spring Boot,
 * le scan des composants et la configuration de l'application.
 *
 * @since 1.0
 */
@SpringBootApplication
public class AnalyticsServiceApplication {

    /**
     * Méthode principale qui démarre l'application Spring Boot.
     *
     * @param args arguments de la ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }

}
