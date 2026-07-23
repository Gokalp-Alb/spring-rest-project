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

class McpToolsScriptTest {

    private final IMetadataService metadataService = mock(IMetadataService.class);
    private final IDataService dataService = mock(IDataService.class);
    private final IRelationService relationService = mock(IRelationService.class);
    private final IUserService userService = mock(IUserService.class);
    private final IPersonalAccessTokenService patService = mock(IPersonalAccessTokenService.class);
    private final IDatabaseManagementService databaseManagementService = mock(IDatabaseManagementService.class);
    private final ScriptExecutionService scriptExecutionService = mock(ScriptExecutionService.class);
    private final ScriptManagementService scriptManagementService = mock(ScriptManagementService.class);
    private final KafkaMappingService kafkaMappingService = mock(KafkaMappingService.class);

    private McpTools mcpTools;

    @BeforeEach
    void setUp() {
        mcpTools = new McpTools(metadataService, dataService, relationService, userService,
                patService, databaseManagementService, scriptExecutionService, scriptManagementService,
                kafkaMappingService);
        ReflectionTestUtils.setField(mcpTools, "mcpPat", "pat_test_token");
    }

    @Test
    void executeScript_resolvesUserFromPatAndDelegatesToScriptExecutionService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(42L);
        ScriptExecutionResponse expected = new ScriptExecutionResponse(3, List.of());
        when(scriptExecutionService.executeAdhoc(eq("1+2;"), any(ScriptCaller.class), eq(false))).thenReturn(expected);

        ScriptExecutionResponse actual = mcpTools.executeScript("1+2;");

        assertEquals(expected, actual);
        verify(scriptExecutionService).executeAdhoc(
                eq("1+2;"),
                argThat(caller -> "42".equals(caller.userId()) && caller.roles().contains("MCP")),
                eq(false));
    }

    @Test
    void executeScript_withoutPat_throwsUnauthorizedAndNeverCallsService() {
        ReflectionTestUtils.setField(mcpTools, "mcpPat", "");

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                mcpTools.executeScript("1;")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(scriptExecutionService, never()).executeAdhoc(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void executeScript_propagatesUnauthorizedWhenPatUserLacksScriptEngineerRole() {
        // The MCP tool itself does not check the SCRIPT_ENGINEER group - ScriptExecutionService
        // does, by querying AppUserRepo for the PAT-resolved user's groups. This test simulates
        // that DB-backed rejection (a valid PAT for a user without the group) and asserts the
        // MCP layer does not swallow or downgrade it - the caller must see the same
        // UNAUTHORIZED_ACCESS the REST endpoint would produce for the same user.
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(99L);
        when(scriptExecutionService.executeAdhoc(eq("1;"), any(ScriptCaller.class), eq(false)))
                .thenThrow(new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Caller does not have SCRIPT_ENGINEER role"));

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                mcpTools.executeScript("1;")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(scriptExecutionService).executeAdhoc(
                eq("1;"),
                argThat(caller -> "99".equals(caller.userId())),
                eq(false));
    }

    @Test
    void createScript_resolvesUserFromPatAndDelegatesToScriptManagementService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(42L);
        Script expected = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("x;").build();
        when(scriptManagementService.createScript(ScriptType.DB, 5L, null, "x;", 42L)).thenReturn(expected);

        Script actual = mcpTools.createScript(ScriptType.DB, 5L, null, "x;");

        assertEquals(expected, actual);
        verify(scriptManagementService).createScript(ScriptType.DB, 5L, null, "x;", 42L);
    }

    @Test
    void createScript_withoutPat_throwsUnauthorizedAndNeverCallsService() {
        ReflectionTestUtils.setField(mcpTools, "mcpPat", "");

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                mcpTools.createScript(ScriptType.DB, 5L, null, "x;")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(scriptManagementService, never()).createScript(any(), any(), any(), any(), any());
    }

    @Test
    void updateScript_resolvesUserFromPatAndDelegatesToScriptManagementService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(42L);
        Script expected = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("y;").build();
        when(scriptManagementService.updateScript(1L, "y;", 42L)).thenReturn(expected);

        Script actual = mcpTools.updateScript(1L, "y;");

        assertEquals(expected, actual);
        verify(scriptManagementService).updateScript(1L, "y;", 42L);
    }

    @Test
    void deleteScript_resolvesUserFromPatAndDelegatesToScriptManagementService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(42L);

        mcpTools.deleteScript(1L);

        verify(scriptManagementService).deleteScript(1L, 42L);
    }

    @Test
    void getScript_delegatesToScriptManagementServiceWithoutRequiringPat() {
        ReflectionTestUtils.setField(mcpTools, "mcpPat", "");
        Script expected = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("z;").build();
        when(scriptManagementService.getScript(1L)).thenReturn(expected);

        Script actual = mcpTools.getScript(1L);

        assertEquals(expected, actual);
    }

    @Test
    void listScripts_delegatesToScriptManagementServiceWithoutRequiringPat() {
        ReflectionTestUtils.setField(mcpTools, "mcpPat", "");
        Script script = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("z;").build();
        when(scriptManagementService.listScripts()).thenReturn(List.of(script));

        List<Script> actual = mcpTools.listScripts();

        assertEquals(List.of(script), actual);
    }
}
