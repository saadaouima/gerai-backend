package com.gerai.notificationservice.config;

import com.gerai.notificationservice.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configuration Kafka du microservice notification-service.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring.<br>
 * {@code @EnableKafka} : active l'infrastructure Kafka de Spring (traitement
 * des annotations {@code @KafkaListener}).<br>
 * {@code @Slf4j} : injecte un logger Lombok pour tracer les événements Kafka.
 * </p>
 * <p>
 * Responsabilités : création automatique du topic {@code notification-events}
 * au démarrage et configuration d'un gestionnaire d'erreurs avec politique
 * de ré-essai (3 tentatives espacées de 1 seconde) avant de passer le message.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Configuration
public class KafkaConfig {

    /**
     * Crée automatiquement le topic Kafka de notifications sur le broker
     * s'il n'existe pas encore au démarrage de l'application.
     * <p>
     * 3 partitions permettent une consommation parallèle par groupe logique
     * de producteurs (projets, tâches, demandes). Le facteur de réplication 1
     * correspond à la configuration mono-broker de l'environnement de développement.
     * </p>
     *
     * @param topic nom du topic, injecté depuis {@code app.kafka.topic.notifications}
     * @return le descripteur {@link NewTopic} utilisé par Spring Kafka pour
     *         créer le topic sur le broker
     */
    @Bean
    public NewTopic notificationEventsTopic(
            @Value("${app.kafka.topic.notifications}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Remplace la factory de conteneurs Kafka par défaut de Spring Boot
     * en ajoutant un gestionnaire d'erreurs personnalisé.
     * <p>
     * Stratégie : jusqu'à 3 tentatives avec une pause de 1 seconde entre chaque ;
     * si toutes échouent, le message est journalisé et ignoré afin d'éviter
     * tout blocage du consommateur.
     * </p>
     *
     * @param consumerFactory fabrique de consommateurs Kafka auto-configurée par Spring Boot
     * @return une {@link ConcurrentKafkaListenerContainerFactory} prête à l'emploi
     *         avec gestion des erreurs et politique de ré-essai
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, NotificationEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Retry 3 times, 1 s apart, then skip the message with a warning
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "[KafkaConsumer] Message définitivement ignoré après 3 tentatives | " +
                        "topic={} | partition={} | offset={} | erreur={}",
                        record.topic(), record.partition(), record.offset(),
                        exception.getMessage()),
                new FixedBackOff(1_000L, 3L)
        );

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
