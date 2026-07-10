package com.springrest.springrestproject.service.implementations.Kafka;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class DynamicInboundConsumerManager {

    private final ConsumerFactory<String, Map<String, Object>> consumerFactory;
    private final InboundKafkaProcessor inboundProcessor;
    private final ConcurrentHashMap<String, ConcurrentMessageListenerContainer<String, Map<String, Object>>> activeContainers = new ConcurrentHashMap<>();

    public synchronized void subscribeToInboundTopic(String topic, String targetTableName) {
        if (activeContainers.containsKey(topic)) return;

        ContainerProperties containerProperties = new ContainerProperties(topic);

        containerProperties.setMessageListener((MessageListener<String, Map<String, Object>>) record -> {
            Map<String, Object> messageBody = record.value();

            String action = (String) messageBody.get("action");
            Object payload = messageBody.get("payload");

            Number idNum = (Number) messageBody.get("id");
            Long targetId = idNum != null ? idNum.longValue() : null;

            inboundProcessor.processInboundEvent(action, payload, targetTableName, targetId);
        });

        ConcurrentMessageListenerContainer<String, Map<String, Object>> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, containerProperties);

        container.start();
        activeContainers.put(topic, container);
    }

    public synchronized void unsubscribeFromInboundTopic(String topic) {
        ConcurrentMessageListenerContainer<String, Map<String, Object>> container = activeContainers.remove(topic);
        if (container != null) {
            container.stop();
        }
    }

    @PreDestroy
    public synchronized void shutdownAllConsumers() {
        activeContainers.forEach((topic, container) -> {
            try {
                if (container.isRunning()) {
                    container.stop();
                    log.info("Stopped dynamic consumer container for topic: {}", topic);
                }
            } catch (Exception e) {
                log.error("Failed to cleanly stop consumer container for topic: {}", topic, e);
            }
        });

        activeContainers.clear();
    }
}