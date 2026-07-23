package com.springrest.springrestproject.mcp.tools;

import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.scripting.model.ScriptCaller;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.dto.response.scripting.ScriptExecutionResponse;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.service.implementations.Kafka.KafkaMappingService;
import com.springrest.springrestproject.service.implementations.ScriptManagementService;
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
    private final ScriptManagementService scriptManagementService = mock(ScriptManagementService.class);
    private final KafkaMappingService kafkaMappingService = mock(KafkaMappingService.class);

    private SandboxMcpTools sandboxMcpTools;

    @BeforeEach
    void setUp() {
        sandboxMcpTools = new SandboxMcpTools(metadataService, dataService, relationService, userService,
                sandboxDataSource, databaseManagementService, patService, scriptExecutionService,
                scriptManagementService, kafkaMappingService);
        ReflectionTestUtils.setField(sandboxMcpTools, "mcpPat", "pat_test_token");
    }

    @Test
    void executeScript_resolvesUserFromPatAndDelegatesToScriptExecutionService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(7L);
        ScriptExecutionResponse expected = new ScriptExecutionResponse(3, List.of());
        when(scriptExecutionService.executeAdhoc(eq("1+2;"), any(ScriptCaller.class), eq(false))).thenReturn(expected);

        ScriptExecutionResponse actual = sandboxMcpTools.executeScript("1+2;");

        assertEquals(expected, actual);
        verify(scriptExecutionService).executeAdhoc(
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
        verify(scriptExecutionService, never()).executeAdhoc(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void executeScript_propagatesUnauthorizedWhenPatUserLacksScriptEngineerRole() {
        // Same rationale as McpToolsScriptTest's equivalent case: the SCRIPT_ENGINEER group
        // check lives in ScriptExecutionService (backed by AppUserRepo), not in this MCP tool.
        // A valid PAT for a user without the group must still surface UNAUTHORIZED_ACCESS
        // unchanged through this layer.
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(99L);
        when(scriptExecutionService.executeAdhoc(eq("1;"), any(ScriptCaller.class), eq(false)))
                .thenThrow(new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Caller does not have SCRIPT_ENGINEER role"));

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                sandboxMcpTools.executeScript("1;")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(scriptExecutionService).executeAdhoc(
                eq("1;"),
                argThat(caller -> "99".equals(caller.userId())),
                eq(false));
    }

    @Test
    void createScript_resolvesUserFromPatAndDelegatesToScriptManagementService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(7L);
        Script expected = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("x;").build();
        when(scriptManagementService.createScript(ScriptType.DB, 5L, null, "x;", 7L)).thenReturn(expected);

        Script actual = sandboxMcpTools.createScript(ScriptType.DB, 5L, null, "x;");

        assertEquals(expected, actual);
        verify(scriptManagementService).createScript(ScriptType.DB, 5L, null, "x;", 7L);
    }

    @Test
    void createScript_withoutPat_throwsUnauthorizedAndNeverCallsService() {
        ReflectionTestUtils.setField(sandboxMcpTools, "mcpPat", "");

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                sandboxMcpTools.createScript(ScriptType.DB, 5L, null, "x;")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(scriptManagementService, never()).createScript(any(), any(), any(), any(), any());
    }

    @Test
    void updateScript_resolvesUserFromPatAndDelegatesToScriptManagementService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(7L);
        Script expected = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("y;").build();
        when(scriptManagementService.updateScript(1L, "y;", 7L)).thenReturn(expected);

        Script actual = sandboxMcpTools.updateScript(1L, "y;");

        assertEquals(expected, actual);
        verify(scriptManagementService).updateScript(1L, "y;", 7L);
    }

    @Test
    void deleteScript_resolvesUserFromPatAndDelegatesToScriptManagementService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(7L);

        sandboxMcpTools.deleteScript(1L);

        verify(scriptManagementService).deleteScript(1L, 7L);
    }

    @Test
    void getScript_delegatesToScriptManagementServiceWithoutRequiringPat() {
        ReflectionTestUtils.setField(sandboxMcpTools, "mcpPat", "");
        Script expected = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("z;").build();
        when(scriptManagementService.getScript(1L)).thenReturn(expected);

        Script actual = sandboxMcpTools.getScript(1L);

        assertEquals(expected, actual);
    }

    @Test
    void listScripts_delegatesToScriptManagementServiceWithoutRequiringPat() {
        ReflectionTestUtils.setField(sandboxMcpTools, "mcpPat", "");
        Script script = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("z;").build();
        when(scriptManagementService.listScripts()).thenReturn(List.of(script));

        List<Script> actual = sandboxMcpTools.listScripts();

        assertEquals(List.of(script), actual);
    }
}
