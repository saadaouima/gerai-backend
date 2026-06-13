package com.gerai.analyticsservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Configuration du cache Caffeine.
 *
 * Noms de caches alignés EXACTEMENT avec les annotations @Cacheable
 * et @CacheEvict présentes dans StatsService :
 *
 *   "dashboard"         → getDashboard()
 *   "conge-stats"       → getCongeStats()
 *   "formation-stats"   → getFormationStats()
 *   "par-mois"          → getDemandesParMoisEtType()
 *   "top-5-absences"    → getTop5EmployesAbsences()
 *
 * CORRECTION : suppression de "stats-departement" et "demandes-employe"
 * qui n'ont PAS de @Cacheable dans StatsService (méthodes non cachées
 * volontairement car dépendent du rôle/deptId dynamique).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        manager.setCacheNames(Arrays.asList(
                "dashboard",
                "conge-stats",
                "formation-stats",
                "par-mois",
                "top-5-absences",
                "attrition-predictions",
                "attrition-summary"
        ));

        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .recordStats()
        );

        return manager;
    }
}