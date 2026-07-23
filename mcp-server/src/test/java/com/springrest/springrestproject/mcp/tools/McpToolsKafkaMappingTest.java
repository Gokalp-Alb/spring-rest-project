package com.springrest.springrestproject.mcp.tools;

import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.model.KafkaTableMapping;
import com.springrest.springrestproject.model.user.GroupName;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolsKafkaMappingTest {

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
    void createKafkaMapping_withKafkaEngineerRole_delegatesToKafkaMappingService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(42L);
        when(userService.getUserById(42L)).thenReturn(new UserRequest(42L, "kafka-eng", List.of(GroupName.KAFKA_ENGINEER), null));
        KafkaTableMapping expected = KafkaTableMapping.builder().id(1L).tableName("orders").topicId(5L).direction("OUTBOUND").active(true).build();
        when(kafkaMappingService.createMapping("orders", "orders-events", "OUTBOUND")).thenReturn(expected);

        KafkaTableMapping actual = mcpTools.createKafkaMapping("orders", "orders-events", "OUTBOUND");

        assertEquals(expected, actual);
        verify(kafkaMappingService).createMapping("orders", "orders-events", "OUTBOUND");
    }

    @Test
    void createKafkaMapping_withoutKafkaEngineerRole_throwsUnauthorizedAndNeverCallsService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(99L);
        when(userService.getUserById(99L)).thenReturn(new UserRequest(99L, "plain-user", List.of(GroupName.REGISTERED_USER), null));

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                mcpTools.createKafkaMapping("orders", "orders-events", "OUTBOUND")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(kafkaMappingService, never()).createMapping(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createKafkaMapping_withoutPat_throwsUnauthorizedAndNeverCallsService() {
        ReflectionTestUtils.setField(mcpTools, "mcpPat", "");

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                mcpTools.createKafkaMapping("orders", "orders-events", "OUTBOUND")
        );

        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
        verify(kafkaMappingService, never()).createMapping(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateKafkaMapping_withKafkaEngineerRole_delegatesToKafkaMappingService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(42L);
        when(userService.getUserById(42L)).thenReturn(new UserRequest(42L, "kafka-eng", List.of(GroupName.KAFKA_ENGINEER), null));
        KafkaTableMapping expected = KafkaTableMapping.builder().id(1L).tableName("orders").topicId(5L).direction("OUTBOUND").active(false).build();
        when(kafkaMappingService.updateMapping(1L, "orders", "orders-events", "OUTBOUND", false)).thenReturn(expected);

        KafkaTableMapping actual = mcpTools.updateKafkaMapping(1L, "orders", "orders-events", "OUTBOUND", false);

        assertEquals(expected, actual);
        verify(kafkaMappingService).updateMapping(1L, "orders", "orders-events", "OUTBOUND", false);
    }

    @Test
    void removeKafkaMapping_withKafkaEngineerRole_delegatesToKafkaMappingService() {
        when(patService.validateTokenAndGetUserId("pat_test_token")).thenReturn(42L);
        when(userService.getUserById(42L)).thenReturn(new UserRequest(42L, "kafka-eng", List.of(GroupName.KAFKA_ENGINEER), null));

        mcpTools.removeKafkaMapping(1L);

        verify(kafkaMappingService).removeMapping(1L);
    }

    @Test
    void getKafkaMapping_delegatesToKafkaMappingServiceWithoutRequiringPat() {
        ReflectionTestUtils.setField(mcpTools, "mcpPat", "");
        KafkaTableMapping expected = KafkaTableMapping.builder().id(1L).tableName("orders").topicId(5L).direction("OUTBOUND").active(true).build();
        when(kafkaMappingService.getMapping(1L)).thenReturn(expected);

        KafkaTableMapping actual = mcpTools.getKafkaMapping(1L);

        assertEquals(expected, actual);
    }

    @Test
    void listKafkaMappings_delegatesToKafkaMappingServiceWithoutRequiringPat() {
        ReflectionTestUtils.setField(mcpTools, "mcpPat", "");
        KafkaTableMapping mapping = KafkaTableMapping.builder().id(1L).tableName("orders").topicId(5L).direction("OUTBOUND").active(true).build();
        when(kafkaMappingService.listMappings()).thenReturn(List.of(mapping));

        List<KafkaTableMapping> actual = mcpTools.listKafkaMappings();

        assertEquals(List.of(mapping), actual);
    }
}
