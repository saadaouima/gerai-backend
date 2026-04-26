package com.gerai.notificationservice.config;

import com.gerai.notificationservice.event.NotificationEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:notification-group}")
    private String groupId;

    /* ═══════════════════════════════════════
       🏭 CONSUMER FACTORY
       ═══════════════════════════════════════ */

    @Bean
    public ConsumerFactory<String, NotificationEvent> consumerFactory() {

        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Désactivation auto-commit (on laisse Spring gérer)
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Optimisation légère
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        // ─── Désérialisation sécurisée ───

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);

        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);

        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class);

        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class);

        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                NotificationEvent.class);

        props.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.gerai.*");

        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /* ═══════════════════════════════════════
       🎧 LISTENER CONTAINER FACTORY
       ═══════════════════════════════════════ */

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>
    kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // ACK après succès du traitement
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.RECORD);

        // Retry intelligent : 3 tentatives espacées de 2 secondes
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        new FixedBackOff(2000L, 3L)
                );

        factory.setCommonErrorHandler(errorHandler);

        // 1 thread = 1 partition (OK pour ton PFE)
        factory.setConcurrency(1);

        return factory;
    }
}