package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.governance.SystemGovernanceGuard;
import com.springrest.springrestproject.model.KafkaTableMapping;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.SYS_KAFKA_TABLE_MAPPINGS;
import static jooq.generated.Tables.SYS_KAFKA_TABLE_MAPPINGS_LOG;

@Repository
@RequiredArgsConstructor
public class KafkaTableMappingRepo {
    private final DSLContext dsl;
    private final SystemGovernanceGuard governanceGuard;

    public List<KafkaTableMapping> findByDirectionAndActiveTrue(String direction) {
        return dsl.selectFrom(SYS_KAFKA_TABLE_MAPPINGS)
                .where(SYS_KAFKA_TABLE_MAPPINGS.DIRECTION.eq(direction))
                .and(SYS_KAFKA_TABLE_MAPPINGS.ACTIVE.eq(true))
                .fetchInto(KafkaTableMapping.class);
    }

    public Optional<KafkaTableMapping> findByTableNameAndDirectionAndActiveTrue(String tableName, String direction) {
        return Optional.ofNullable(
                dsl.selectFrom(SYS_KAFKA_TABLE_MAPPINGS)
                        .where(SYS_KAFKA_TABLE_MAPPINGS.TABLE_NAME.eq(tableName))
                        .and(SYS_KAFKA_TABLE_MAPPINGS.DIRECTION.eq(direction))
                        .and(SYS_KAFKA_TABLE_MAPPINGS.ACTIVE.eq(true))
                        .fetchOneInto(KafkaTableMapping.class)
        );
    }

    public Optional<KafkaTableMapping> findById(Long id) {
        return Optional.ofNullable(
                dsl.selectFrom(SYS_KAFKA_TABLE_MAPPINGS)
                        .where(SYS_KAFKA_TABLE_MAPPINGS.ID.eq(id))
                        .fetchOneInto(KafkaTableMapping.class)
        );
    }

    public List<KafkaTableMapping> findAll() {
        return dsl.selectFrom(SYS_KAFKA_TABLE_MAPPINGS).fetchInto(KafkaTableMapping.class);
    }

    public KafkaTableMapping save(KafkaTableMapping mapping) {
        if (mapping.id() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(SYS_KAFKA_TABLE_MAPPINGS)
                            .set(SYS_KAFKA_TABLE_MAPPINGS.TABLE_NAME, mapping.tableName())
                            .set(SYS_KAFKA_TABLE_MAPPINGS.KAFKA_TOPIC, mapping.kafkaTopic())
                            .set(SYS_KAFKA_TABLE_MAPPINGS.DIRECTION, mapping.direction())
                            .set(SYS_KAFKA_TABLE_MAPPINGS.ACTIVE, mapping.active())
                            .returning(SYS_KAFKA_TABLE_MAPPINGS.ID)
                            .fetchOne())
                    .getValue(SYS_KAFKA_TABLE_MAPPINGS.ID);
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
            Boolean currentRestricted = dsl.select(SYS_KAFKA_TABLE_MAPPINGS.IS_RESTRICTED)
                    .from(SYS_KAFKA_TABLE_MAPPINGS).where(SYS_KAFKA_TABLE_MAPPINGS.ID.eq(mapping.id()))
                    .fetchOne(SYS_KAFKA_TABLE_MAPPINGS.IS_RESTRICTED);
            governanceGuard.assertRowMutable(Boolean.TRUE.equals(currentRestricted));
            dsl.update(SYS_KAFKA_TABLE_MAPPINGS)
                    .set(SYS_KAFKA_TABLE_MAPPINGS.TABLE_NAME, mapping.tableName())
                    .set(SYS_KAFKA_TABLE_MAPPINGS.KAFKA_TOPIC, mapping.kafkaTopic())
                    .set(SYS_KAFKA_TABLE_MAPPINGS.DIRECTION, mapping.direction())
                    .set(SYS_KAFKA_TABLE_MAPPINGS.ACTIVE, mapping.active())
                    .where(SYS_KAFKA_TABLE_MAPPINGS.ID.eq(mapping.id()))
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
        dsl.insertInto(SYS_KAFKA_TABLE_MAPPINGS_LOG)
                .set(SYS_KAFKA_TABLE_MAPPINGS_LOG.ID, mapping.id())
                .set(SYS_KAFKA_TABLE_MAPPINGS_LOG.ACTIVE, mapping.active())
                .set(SYS_KAFKA_TABLE_MAPPINGS_LOG.DIRECTION, mapping.direction())
                .set(SYS_KAFKA_TABLE_MAPPINGS_LOG.KAFKA_TOPIC, mapping.kafkaTopic())
                .set(SYS_KAFKA_TABLE_MAPPINGS_LOG.TABLE_NAME, mapping.tableName())
                .set(SYS_KAFKA_TABLE_MAPPINGS_LOG.OPERATION_TYPE, operation)
                .set(SYS_KAFKA_TABLE_MAPPINGS_LOG.EXECUTED_AT, java.time.LocalDateTime.now())
                .set(SYS_KAFKA_TABLE_MAPPINGS_LOG.USER_ID, executorId)
                .execute();
    }
}