package com.springrest.springrestproject.service.implementations.Kafka;

import com.springrest.springrestproject.core.hooks.ScriptHookInvoker;
import com.springrest.springrestproject.core.hooks.ScriptHookSession;
import com.springrest.springrestproject.model.KafkaTopic;
import com.springrest.springrestproject.repository.KafkaTableMappingRepo;
import com.springrest.springrestproject.repository.KafkaTopicRepo;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class OutboundKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTableMappingRepo mappingRepo;
    private final KafkaTopicRepo topicRepo;
    private final ScriptHookInvoker hookInvoker;

    // Explicit constructor (rather than Lombok's @RequiredArgsConstructor) so that @Lazy can be
    // placed directly on the hookInvoker parameter, mirroring DataService's constructor. @Lazy
    // breaks a genuine circular bean dependency: DataService -> OutboundKafkaPublisher ->
    // ScriptHookInvoker (ScriptHookInvokerImpl, from the scripting module) -> IDataService (used
    // by its TablesProxy to let scripts read/write table data) -> DataService again. Spring cannot
    // satisfy that cycle via constructor injection without one side being a lazy proxy.
    public OutboundKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                   KafkaTableMappingRepo mappingRepo,
                                   KafkaTopicRepo topicRepo,
                                   @Lazy ScriptHookInvoker hookInvoker) {
        this.kafkaTemplate = kafkaTemplate;
        this.mappingRepo = mappingRepo;
        this.topicRepo = topicRepo;
        this.hookInvoker = hookInvoker;
    }

    public void publishMutation(String tableName, String action, Map<String, Object> rowData, Long userId) {
        if (userId == null || userId == 0L) {
            return;
        }

        mappingRepo.findByTableNameAndDirectionAndActiveTrue(tableName, "OUTBOUND")
                .ifPresent(mapping -> {
            String topicName = topicRepo.findById(mapping.topicId())
                    .map(KafkaTopic::topicName)
                    .orElseThrow();

            try (ScriptHookSession hookSession = hookInvoker.openOutboundTopicSession(mapping.topicId(), userId).orElse(null)) {
                if (hookSession != null) {
                    hookSession.invokeIfDefined("onOutboundTopic");
                }

                Map<String, Object> messagePayload = new HashMap<>();
                messagePayload.put("action", action);
                messagePayload.put("tableName", tableName);
                messagePayload.put("payload", rowData);

                kafkaTemplate.send(topicName, messagePayload);
            }
        });
    }
}
