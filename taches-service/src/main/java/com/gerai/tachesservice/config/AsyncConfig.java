package com.gerai.tachesservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration du pool de threads asynchrones pour les notifications Kafka.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.
 * {@code @EnableAsync} : active le support de l'annotation {@code @Async} dans
 * l'ensemble du contexte Spring. Les méthodes de {@link com.gerai.tachesservice.service.TacheNotificationProducer}
 * annotées {@code @Async} s'exécutent dans le pool défini ici.
 * <p>
 * Principe de résilience : les envois Kafka sont décorrélés de la transaction HTTP
 * principale. Si Kafka est temporairement indisponible, la tâche Oracle est sauvegardée
 * normalement — la notification est perdue mais l'opération métier est préservée.
 *
 * @since 1.0
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Crée et configure le pool de threads dédié aux envois de notifications Kafka.
     * <p>
     * Le bean est nommé {@code "notifExecutor"} pour être référencé explicitement
     * par {@code @Async("notifExecutor")} dans {@code TacheNotificationProducer}.
     * <ul>
     *   <li>Taille du cœur : 2 threads permanents</li>
     *   <li>Taille maximale : 4 threads en cas de pic</li>
     *   <li>Capacité de la file d'attente : 50 tâches en attente</li>
     *   <li>Préfixe des threads : {@code tache-notif-} (visible dans les logs)</li>
     *   <li>Arrêt gracieux : attend 10 secondes la fin des tâches en cours</li>
     * </ul>
     *
     * @return l'exécuteur configuré pour les notifications asynchrones
     */
    @Bean(name = "notifExecutor")
    public Executor notifExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("tache-notif-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}