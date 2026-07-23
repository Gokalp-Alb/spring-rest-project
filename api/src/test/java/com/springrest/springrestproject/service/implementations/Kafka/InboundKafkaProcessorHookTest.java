package com.springrest.springrestproject.service.implementations.Kafka;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.SpringRestProjectApplication;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.KafkaTopic;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.repository.KafkaTopicRepo;
import com.springrest.springrestproject.repository.ScriptRepo;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Placed under api's test tree rather than domain's (as originally specified in the task brief):
// domain/pom.xml has no dependency on scripting, and structurally cannot (scripting already
// depends on domain in main scope, so the reverse would be a circular module dependency). Only
// api has both domain and scripting on its classpath, so only here can a real InboundKafkaProcessor
// and a real ScriptHookInvokerImpl coexist in the same Spring context (same fix applied to
// DataServiceHookIntegrationTest in Task 13 and OutboundKafkaPublisherHookTest in Task 14; see
// api/pom.xml for the domain test-jar dependency this relies on).
//
// classes = SpringRestProjectApplication.class is required: domain's test-jar also puts
// DomainTestApplication (itself @SpringBootApplication-annotated) on the classpath, so an
// unqualified @SpringBootTest scan finds two @SpringBootConfiguration classes and fails to boot.
@SpringBootTest(classes = SpringRestProjectApplication.class)
class InboundKafkaProcessorHookTest extends BaseIntegrationTest {

    @Autowired
    private InboundKafkaProcessor processor;
    @Autowired
    private IMetadataService metadataService;
    @Autowired
    private KafkaTopicRepo topicRepo;
    @Autowired
    private ScriptRepo scriptRepo;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onInboundTopicVetoPreventsTheApply() {
        String tableName = "inbound_hook_it_orders_1";
        createSimpleTable(tableName);
        KafkaTopic topic = topicRepo.save(new KafkaTopic(null, "inbound-hook-topic-1"));
        scriptRepo.save(new Script(null, ScriptType.KAFKA, null, topic.id(),
                "function onInboundTopic() { throw new Error('veto apply'); }"));

        processor.processInboundEvent("INSERT",
                Map.of("tableName", tableName, "rowData", Map.of("name", "test")),
                tableName, null, "inbound-hook-topic-1");

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        assertEquals(0, rowCount);
    }

    @Test
    void topicWithNoScriptAppliesNormally() {
        String tableName = "inbound_hook_it_orders_2";
        createSimpleTable(tableName);
        topicRepo.save(new KafkaTopic(null, "inbound-hook-topic-2"));

        processor.processInboundEvent("INSERT",
                Map.of("tableName", tableName, "rowData", Map.of("name", "test")),
                tableName, null, "inbound-hook-topic-2");

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        assertEquals(1, rowCount);
    }

    private void createSimpleTable(String tableName) {
        ColumnMetadata column = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(255)")
                .tableName(tableName)
                .build();
        metadataService.createTable(tableName, new TableCreateRequest(List.of(column), false), 1L);
    }
}
