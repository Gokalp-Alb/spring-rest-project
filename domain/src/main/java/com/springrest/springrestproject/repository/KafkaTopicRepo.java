package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.KafkaTopic;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.SYS_KAFKA_TOPICS;

@Repository
@RequiredArgsConstructor
public class KafkaTopicRepo {
    private final DSLContext dsl;

    public Optional<KafkaTopic> findByTopicName(String topicName) {
        return dsl.selectFrom(SYS_KAFKA_TOPICS)
                .where(SYS_KAFKA_TOPICS.TOPIC_NAME.eq(topicName))
                .fetchOptional(this::toKafkaTopic);
    }

    public Optional<KafkaTopic> findById(Long id) {
        return dsl.selectFrom(SYS_KAFKA_TOPICS)
                .where(SYS_KAFKA_TOPICS.ID.eq(id))
                .fetchOptional(this::toKafkaTopic);
    }

    private KafkaTopic toKafkaTopic(Record record) {
        return KafkaTopic.builder()
                .id(record.get(SYS_KAFKA_TOPICS.ID))
                .topicName(record.get(SYS_KAFKA_TOPICS.TOPIC_NAME))
                .build();
    }

    public KafkaTopic save(KafkaTopic topic) {
        Long generatedId = Objects.requireNonNull(dsl.insertInto(SYS_KAFKA_TOPICS)
                        .set(SYS_KAFKA_TOPICS.TOPIC_NAME, topic.topicName())
                        .set(SYS_KAFKA_TOPICS.CREATOR_ID, 0L)
                        .set(SYS_KAFKA_TOPICS.CREATED_DATE, java.time.LocalDateTime.now())
                        .set(SYS_KAFKA_TOPICS.LAST_UPDATER_ID, 0L)
                        .set(SYS_KAFKA_TOPICS.LAST_CHANGED_DATE, java.time.LocalDateTime.now())
                        .set(SYS_KAFKA_TOPICS.IS_RESTRICTED, true)
                        .returning(SYS_KAFKA_TOPICS.ID)
                        .fetchOne())
                .getValue(SYS_KAFKA_TOPICS.ID);
        return KafkaTopic.builder().id(generatedId).topicName(topic.topicName()).build();
    }
}
