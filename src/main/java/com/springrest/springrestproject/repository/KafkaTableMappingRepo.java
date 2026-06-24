package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.KafkaTableMapping;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.KAFKA_TABLE_MAPPINGS;

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
        if (mapping.getId() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(KAFKA_TABLE_MAPPINGS)
                            .set(KAFKA_TABLE_MAPPINGS.TABLE_NAME, mapping.getTableName())
                            .set(KAFKA_TABLE_MAPPINGS.KAFKA_TOPIC, mapping.getKafkaTopic())
                            .set(KAFKA_TABLE_MAPPINGS.DIRECTION, mapping.getDirection())
                            .set(KAFKA_TABLE_MAPPINGS.ACTIVE, mapping.isActive())
                            .returning(KAFKA_TABLE_MAPPINGS.ID)
                            .fetchOne())
                    .getValue(KAFKA_TABLE_MAPPINGS.ID);
            mapping.setId(generatedId);
        } else {
            dsl.update(KAFKA_TABLE_MAPPINGS)
                    .set(KAFKA_TABLE_MAPPINGS.TABLE_NAME, mapping.getTableName())
                    .set(KAFKA_TABLE_MAPPINGS.KAFKA_TOPIC, mapping.getKafkaTopic())
                    .set(KAFKA_TABLE_MAPPINGS.DIRECTION, mapping.getDirection())
                    .set(KAFKA_TABLE_MAPPINGS.ACTIVE, mapping.isActive())
                    .where(KAFKA_TABLE_MAPPINGS.ID.eq(mapping.getId()))
                    .execute();
        }
        return mapping;
    }
}