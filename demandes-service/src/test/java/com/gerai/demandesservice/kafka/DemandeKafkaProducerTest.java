package com.gerai.demandesservice.kafka;

import com.gerai.demandesservice.dto.NotificationEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration Kafka : vérifie que KafkaTemplate<String, NotificationEvent>
 * publie correctement sur le topic "notification-events" avec le bon schéma JSON.
 *
 * EmbeddedKafkaBroker démarre un broker Kafka en mémoire (sans Docker),
 * et spring.kafka.bootstrap-servers est automatiquement pointé vers ce broker
 * via la propriété ${spring.embedded.kafka.brokers} du profil test.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics     = {"notification-events"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:0",
        "port=0"
    }
)
@DirtiesContext
class DemandeKafkaProducerTest {

    private static final String TOPIC = "notification-events";

    @Autowired
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private Consumer<String, NotificationEvent> consumer;

    @BeforeEach
    void setUpConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
            "test-grp-" + System.currentTimeMillis(), "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            new JsonDeserializer<>(NotificationEvent.class, false)
        ).createConsumer();

        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TOPIC);
    }

    @AfterEach
    void tearDownConsumer() {
        consumer.close();
    }

    // ─── Test 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1 — NotificationEvent validé RH : publié sur notification-events avec payload correct")
    void whenValidationRhEvent_thenConsumerReceivesCorrectPayload() throws Exception {
        NotificationEvent event = NotificationEvent.builder()
            .employeeId(10L)
            .email("nour@gerai.tn")
            .type("VALIDEE_RH")
            .title("Votre demande de congé a été approuvée")
            .referenceId("42")
            .referenceType("CONGE")
            .sourceService("DEMANDES-SERVICE")
            .build();

        // Envoi synchrone pour garantir la livraison avant la lecture
        kafkaTemplate.send(TOPIC, String.valueOf(event.getEmployeeId()), event).get();

        ConsumerRecords<String, NotificationEvent> records =
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.count()).isEqualTo(1);
        NotificationEvent received = records.iterator().next().value();
        assertThat(received.getEmployeeId()).isEqualTo(10L);
        assertThat(received.getType()).isEqualTo("VALIDEE_RH");
        assertThat(received.getReferenceType()).isEqualTo("CONGE");
        assertThat(received.getSourceService()).isEqualTo("DEMANDES-SERVICE");
    }

    // ─── Test 2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("2 — NotificationEvent rejet : clé Kafka = employeeId (routage correct)")
    void whenRejetEvent_thenKafkaKeyIsEmployeeId() throws Exception {
        NotificationEvent event = NotificationEvent.builder()
            .employeeId(20L)
            .email("chef@gerai.tn")
            .type("REJETEE")
            .title("Votre demande a été refusée")
            .referenceId("99")
            .referenceType("PRET")
            .sourceService("DEMANDES-SERVICE")
            .build();

        kafkaTemplate.send(TOPIC, String.valueOf(event.getEmployeeId()), event).get();

        ConsumerRecords<String, NotificationEvent> records =
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        // Parmi tous les records (peut inclure des messages de tests précédents),
        // chercher celui dont la clé est "20" (employeeId du test courant)
        var matching = StreamSupport.stream(records.spliterator(), false)
            .filter(r -> "20".equals(r.key()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Aucun record avec la clé '20'"));

        // La clé doit être l'employeeId en String — garantit le routage vers
        // la partition correcte pour les notifications de cet employé
        assertThat(matching.key()).isEqualTo("20");
        assertThat(matching.value().getType()).isEqualTo("REJETEE");
    }
}
