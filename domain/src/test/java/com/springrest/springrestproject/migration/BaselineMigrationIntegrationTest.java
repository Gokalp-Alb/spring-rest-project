package com.springrest.springrestproject.migration;

import com.springrest.springrestproject.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BaselineMigrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sysAppUsersHasNoRoleColumnAndHasSystemColumns() {
        Integer roleColCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_app_users' AND column_name = 'role'",
                Integer.class);
        assertEquals(0, roleColCount);

        for (String col : new String[]{"creator_id", "created_date", "last_updater_id", "last_changed_date", "is_restricted"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_app_users' AND column_name = ?",
                    Integer.class, col);
            assertEquals(1, count, "sys_app_users missing column: " + col);
        }
    }

    @Test
    void mcpAgentHasIdOne() {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_app_users WHERE username = 'mcp_agent'", Long.class);
        assertEquals(1L, id);
    }

    @Test
    void sysUserGroupsHasSixRestrictedDefaultRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_groups WHERE is_restricted = true", Integer.class);
        assertEquals(6, count);
        Integer names = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_groups WHERE group_name IN ('ADMIN','REGISTERED_USER','SCRIPT_ENGINEER','KAFKA_ENGINEER','MCP_AGENT','DATABASE_ADMIN')",
                Integer.class);
        assertEquals(6, names);
    }

    @Test
    void mcpAgentIsOnlyInMcpAgentGroup() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_app_users_sys_user_groups_jt jt " +
                        "JOIN sys_user_groups g ON jt.group_id = g.id " +
                        "WHERE jt.user_id = 1", Integer.class);
        assertEquals(1, count);
        String groupName = jdbcTemplate.queryForObject(
                "SELECT g.group_name FROM sys_app_users_sys_user_groups_jt jt " +
                        "JOIN sys_user_groups g ON jt.group_id = g.id " +
                        "WHERE jt.user_id = 1", String.class);
        assertEquals("MCP_AGENT", groupName);
    }

    @Test
    void sysExecutionLogEntriesHasForeignKeyToSysExecutionLogs() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE table_name = 'sys_execution_log_entries' AND constraint_type = 'FOREIGN KEY'",
                Integer.class);
        assertEquals(1, count);
    }

    @Test
    void registryContainsSelfReferentialRows() {
        for (String tbl : new String[]{"sys_table_metadata", "sys_column_metadata", "sys_relation_metadata",
                "sys_app_users", "sys_user_groups", "sys_app_users_sys_user_groups_jt",
                "sys_kafka_table_mappings", "sys_ddl_log", "sys_execution_logs", "sys_execution_log_entries"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_table_metadata WHERE table_name = ? AND is_restricted = true",
                    Integer.class, tbl);
            assertEquals(1, count, "sys_table_metadata missing restricted row for: " + tbl);
        }
    }

    @Test
    void relationMetadataHasTwoSystemRelations() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_relation_metadata WHERE is_restricted = true", Integer.class);
        assertEquals(2, count);
    }

    @Test
    void sysKafkaTopicsTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'sys_kafka_topics'",
                Integer.class);
        assertEquals(1, count);
    }

    @Test
    void sysKafkaTableMappingsHasTopicIdNotKafkaTopic() {
        Integer topicIdCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_kafka_table_mappings' AND column_name = 'topic_id'",
                Integer.class);
        assertEquals(1, topicIdCount);
        Integer kafkaTopicCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_kafka_table_mappings' AND column_name = 'kafka_topic'",
                Integer.class);
        assertEquals(0, kafkaTopicCount);
    }

    @Test
    void sysScriptsTableExistsWithTypeConstraintAndUniqueIndexes() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'sys_scripts'", Integer.class);
        assertEquals(1, tableCount);

        Integer checkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE table_name = 'sys_scripts' AND constraint_type = 'CHECK' AND constraint_name = 'sys_scripts_type_target_chk'",
                Integer.class);
        assertEquals(1, checkCount);

        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sys_scripts' " +
                        "AND indexname IN ('sys_scripts_one_per_table', 'sys_scripts_one_per_topic')",
                Integer.class);
        assertEquals(2, indexCount);
    }

    @Test
    void sysScriptsLogTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'sys_scripts_log'", Integer.class);
        assertEquals(1, count);
    }

    @Test
    void sysExecutionLogsHasScriptIdNotScript() {
        Integer scriptIdCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_execution_logs' AND column_name = 'script_id'",
                Integer.class);
        assertEquals(1, scriptIdCount);
        Integer scriptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_execution_logs' AND column_name = 'script'",
                Integer.class);
        assertEquals(0, scriptCount);
    }

    @Test
    void newSysTablesAreRegisteredAndRestricted() {
        for (String tbl : new String[]{"sys_kafka_topics", "sys_scripts", "sys_scripts_log"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_table_metadata WHERE table_name = ? AND is_restricted = true",
                    Integer.class, tbl);
            assertEquals(1, count, "missing registry row for: " + tbl);
        }
    }
}
