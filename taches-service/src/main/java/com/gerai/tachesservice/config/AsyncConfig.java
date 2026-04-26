package com.gerai.tachesservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Active @Async pour TacheNotificationProducer.
 *
 * Les envois Kafka sont asynchrones pour ne JAMAIS bloquer
 * la transaction HTTP principale (createTache, updateTache…).
 *
 * Si Kafka est temporairement indisponible, la tâche Oracle est
 * sauvegardée normalement — la notification est perdue,
 * mais pas l'opération métier.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

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