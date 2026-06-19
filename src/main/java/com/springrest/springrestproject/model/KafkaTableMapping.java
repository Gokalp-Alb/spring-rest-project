package com.springrest.springrestproject.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kafka_table_mappings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class KafkaTableMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tableName;

    @Column(nullable = false)
    private String kafkaTopic;

    @Column(nullable = false)
    private String direction;

    @Column(nullable = false)
    private boolean active = true;
}