package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.model.KafkaTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ScriptRepoTest extends BaseIntegrationTest {

    @Autowired
    private ScriptRepo repo;
    @Autowired
    private KafkaTopicRepo topicRepo;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long seedTable(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO sys_table_metadata (table_name, creator_id, created_date, last_updater_id, last_changed_date, is_audit_enabled, is_restricted) " +
                        "VALUES (?, 0, now(), 0, now(), false, false) RETURNING id",
                Long.class, name);
    }

    @Test
    void savesAndFindsDbScriptByTableId() {
        Long tableId = seedTable("script_repo_test_table_1");
        Script saved = repo.save(new Script(null, ScriptType.DB, tableId, null, "function beforeSaveToDB() {}"));

        assertTrue(saved.id() > 0);
        Optional<Script> found = repo.findByTableId(tableId);
        assertTrue(found.isPresent());
        assertEquals(ScriptType.DB, found.get().scriptType());
        assertEquals("function beforeSaveToDB() {}", found.get().scriptBody());
    }

    @Test
    void savesAndFindsKafkaScriptByTopicId() {
        KafkaTopic topic = topicRepo.save(new KafkaTopic(null, "script-repo-test-topic"));
        Script saved = repo.save(new Script(null, ScriptType.KAFKA, null, topic.id(), "function onInboundTopic() {}"));

        Optional<Script> found = repo.findByTopicId(topic.id());
        assertTrue(found.isPresent());
        assertEquals(saved.id(), found.get().id());
    }

    @Test
    void secondScriptForSameTableViolatesUniqueConstraint() {
        Long tableId = seedTable("script_repo_test_table_2");
        repo.save(new Script(null, ScriptType.DB, tableId, null, "function beforeSaveToDB() {}"));

        assertThrows(Exception.class, () ->
                repo.save(new Script(null, ScriptType.DB, tableId, null, "function afterSaveToDB() {}")));
    }

    @Test
    void deleteFreesTheSlotForANewScript() {
        Long tableId = seedTable("script_repo_test_table_3");
        Script first = repo.save(new Script(null, ScriptType.DB, tableId, null, "function beforeSaveToDB() {}"));

        repo.delete(first.id());

        Script second = repo.save(new Script(null, ScriptType.DB, tableId, null, "function afterSaveToDB() {}"));
        assertNotEquals(first.id(), second.id());
        assertTrue(repo.findByTableId(tableId).isPresent());
    }

    @Test
    void deleteWritesToShadowLogTable() {
        Long tableId = seedTable("script_repo_test_table_4");
        Script saved = repo.save(new Script(null, ScriptType.DB, tableId, null, "function beforeSaveToDB() {}"));

        repo.delete(saved.id());

        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_scripts_log WHERE id = ? AND operation_type = 'DELETE'",
                Integer.class, saved.id());
        assertEquals(1, logCount);
    }

    @Test
    void updatePreservesIdAndChangesOnlyScriptBody() {
        Long tableId = seedTable("script_repo_test_table_5");
        Script saved = repo.save(new Script(null, ScriptType.DB, tableId, null, "function beforeSaveToDB() {}"));

        Script updated = repo.update(Script.builder()
                .id(saved.id()).scriptType(saved.scriptType()).tableId(saved.tableId()).topicId(saved.topicId())
                .scriptBody("function beforeSaveToDB() { console.log('changed'); }")
                .build());

        assertEquals(saved.id(), updated.id());
        Script found = repo.findByTableId(tableId).orElseThrow();
        assertEquals(saved.id(), found.id());
        assertEquals("function beforeSaveToDB() { console.log('changed'); }", found.scriptBody());
    }

    @Test
    void updateWritesToShadowLogTableAsPut() {
        Long tableId = seedTable("script_repo_test_table_6");
        Script saved = repo.save(new Script(null, ScriptType.DB, tableId, null, "function beforeSaveToDB() {}"));

        repo.update(Script.builder()
                .id(saved.id()).scriptType(saved.scriptType()).tableId(saved.tableId()).topicId(saved.topicId())
                .scriptBody("function beforeSaveToDB() { console.log('changed'); }")
                .build());

        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_scripts_log WHERE id = ? AND operation_type = 'PUT'",
                Integer.class, saved.id());
        assertEquals(1, logCount);
    }
}
