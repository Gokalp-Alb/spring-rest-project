package com.springrest.springrestproject.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record KafkaTableMapping(
    Long id,
    String tableName,
    Long topicId,
    String direction,
    boolean active
) {}