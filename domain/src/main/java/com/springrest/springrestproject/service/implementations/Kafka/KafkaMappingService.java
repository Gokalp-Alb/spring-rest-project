package com.springrest.springrestproject.service.implementations.Kafka;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.model.KafkaTableMapping;
import com.springrest.springrestproject.model.KafkaTopic;
import com.springrest.springrestproject.repository.KafkaTableMappingRepo;
import com.springrest.springrestproject.repository.KafkaTopicRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KafkaMappingService {

    private final KafkaTableMappingRepo mappingRepo;
    private final KafkaTopicRepo topicRepo;
    private final DynamicInboundConsumerManager consumerManager;

    @Transactional
    public KafkaTableMapping createMapping(String tableName, String topicName, String direction) {
        Long topicId = getOrCreateTopic(topicName);
        String upperDirection = direction.toUpperCase();

        KafkaTableMapping saved = mappingRepo.save(KafkaTableMapping.builder()
                .tableName(tableName).topicId(topicId).direction(upperDirection).active(true)
                .build());

        if ("INBOUND".equals(upperDirection)) {
            consumerManager.subscribeToInboundTopic(topicName, tableName);
        }
        return saved;
    }

    @Transactional
    public KafkaTableMapping updateMapping(Long id, String tableName, String topicName, String direction, boolean active) {
        KafkaTableMapping existing = mappingRepo.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "id: " + id));
        String existingTopicName = topicRepo.findById(existing.topicId())
                .map(KafkaTopic::topicName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "topicId: " + existing.topicId()));

        Long topicId = getOrCreateTopic(topicName);
        String upperDirection = direction.toUpperCase();

        KafkaTableMapping updated = mappingRepo.save(KafkaTableMapping.builder()
                .id(id).tableName(tableName).topicId(topicId).direction(upperDirection).active(active)
                .build());

        reconcileSubscription(existing, existingTopicName, updated, topicName);
        return updated;
    }

    public void removeMapping(Long id) {
        mappingRepo.findById(id).ifPresent(mapping -> {
            KafkaTableMapping updated = mapping.toBuilder().active(false).build();
            mappingRepo.save(updated);
            if ("INBOUND".equals(mapping.direction())) {
                String topicName = topicRepo.findById(mapping.topicId())
                        .map(KafkaTopic::topicName)
                        .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "topicId: " + mapping.topicId()));
                consumerManager.unsubscribeFromInboundTopic(topicName);
            }
        });
    }

    public List<KafkaTableMapping> listMappings() {
        return mappingRepo.findAll();
    }

    public KafkaTableMapping getMapping(Long id) {
        return mappingRepo.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "id: " + id));
    }

    private Long getOrCreateTopic(String topicName) {
        return topicRepo.findByTopicName(topicName)
                .map(KafkaTopic::id)
                .orElseGet(() -> topicRepo.save(new KafkaTopic(null, topicName)).id());
    }

    private void reconcileSubscription(KafkaTableMapping before, String beforeTopicName, KafkaTableMapping after, String afterTopicName) {
        boolean wasSubscribed = before.active() && "INBOUND".equals(before.direction());
        boolean shouldBeSubscribed = after.active() && "INBOUND".equals(after.direction());

        if (wasSubscribed && (!shouldBeSubscribed || !beforeTopicName.equals(afterTopicName))) {
            consumerManager.unsubscribeFromInboundTopic(beforeTopicName);
        }
        if (shouldBeSubscribed && (!wasSubscribed || !beforeTopicName.equals(afterTopicName))) {
            consumerManager.subscribeToInboundTopic(afterTopicName, after.tableName());
        }
    }
}
