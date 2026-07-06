package com.springrest.springrestproject.service.implementations.Kafka;

import com.springrest.springrestproject.repository.KafkaTableMappingRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutboundKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTableMappingRepo mappingRepo;

    public void publishMutation(String tableName, String action, Map<String, Object> rowData, Long userId) {
        if (userId == null || userId == 0L) {
            return;
        }

        mappingRepo.findByTableNameAndDirectionAndActiveTrue(tableName, "OUTBOUND")
                .ifPresent(mapping -> {
            Map<String, Object> messagePayload = new HashMap<>();
            messagePayload.put("action", action);
            messagePayload.put("tableName", tableName);
            messagePayload.put("payload", rowData);

            kafkaTemplate.send(mapping.kafkaTopic(), messagePayload);
        });
    }
}