package com.springrest.scripting.engine;

import com.springrest.scripting.model.CallerOrigin;
import com.springrest.scripting.model.ScriptCaller;
import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.DomainTestApplication;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.dto.response.scripting.ScriptExecutionResponse;
import com.springrest.springrestproject.model.ExecutionLog;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.repository.ExecutionLogRepo;
import com.springrest.springrestproject.service.implementations.ExecutionLogService;
import com.springrest.springrestproject.service.interfaces.IDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// classes = DomainTestApplication.class is required here (unlike domain's own @SpringBootTest
// classes, which don't need it): Spring Boot's @SpringBootTest only searches packages upward
// from the test class for a @SpringBootConfiguration, and this test lives in
// com.springrest.scripting.engine, a sibling package tree to DomainTestApplication's
// com.springrest.springrestproject, not an ancestor of it.
//
// script.execution.* is normally supplied by api/src/main/resources/application.properties,
// which isn't on this module's test classpath; without it timeoutMs binds to the primitive
// default of 0 and every script - even trivial ones - gets cancelled by the watchdog
// immediately. Supply the same values here explicitly.
@SpringBootTest(classes = DomainTestApplication.class)
@TestPropertySource(properties = {
        "script.execution.timeout-ms=5000",
        "script.execution.memory-limit-mb=64"
})
class ScriptExecutionServiceTest extends BaseIntegrationTest {

    @Autowired
    private ScriptExecutionService scriptExecutionService;

    @Autowired
    private ExecutionLogService logService;

    @Autowired
    private IDataService dataService;

    @Autowired
    private ExecutionLogRepo executionLogRepo;

    @Autowired
    private AppUserRepo appUserRepo;

    @Autowired
    private ScriptExecutionProperties executionProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long scriptEngineerUserId;
    private Long noGroupUserId;

    @BeforeEach
    void setUpCallers() {
        jdbcTemplate.execute("DELETE FROM sys_app_users WHERE username IN ('script_exec_test_engineer', 'script_exec_test_no_group')");

        AppUser engineer = AppUser.builder()
                .username("script_exec_test_engineer")
                .password(passwordEncoder.encode("password123"))
                .active(true)
                .build();
        scriptEngineerUserId = appUserRepo.save(engineer).id();
        appUserRepo.saveGroup(scriptEngineerUserId, GroupName.SCRIPT_ENGINEER, 1L);

        AppUser noGroupUser = AppUser.builder()
                .username("script_exec_test_no_group")
                .password(passwordEncoder.encode("password123"))
                .active(true)
                .build();
        noGroupUserId = appUserRepo.save(noGroupUser).id();
    }

    // NOTE: sys_execution_logs no longer stores the raw script text as of Task 8's schema change
    // (only script_id, which is null for ad-hoc executions) - so the most recent log row for the
    // given caller is used to identify the just-completed execution instead of matching on script
    // text. Each test recreates its callers in @BeforeEach and drives exactly one relevant
    // execution per caller, so "latest row for this caller" is unambiguous.
    private ExecutionLog findLogByScript(String callerId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_execution_logs WHERE caller = ? ORDER BY id DESC LIMIT 1",
                Long.class, callerId);
        String executionId = jdbcTemplate.queryForObject(
                "SELECT execution_id FROM sys_execution_logs WHERE id = ?", String.class, id);
        Optional<ExecutionLog> found = executionLogRepo.findByExecutionId(executionId);
        assertTrue(found.isPresent());
        return found.get();
    }

    @Test
    void execute_returnsResultAndCollectedLogs() {
        ScriptCaller caller = new ScriptCaller(String.valueOf(scriptEngineerUserId), Set.of(), CallerOrigin.USER_SUBMITTED);
        String script = "console.log('hello'); console.warn('careful'); 1 + 2;";

        ScriptExecutionResponse response = scriptExecutionService.executeAdhoc(script, caller, false);

        assertEquals(3, response.result());
        assertEquals(2, response.logs().size());
        assertEquals("INFO", response.logs().get(0).level().name());
        assertEquals("hello", response.logs().get(0).message());
        assertEquals("WARN", response.logs().get(1).level().name());
        assertEquals("careful", response.logs().get(1).message());
    }

    @Test
    void execute_throwsWhenCallerLacksScriptEngineerRole() {
        ScriptCaller caller = new ScriptCaller(String.valueOf(noGroupUserId), Set.of(), CallerOrigin.USER_SUBMITTED);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                scriptExecutionService.executeAdhoc("1;", caller, false)
        );
        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
    }

    @Test
    void execute_throwsWhenScriptExceedsSizeLimit() {
        ScriptCaller caller = new ScriptCaller(String.valueOf(scriptEngineerUserId), Set.of(), CallerOrigin.USER_SUBMITTED);
        String oversized = "1".repeat(100_001);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                scriptExecutionService.executeAdhoc(oversized, caller, false)
        );
        assertEquals(ErrorCode.SCRIPT_INVALID_PAYLOAD, ex.getErrorCode());
    }

    @Test
    void execute_wrapsRuntimeScriptErrorsAsApplicationException() {
        ScriptCaller caller = new ScriptCaller(String.valueOf(scriptEngineerUserId), Set.of(), CallerOrigin.USER_SUBMITTED);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                scriptExecutionService.executeAdhoc("throw new Error('boom');", caller, false)
        );
        assertEquals(ErrorCode.SCRIPT_QUERY_FAILED, ex.getErrorCode());
    }

    /**
     * Regression test for the GraalVM 24.0.2 double-close hazard: evalWithTimeout's watchdog
     * calls context.close(true) on a background thread when the script exceeds the configured
     * timeout, and any subsequent close() on that same context deterministically rethrows
     * PolyglotException(isCancelled()==true). This drives execute() through an actual timeout
     * (using a short-timeout instance of the service, constructed with the real, autowired
     * collaborators) to verify the service's own close-in-finally handling does not let that
     * second close-exception mask/replace the real failure, and that the caller still sees a
     * single, correctly-classified ApplicationException(SCRIPT_QUERY_FAILED).
     */
    @Test
    void execute_cancelsScriptThatExceedsTimeoutWithoutDoubleCloseMaskingTheFailure() {
        ScriptCaller caller = new ScriptCaller(String.valueOf(scriptEngineerUserId), Set.of(), CallerOrigin.USER_SUBMITTED);
        Environment nonProdEnvironment = mock(Environment.class);
        when(nonProdEnvironment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        ScriptExecutionService shortTimeoutService = new ScriptExecutionService(
                logService, dataService, new ScriptExecutionProperties(200L, 64), appUserRepo, nonProdEnvironment);
        String script = "while(true){} // timeout-regression-" + System.nanoTime();

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                shortTimeoutService.executeAdhoc(script, caller, false)
        );

        assertEquals(ErrorCode.SCRIPT_QUERY_FAILED, ex.getErrorCode());

        // Persisted status must distinguish a watchdog timeout from an ordinary runtime FAILED
        // execution - see Finding 1 of the final whole-branch review.
        ExecutionLog persisted = findLogByScript(String.valueOf(scriptEngineerUserId));
        assertEquals(ExecutionStatus.TIMEOUT, persisted.status());
    }

    /**
     * Regression test for Finding 2 of the final whole-branch review: ApplicationException is
     * constructed with `super(errorCode.getMessageKey())`, so getMessage() returns a bare
     * message key like "error.bad_request" rather than the actual detail carried in getArgs().
     * TablesProxy wraps a malformed tables.select() query as
     * ApplicationException(BAD_REQUEST, "Failed to parse query: ...") - this drives that path
     * and asserts the persisted sys_execution_logs.error_message contains the resolved error code
     * name and detail, not just the raw key.
     */
    @Test
    void execute_persistsResolvedApplicationExceptionDetailNotBareMessageKey() {
        ScriptCaller caller = new ScriptCaller(String.valueOf(scriptEngineerUserId), Set.of(), CallerOrigin.USER_SUBMITTED);
        String script = "tables.select({tableName: 'test_table', page: 'not-a-number'}); // " + System.nanoTime();

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                scriptExecutionService.executeAdhoc(script, caller, false)
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());

        ExecutionLog persisted = findLogByScript(String.valueOf(scriptEngineerUserId));
        assertEquals(ExecutionStatus.FAILED, persisted.status());
        assertNotEquals(ErrorCode.BAD_REQUEST.getMessageKey(), persisted.errorMessage());
        assertTrue(persisted.errorMessage().contains("BAD_REQUEST"));
        assertTrue(persisted.errorMessage().contains("Failed to parse query"));
    }

    @Test
    void executeAdhocThrowsWhenProfileIsProduction() {
        Environment prodEnvironment = mock(Environment.class);
        when(prodEnvironment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        ScriptExecutionService prodService = new ScriptExecutionService(logService, dataService, executionProperties, appUserRepo, prodEnvironment);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                prodService.executeAdhoc("1+1", new ScriptCaller("1", Set.of("SCRIPT_ENGINEER"), CallerOrigin.USER_SUBMITTED), false));
        assertEquals(ErrorCode.SCRIPT_EXECUTION_DISABLED, ex.getErrorCode());
    }
}
