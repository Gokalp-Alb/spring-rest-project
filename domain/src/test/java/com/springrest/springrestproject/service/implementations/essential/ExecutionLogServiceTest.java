package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.model.ExecutionLog;
import com.springrest.springrestproject.repository.ExecutionLogRepo;
import com.springrest.springrestproject.service.interfaces.IExecutionLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExecutionLogServiceTest extends BaseIntegrationTest {

    @Autowired
    private IExecutionLogService executionLogService;

    @Autowired
    private ExecutionLogRepo executionLogRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM sys_execution_log_entries");
        jdbcTemplate.execute("DELETE FROM sys_execution_logs");
    }

    @Test
    void shouldLogStartAndRetrieve() {
        String execId = UUID.randomUUID().toString();
        Long scriptId = null;
        String caller = "test_caller";

        ExecutionLog log = executionLogService.logStart(execId, scriptId, caller);
        assertNotNull(log);
        assertNotNull(log.id());
        assertEquals(execId, log.executionId());
        assertEquals(scriptId, log.scriptId());
        assertEquals(caller, log.caller());
        assertEquals(ExecutionStatus.RUNNING, log.status());
        assertNotNull(log.createdAt());
        assertNull(log.finishedAt());

        Optional<ExecutionLog> found = executionLogRepo.findByExecutionId(execId);
        assertTrue(found.isPresent());
        assertEquals(ExecutionStatus.RUNNING, found.get().status());
    }

    @Test
    void shouldLogSuccess() {
        String execId = UUID.randomUUID().toString();
        executionLogService.logStart(execId, null, "test_caller");

        executionLogService.logSuccess(execId, "1");

        Optional<ExecutionLog> found = executionLogRepo.findByExecutionId(execId);
        assertTrue(found.isPresent());
        assertEquals(ExecutionStatus.SUCCESS, found.get().status());
        assertEquals("1", found.get().output());
        assertNotNull(found.get().durationMs());
        assertTrue(found.get().durationMs() >= 0);
        assertNotNull(found.get().finishedAt());
        assertNull(found.get().errorMessage());
    }

    @Test
    void shouldLogFailure() {
        String execId = UUID.randomUUID().toString();
        executionLogService.logStart(execId, null, "test_caller");

        executionLogService.logFailure(execId, "Syntax error at line 1");

        Optional<ExecutionLog> found = executionLogRepo.findByExecutionId(execId);
        assertTrue(found.isPresent());
        assertEquals(ExecutionStatus.FAILED, found.get().status());
        assertNotNull(found.get().durationMs());
        assertNotNull(found.get().finishedAt());
        assertEquals("Syntax error at line 1", found.get().errorMessage());
    }

    @Test
    void shouldLogFailureWithExplicitStatus() {
        String execId = UUID.randomUUID().toString();
        executionLogService.logStart(execId, null, "test_caller");

        executionLogService.logFailure(execId, "BAD_REQUEST: [Failed to parse query]", ExecutionStatus.TIMEOUT);

        Optional<ExecutionLog> found = executionLogRepo.findByExecutionId(execId);
        assertTrue(found.isPresent());
        assertEquals(ExecutionStatus.TIMEOUT, found.get().status());
        assertNotNull(found.get().durationMs());
        assertNotNull(found.get().finishedAt());
        assertEquals("BAD_REQUEST: [Failed to parse query]", found.get().errorMessage());
    }

    @Test
    void shouldAppendEntriesInSequenceOrder() {
        String execId = UUID.randomUUID().toString();
        executionLogService.logStart(execId, null, "test_caller");

        executionLogService.append(execId, com.springrest.springrestproject.core.model.scripting.LogLevel.INFO, "a", null);
        executionLogService.append(execId, com.springrest.springrestproject.core.model.scripting.LogLevel.WARN, "b", null);

        java.util.List<com.springrest.springrestproject.core.model.scripting.ExecutionLogEntry> entries =
                executionLogService.getEntries(execId);

        assertEquals(2, entries.size());
        assertEquals("a", entries.get(0).message());
        assertEquals(com.springrest.springrestproject.core.model.scripting.LogLevel.INFO, entries.get(0).level());
        assertEquals(1, entries.get(0).sequenceNumber());
        assertEquals("b", entries.get(1).message());
        assertEquals(com.springrest.springrestproject.core.model.scripting.LogLevel.WARN, entries.get(1).level());
        assertEquals(2, entries.get(1).sequenceNumber());
    }

    @Test
    void shouldReturnEmptyListWhenNoEntriesLogged() {
        String execId = UUID.randomUUID().toString();
        executionLogService.logStart(execId, null, "test_caller");

        assertTrue(executionLogService.getEntries(execId).isEmpty());
    }

    @Test
    void shouldFailOnNonExistentExecutionId() {
        String randomExecId = UUID.randomUUID().toString();

        ApplicationException successEx = assertThrows(ApplicationException.class, () ->
                executionLogService.logSuccess(randomExecId, "output")
        );
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, successEx.getErrorCode());

        ApplicationException failureEx = assertThrows(ApplicationException.class, () ->
                executionLogService.logFailure(randomExecId, "error message")
        );
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, failureEx.getErrorCode());
    }
}
