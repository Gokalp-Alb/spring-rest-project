package com.springrest.springrestproject.service.implementations.Kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springrest.springrestproject.core.hooks.ScriptHookInvoker;
import com.springrest.springrestproject.core.hooks.ScriptHookSession;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.model.KafkaTopic;
import com.springrest.springrestproject.repository.KafkaTopicRepo;
import com.springrest.springrestproject.service.interfaces.IDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundKafkaProcessor {

    private final IDataService dataService;
    private final ObjectMapper objectMapper;
    private final KafkaTopicRepo topicRepo;
    private final ScriptHookInvoker hookInvoker;
    private static final Long KAFKA_SYSTEM_USER_ID = 0L;

    public void processInboundEvent(String action, Object payload, String tableName, Long targetId, String topicName) {
        try {
            Long topicId = topicRepo.findByTopicName(topicName).map(KafkaTopic::id).orElse(null);
            try (ScriptHookSession hookSession = topicId == null
                    ? null
                    : hookInvoker.openInboundTopicSession(topicId, KAFKA_SYSTEM_USER_ID).orElse(null)) {
                if (hookSession != null) {
                    hookSession.invokeIfDefined("onInboundTopic");
                }

                switch (action.toUpperCase()) {
                    case "INSERT" -> {
                        TableInsertRequest insertRequest = objectMapper.convertValue(payload, TableInsertRequest.class);
                        dataService.insertRow(insertRequest, KAFKA_SYSTEM_USER_ID);
                        log.info("Successfully executed inbound Kafka INSERT for table: {}", tableName);
                    }
                    case "UPDATE" -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> updateData = objectMapper.convertValue(payload, Map.class);
                        dataService.updateRowById(tableName, targetId, updateData, KAFKA_SYSTEM_USER_ID);
                        log.info("Successfully executed inbound Kafka UPDATE for table: {} ID: {}", tableName, targetId);
                    }
                    case "DELETE" -> {
                        dataService.deleteRowById(tableName, targetId, KAFKA_SYSTEM_USER_ID);
                        log.info("Successfully executed inbound Kafka DELETE for table: {} ID: {}", tableName, targetId);
                    }
                    default -> log.warn("Unknown inbound action received: {}", action);
                }
            }
        } catch (Exception e) {
            log.error("Failed to apply inbound Kafka event to database", e);
        }
    }
}
