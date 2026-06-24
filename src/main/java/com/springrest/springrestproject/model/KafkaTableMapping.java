package com.springrest.springrestproject.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class KafkaTableMapping {
    private Long id;
    private String tableName;
    private String kafkaTopic;
    private String direction;
    private boolean active;
}