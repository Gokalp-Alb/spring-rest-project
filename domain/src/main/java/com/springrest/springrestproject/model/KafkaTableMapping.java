package com.springrest.springrestproject.model;

import lombok.Builder;

@Builder
public record KafkaTableMapping(
    Long id,
    String tableName,
    String kafkaTopic,
    String direction,
    boolean active
) {}