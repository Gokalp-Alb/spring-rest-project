package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.model.KafkaTableMapping;
import com.springrest.springrestproject.repository.KafkaTableMappingRepo;
import com.springrest.springrestproject.service.implementations.Kafka.DynamicInboundConsumerManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kafka-mappings")
@RequiredArgsConstructor
public class KafkaMappingController {

    private final KafkaTableMappingRepo mappingRepo;
    private final DynamicInboundConsumerManager consumerManager;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KafkaTableMapping> createMapping(
            @RequestParam String tableName,
            @RequestParam String kafkaTopic,
            @RequestParam String direction) {

        KafkaTableMapping mapping = KafkaTableMapping.builder()
                .tableName(tableName)
                .kafkaTopic(kafkaTopic)
                .direction(direction.toUpperCase())
                .active(true)
                .build();

        KafkaTableMapping savedMapping = mappingRepo.save(mapping);

        if ("INBOUND".equalsIgnoreCase(direction)) {
            consumerManager.subscribeToInboundTopic(kafkaTopic, tableName);
        }

        return ApiResponse.success(HttpStatus.CREATED.value(), savedMapping);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> removeMapping(@PathVariable Long id) {
        mappingRepo.findById(id).ifPresent(mapping -> {
            KafkaTableMapping updatedMapping = KafkaTableMapping.builder()
                    .id(mapping.id())
                    .tableName(mapping.tableName())
                    .kafkaTopic(mapping.kafkaTopic())
                    .direction(mapping.direction())
                    .active(false)
                    .build();
            mappingRepo.save(updatedMapping);

            if ("INBOUND".equalsIgnoreCase(mapping.direction())) {
                consumerManager.unsubscribeFromInboundTopic(mapping.kafkaTopic());
            }
        });

        return ApiResponse.success(HttpStatus.OK.value(), null);
    }
}