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
 * Configuration du gestionnaire de cache Caffeine pour le service analytique.
 *
 * Noms de caches alignés EXACTEMENT avec les annotations {@code @Cacheable}
 * et {@code @CacheEvict} présentes dans {@link com.gerai.analyticsservice.service.StatsService} :
 * <ul>
 *   <li>"dashboard"         → {@code getDashboard()}</li>
 *   <li>"conge-stats"       → {@code getCongeStats()}</li>
 *   <li>"formation-stats"   → {@code getFormationStats()}</li>
 *   <li>"par-mois"          → {@code getDemandesParMoisEtType()}</li>
 *   <li>"top-5-absences"    → {@code getTop5EmployesAbsences()}</li>
 *   <li>"attrition-predictions" → {@code AttritionService.getPredictions()}</li>
 *   <li>"attrition-summary"     → {@code AttritionService.getSummary()}</li>
 * </ul>
 *
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * {@code @EnableCaching} : active le support des annotations de cache ({@code @Cacheable}, etc.)
 * dans le contexte Spring. Les entrées expirent après 10 minutes d'écriture,
 * avec une taille maximale de 500 entrées par région.
 *
 * @since 1.0
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Crée et configure le gestionnaire de cache Caffeine.
     * Toutes les régions partagent la même politique d'expiration :
     * 10 minutes après écriture, avec un maximum de 500 entrées.
     *
     * @return l'instance configurée de {@link CacheManager}
     */
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