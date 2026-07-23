package com.springrest.springrestproject.mcp.tools;

import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.springrestproject.model.KafkaTableMapping;
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

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxMcpToolsKafkaMappingTest {

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
    }

    @Test
    void createKafkaMapping_delegatesToKafkaMappingServiceWithoutRequiringPat() {
        KafkaTableMapping expected = KafkaTableMapping.builder().id(1L).tableName("orders").topicId(5L).direction("OUTBOUND").active(true).build();
        when(kafkaMappingService.createMapping("orders", "orders-events", "OUTBOUND")).thenReturn(expected);

        KafkaTableMapping actual = sandboxMcpTools.createKafkaMapping("orders", "orders-events", "OUTBOUND");

        assertEquals(expected, actual);
        verify(kafkaMappingService).createMapping("orders", "orders-events", "OUTBOUND");
    }

    @Test
    void updateKafkaMapping_delegatesToKafkaMappingService() {
        KafkaTableMapping expected = KafkaTableMapping.builder().id(1L).tableName("orders").topicId(5L).direction("OUTBOUND").active(false).build();
        when(kafkaMappingService.updateMapping(1L, "orders", "orders-events", "OUTBOUND", false)).thenReturn(expected);

        KafkaTableMapping actual = sandboxMcpTools.updateKafkaMapping(1L, "orders", "orders-events", "OUTBOUND", false);

        assertEquals(expected, actual);
        verify(kafkaMappingService).updateMapping(1L, "orders", "orders-events", "OUTBOUND", false);
    }

    @Test
    void removeKafkaMapping_delegatesToKafkaMappingService() {
        sandboxMcpTools.removeKafkaMapping(1L);

        verify(kafkaMappingService).removeMapping(1L);
    }

    @Test
    void getKafkaMapping_delegatesToKafkaMappingService() {
        KafkaTableMapping expected = KafkaTableMapping.builder().id(1L).tableName("orders").topicId(5L).direction("OUTBOUND").active(true).build();
        when(kafkaMappingService.getMapping(1L)).thenReturn(expected);

        KafkaTableMapping actual = sandboxMcpTools.getKafkaMapping(1L);

        assertEquals(expected, actual);
    }

    @Test
    void listKafkaMappings_delegatesToKafkaMappingService() {
        KafkaTableMapping mapping = KafkaTableMapping.builder().id(1L).tableName("orders").topicId(5L).direction("OUTBOUND").active(true).build();
        when(kafkaMappingService.listMappings()).thenReturn(List.of(mapping));

        List<KafkaTableMapping> actual = sandboxMcpTools.listKafkaMappings();

        assertEquals(List.of(mapping), actual);
    }
}
