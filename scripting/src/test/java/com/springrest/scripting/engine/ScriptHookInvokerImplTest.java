package com.springrest.scripting.engine;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.DomainTestApplication;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.hooks.ScriptHookSession;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.repository.ScriptRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// classes = DomainTestApplication.class is required for the same reason documented on
// ScriptExecutionServiceTest: this test lives in com.springrest.scripting.engine, a sibling
// package tree to DomainTestApplication's com.springrest.springrestproject, not an ancestor of
// it, so @SpringBootTest's upward package search would not find a @SpringBootConfiguration
// without this. script.execution.* properties are likewise not on this module's test classpath
// otherwise (see ScriptExecutionServiceTest for the same note).
@SpringBootTest(classes = DomainTestApplication.class)
@TestPropertySource(properties = {
        "script.execution.timeout-ms=5000",
        "script.execution.memory-limit-mb=64"
})
class ScriptHookInvokerImplTest extends BaseIntegrationTest {

    @Autowired
    private ScriptHookInvokerImpl invoker;
    @Autowired
    private ScriptRepo scriptRepo;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long seedTable(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO sys_table_metadata (table_name, creator_id, created_date, last_updater_id, last_changed_date, is_audit_enabled, is_restricted) " +
                        "VALUES (?, 0, now(), 0, now(), false, false) RETURNING id",
                Long.class, name);
    }

    @Test
    void returnsEmptyWhenNoScriptForTable() {
        Long tableId = seedTable("hook_invoker_test_table_1");
        Optional<ScriptHookSession> session = invoker.openDbHookSession(tableId, 1L);
        assertTrue(session.isEmpty());
    }

    @Test
    void invokesDefinedFunctionAndLogsSuccess() {
        Long tableId = seedTable("hook_invoker_test_table_2");
        scriptRepo.save(new Script(null, ScriptType.DB, tableId, null,
                "function beforeSaveToDB() { console.log('ran'); }"));

        try (ScriptHookSession session = invoker.openDbHookSession(tableId, 1L).orElseThrow()) {
            session.invokeIfDefined("beforeSaveToDB");
            session.invokeIfDefined("afterSaveToDB");
        }

        Integer successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_execution_logs WHERE status = 'SUCCESS'", Integer.class);
        assertTrue(successCount >= 1);
    }

    @Test
    void undefinedFunctionIsANoOp() {
        Long tableId = seedTable("hook_invoker_test_table_3");
        scriptRepo.save(new Script(null, ScriptType.DB, tableId, null, "function beforeSaveToDB() {}"));

        try (ScriptHookSession session = invoker.openDbHookSession(tableId, 1L).orElseThrow()) {
            assertDoesNotThrow(() -> session.invokeIfDefined("afterSaveToDB"));
        }
    }

    @Test
    void throwingFunctionRaisesHookExecutionFailedAndLogsFailure() {
        Long tableId = seedTable("hook_invoker_test_table_4");
        scriptRepo.save(new Script(null, ScriptType.DB, tableId, null,
                "function beforeSaveToDB() { throw new Error('nope'); }"));

        ScriptHookSession session = invoker.openDbHookSession(tableId, 1L).orElseThrow();
        ApplicationException ex = assertThrows(ApplicationException.class, () -> session.invokeIfDefined("beforeSaveToDB"));
        assertEquals(ErrorCode.SCRIPT_HOOK_EXECUTION_FAILED, ex.getErrorCode());
        session.close();

        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_execution_logs WHERE status = 'FAILED' AND error_message LIKE '%nope%'", Integer.class);
        assertEquals(1, failedCount);
    }
}
