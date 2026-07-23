package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.model.KafkaTableMapping;
import com.springrest.springrestproject.service.implementations.Kafka.KafkaMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kafka-mappings")
@RequiredArgsConstructor
public class KafkaMappingController {

    private final KafkaMappingService mappingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KafkaTableMapping> createMapping(
            @RequestParam String tableName,
            @RequestParam String topicName,
            @RequestParam String direction) {
        return ApiResponse.success(HttpStatus.CREATED.value(), mappingService.createMapping(tableName, topicName, direction));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> removeMapping(@PathVariable Long id) {
        mappingService.removeMapping(id);
        return ApiResponse.success(HttpStatus.OK.value(), null);
    }

    @GetMapping
    public ApiResponse<List<KafkaTableMapping>> listMappings() {
        return ApiResponse.success(mappingService.listMappings());
    }

    @GetMapping("/{id}")
    public ApiResponse<KafkaTableMapping> getMapping(@PathVariable Long id) {
        return ApiResponse.success(mappingService.getMapping(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KafkaTableMapping> updateMapping(
            @PathVariable Long id,
            @RequestParam String tableName,
            @RequestParam String topicName,
            @RequestParam String direction,
            @RequestParam boolean active) {
        return ApiResponse.success(mappingService.updateMapping(id, tableName, topicName, direction, active));
    }
}
