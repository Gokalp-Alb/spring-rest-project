package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.model.KafkaTableMapping;
import com.springrest.springrestproject.model.KafkaTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class KafkaTableMappingRepoGovernanceTest extends BaseIntegrationTest {

    @Autowired
    private KafkaTableMappingRepo repo;
    @Autowired
    private KafkaTopicRepo topicRepo;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cannotUpdateRestrictedMapping() {
        KafkaTopic topic = topicRepo.save(new KafkaTopic(null, "orders-topic"));
        KafkaTableMapping saved = repo.save(KafkaTableMapping.builder()
                .tableName("orders").topicId(topic.id()).direction("OUTBOUND").active(true).build());
        jdbcTemplate.update("UPDATE sys_kafka_table_mappings SET is_restricted = true WHERE id = ?", saved.id());

        ApplicationException ex = assertThrows(ApplicationException.class, () -> repo.save(
                KafkaTableMapping.builder().id(saved.id()).tableName("orders").topicId(topic.id())
                        .direction("OUTBOUND").active(false).build()));
        assertEquals(ErrorCode.RESTRICTED_ROW_MUTATION, ex.getErrorCode());
    }
}
