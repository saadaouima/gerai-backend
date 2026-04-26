package com.gerai.tachesservice.config;

import com.gerai.tachesservice.event.TacheNotificationEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Kafka Producer pour taches-service.
 *
 * Ce service PUBLIE uniquement — il n'y a pas de consommateur ici.
 * Le topic "notification-events" est écouté par notification-service.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topic.notifications:notification-events}")
    private String notificationsTopic;

    @Value("${app.kafka.topic.partitions:1}")
    private int partitions;

    @Value("${app.kafka.topic.replicas:1}")
    private int replicas;

    @Bean
    public ProducerFactory<String, TacheNotificationEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Pas de header de type — notification-service est configuré avec
        // spring.json.use.type.headers=false et un value.default.type
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        // Fiabilité
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, TacheNotificationEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Crée le topic si absent au démarrage.
     * Inutile si Kafka est configuré avec auto.create.topics.enable=true,
     * mais recommandé en production pour garantir les paramètres.
     */
    @Bean
    public NewTopic notificationTopic() {
        return TopicBuilder.name(notificationsTopic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}