package com.springrest.springrestproject.config;

import com.springrest.springrestproject.repository.KafkaTableMappingRepo;
import com.springrest.springrestproject.service.implementations.Kafka.DynamicInboundConsumerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaMappingInitializer implements CommandLineRunner {

    private final KafkaTableMappingRepo mappingRepo;
    private final DynamicInboundConsumerManager consumerManager;

    @Override
    public void run(String @NonNull ... args) {
        mappingRepo.findByDirectionAndActiveTrue("INBOUND").forEach(mapping -> {
            try {
                consumerManager.subscribeToInboundTopic(mapping.getKafkaTopic(), mapping.getTableName());
                log.info("Successfully bound inbound topic [{}] to table [{}]", mapping.getKafkaTopic(), mapping.getTableName());
            } catch (Exception e) {
                log.error("Failed to start listener for topic: {}", mapping.getKafkaTopic(), e);
            }
        });
    }
}
