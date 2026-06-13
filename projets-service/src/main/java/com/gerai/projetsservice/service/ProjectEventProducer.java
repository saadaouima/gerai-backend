package com.gerai.projetsservice.service;

import com.gerai.projetsservice.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectEventProducer {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Value("${app.kafka.topic.notifications}")
    private String topic;

    public void emit(NotificationEvent event) {
        // Allow role-broadcast events (employeeId is null, role is set)
        if (event.getEmployeeId() == null && (event.getRole() == null || event.getRole().isBlank())) {
            log.warn("[ProjectEventProducer] employeeId et role absents, notification ignorée");
            return;
        }
        String key = event.getEmployeeId() != null
                ? String.valueOf(event.getEmployeeId())
                : "role-" + event.getRole();
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("[ProjectEventProducer] Échec envoi Kafka | dest={} role={} : {}",
                                event.getEmployeeId(), event.getRole(), ex.getMessage());
                    } else {
                        SendResult<String, NotificationEvent> sr = result;
                        log.info("[ProjectEventProducer] Notification publiée | dest={} role={} | type={} | partition={} | offset={}",
                                event.getEmployeeId(), event.getRole(), event.getType(),
                                sr.getRecordMetadata().partition(),
                                sr.getRecordMetadata().offset());
                    }
                });
    }
}
