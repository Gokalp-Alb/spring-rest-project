package com.springrest.springrestproject.model;

import lombok.Builder;

@Builder
public record KafkaTopic(
    Long id,
    String topicName
) {}
