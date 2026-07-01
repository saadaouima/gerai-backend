package com.gerai_backend.gerai.config;

import com.gerai_backend.gerai.dto.NotificationEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration du producteur Kafka pour l'envoi d'événements de notification.
 *
 * <p>@Configuration : déclare les beans Kafka nécessaires à la publication de messages
 * vers le topic de notifications (consommé par le {@code notification-service}).</p>
 *
 * <p>La sérialisation des valeurs utilise {@link org.springframework.kafka.support.serializer.JsonSerializer}
 * sans en-tête de type ({@code ADD_TYPE_INFO_HEADERS=false}) pour assurer
 * la compatibilité avec les consommateurs qui n'ont pas les mêmes classes DTO.</p>
 *
 * @since 1.0
 */
@Configuration
public class KafkaProducerConfig {

    /** Adresse du broker Kafka (valeur par défaut : {@code localhost:9092}). */
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Crée la fabrique de producteurs Kafka pour les événements {@link NotificationEvent}.
     *
     * <p>Configure le sérialiseur JSON sans en-têtes de type afin d'éviter les erreurs
     * de désérialisation côté consommateur lorsque les classes DTO diffèrent.</p>
     *
     * @return une {@link ProducerFactory} prête à créer des producteurs Kafka
     */
    @Bean
    public ProducerFactory<String, NotificationEvent> notificationProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Crée le {@link KafkaTemplate} utilisé pour publier des {@link NotificationEvent}
     * vers le topic Kafka de notifications.
     *
     * @return un {@link KafkaTemplate} configuré avec la fabrique de producteurs Kafka
     */
    @Bean
    public KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate() {
        return new KafkaTemplate<>(notificationProducerFactory());
    }
}
