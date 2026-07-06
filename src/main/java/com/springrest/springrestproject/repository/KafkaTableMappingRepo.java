package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.KafkaTableMapping;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.KAFKA_TABLE_MAPPINGS;
import static jooq.generated.Tables.KAFKA_TABLE_MAPPINGS_LOG;

@Repository
@RequiredArgsConstructor
public class KafkaTableMappingRepo {
    private final DSLContext dsl;

    public List<KafkaTableMapping> findByDirectionAndActiveTrue(String direction) {
        return dsl.selectFrom(KAFKA_TABLE_MAPPINGS)
                .where(KAFKA_TABLE_MAPPINGS.DIRECTION.eq(direction))
                .and(KAFKA_TABLE_MAPPINGS.ACTIVE.eq(true))
                .fetchInto(KafkaTableMapping.class);
    }

    public Optional<KafkaTableMapping> findByTableNameAndDirectionAndActiveTrue(String tableName, String direction) {
        return Optional.ofNullable(
                dsl.selectFrom(KAFKA_TABLE_MAPPINGS)
                        .where(KAFKA_TABLE_MAPPINGS.TABLE_NAME.eq(tableName))
                        .and(KAFKA_TABLE_MAPPINGS.DIRECTION.eq(direction))
                        .and(KAFKA_TABLE_MAPPINGS.ACTIVE.eq(true))
                        .fetchOneInto(KafkaTableMapping.class)
        );
    }

    public Optional<KafkaTableMapping> findById(Long id) {
        return Optional.ofNullable(
                dsl.selectFrom(KAFKA_TABLE_MAPPINGS)
                        .where(KAFKA_TABLE_MAPPINGS.ID.eq(id))
                        .fetchOneInto(KafkaTableMapping.class)
        );
    }

    public KafkaTableMapping save(KafkaTableMapping mapping) {
        if (mapping.id() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(KAFKA_TABLE_MAPPINGS)
                            .set(KAFKA_TABLE_MAPPINGS.TABLE_NAME, mapping.tableName())
                            .set(KAFKA_TABLE_MAPPINGS.KAFKA_TOPIC, mapping.kafkaTopic())
                            .set(KAFKA_TABLE_MAPPINGS.DIRECTION, mapping.direction())
                            .set(KAFKA_TABLE_MAPPINGS.ACTIVE, mapping.active())
                            .returning(KAFKA_TABLE_MAPPINGS.ID)
                            .fetchOne())
                    .getValue(KAFKA_TABLE_MAPPINGS.ID);
            KafkaTableMapping savedMapping = KafkaTableMapping.builder()
                    .id(generatedId)
                    .tableName(mapping.tableName())
                    .kafkaTopic(mapping.kafkaTopic())
                    .direction(mapping.direction())
                    .active(mapping.active())
                    .build();
            logKafkaTableMappingMutation(savedMapping, "POST");
            return savedMapping;
        } else {
            dsl.update(KAFKA_TABLE_MAPPINGS)
                    .set(KAFKA_TABLE_MAPPINGS.TABLE_NAME, mapping.tableName())
                    .set(KAFKA_TABLE_MAPPINGS.KAFKA_TOPIC, mapping.kafkaTopic())
                    .set(KAFKA_TABLE_MAPPINGS.DIRECTION, mapping.direction())
                    .set(KAFKA_TABLE_MAPPINGS.ACTIVE, mapping.active())
                    .where(KAFKA_TABLE_MAPPINGS.ID.eq(mapping.id()))
                    .execute();
            logKafkaTableMappingMutation(mapping, "PUT");
            return mapping;
        }
    }

    private void logKafkaTableMappingMutation(KafkaTableMapping mapping, String operation) {
        Long executorId = com.springrest.springrestproject.util.SecurityUtils.getCurrentUserId();
        if (executorId == null) {
            executorId = 0L;
        }
        dsl.insertInto(KAFKA_TABLE_MAPPINGS_LOG)
                .set(KAFKA_TABLE_MAPPINGS_LOG.ID, mapping.id())
                .set(KAFKA_TABLE_MAPPINGS_LOG.ACTIVE, mapping.active())
                .set(KAFKA_TABLE_MAPPINGS_LOG.DIRECTION, mapping.direction())
                .set(KAFKA_TABLE_MAPPINGS_LOG.KAFKA_TOPIC, mapping.kafkaTopic())
                .set(KAFKA_TABLE_MAPPINGS_LOG.TABLE_NAME, mapping.tableName())
                .set(KAFKA_TABLE_MAPPINGS_LOG.OPERATION_TYPE, operation)
                .set(KAFKA_TABLE_MAPPINGS_LOG.EXECUTED_AT, java.time.LocalDateTime.now())
                .set(KAFKA_TABLE_MAPPINGS_LOG.USER_ID, executorId)
                .execute();
    }
}