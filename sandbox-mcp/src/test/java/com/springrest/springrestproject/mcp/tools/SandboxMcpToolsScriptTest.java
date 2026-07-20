package com.springrest.springrestproject.mcp.tools;

import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.scripting.model.ScriptCaller;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.response.scripting.ScriptExecutionResponse;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IDatabaseManagementService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IPersonalAccessTokenService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxMcpToolsScriptTest {

    private final IMetadataService metadataService = mock(IMetadataService.class);
    private final IDataService dataService = mock(IDataService.class);
    private final IRelationService relationService = mock(IRelationService.class);
    private final IUserService userService = mock(IUserService.class);
    private final DataSource sandboxDataSource = mock(DataSource.class);
    private final IDatabaseManagementService databaseManagementService = mock(IDatabaseManagementService.class);
    private final IPersonalAccessTokenService patService = mock(IPersonalAccessTokenService.class);
    private final ScriptExecutionService scriptExecutionService = mock(ScriptExecutionService.class);

    private SandboxMcpTools sandboxMcpTools;

    @BeforeEach
    void setUp() {
        sandboxMcpTools = new SandboxMcpTools(metadataService, dataService, relationService, userService,
                sandboxDataSource, databaseManagementService, patService, scriptExecutionService);
        ReflectionTestUtils.setField(sandboxMcpTools, "mcpPat", "pat_test_token");
    }

    @Test
    void executeScript_resolvesUserFromPatAndDelegatesToScriptExecutionService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(7L);
        ScriptExecutionResponse expected = new ScriptExecutionResponse(3, List.of());
        when(scriptExecutionService.execute(eq("1+2;"), any(ScriptCaller.class), eq(false))).thenReturn(expected);

        ScriptExecutionResponse actual = sandboxMcpTools.executeScript("1+2;");

        assertEquals(expected, actual);
        verify(scriptExecutionService).execute(
                eq("1+2;"),
                argThat(caller -> "7".equals(caller.userId()) && caller.roles().contains("MCP")),
                eq(false));
    }

    @Test
    void executeScript_withoutPat_throwsUnauthorizedAndNeverCallsService() {
        ReflectionTestUtils.setField(sandboxMcpTools, "mcpPat", "");

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                sandboxMcpTools.executeScript("1;")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(scriptExecutionService, never()).execute(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void executeScript_propagatesUnauthorizedWhenPatUserLacksScriptEngineerRole() {
        // Same rationale as McpToolsScriptTest's equivalent case: the SCRIPT_ENGINEER group
        // check lives in ScriptExecutionService (backed by AppUserRepo), not in this MCP tool.
        // A valid PAT for a user without the group must still surface UNAUTHORIZED_ACCESS
        // unchanged through this layer.
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(99L);
        when(scriptExecutionService.execute(eq("1;"), any(ScriptCaller.class), eq(false)))
                .thenThrow(new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Caller does not have SCRIPT_ENGINEER role"));

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                sandboxMcpTools.executeScript("1;")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(scriptExecutionService).execute(
                eq("1;"),
                argThat(caller -> "99".equals(caller.userId())),
                eq(false));
    }
}
