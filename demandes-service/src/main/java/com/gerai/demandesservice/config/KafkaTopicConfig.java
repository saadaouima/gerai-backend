package com.gerai.demandesservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // On définit le nom du topic.
    // Si la clé n'est pas dans application.properties, on utilise "notification-events" par défaut.
    @Value("${app.kafka.topic.notifications:notification-events}")
    private String notificationsTopic;

    @Bean
    public NewTopic notificationTopic() {
        return TopicBuilder.name(notificationsTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}