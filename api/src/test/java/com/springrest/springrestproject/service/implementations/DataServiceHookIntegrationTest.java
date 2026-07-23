package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.SpringRestProjectApplication;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.repository.ScriptRepo;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Placed under api's test tree rather than domain's (as originally specified in the task brief):
// domain/pom.xml has no dependency on scripting, and structurally cannot (scripting already
// depends on domain in main scope, so the reverse would be a circular module dependency). Only
// api has both domain and scripting on its classpath, so only here can a real DataService and a
// real ScriptHookInvokerImpl coexist in the same Spring context.
//
// classes = SpringRestProjectApplication.class is required: domain's test-jar also puts
// DomainTestApplication (itself @SpringBootApplication-annotated) on the classpath, so an
// unqualified @SpringBootTest scan finds two @SpringBootConfiguration classes and fails to boot.
@SpringBootTest(classes = SpringRestProjectApplication.class)
class DataServiceHookIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IDataService dataService;
    @Autowired
    private IMetadataService metadataService;
    @Autowired
    private TableMetadataRepo tableMetadataRepo;
    @Autowired
    private ScriptRepo scriptRepo;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void beforeSaveToDbThrowingPreventsTheInsertAndRollsBackNothingBecauseNothingRanYet() {
        String tableName = "hook_it_orders_1";
        createSimpleTable(tableName);
        Long tableId = tableMetadataRepo.findByTableName(tableName).orElseThrow().id();
        scriptRepo.save(new Script(null, ScriptType.DB, tableId, null,
                "function beforeSaveToDB() { throw new Error('veto insert'); }"));

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                dataService.insertRow(new TableInsertRequest(tableName, Map.of("name", "test")), 1L));
        assertNotNull(ex);

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        assertEquals(0, rowCount);
    }

    @Test
    void afterSaveToDbThrowingRollsBackTheInsert() {
        String tableName = "hook_it_orders_2";
        createSimpleTable(tableName);
        Long tableId = tableMetadataRepo.findByTableName(tableName).orElseThrow().id();
        scriptRepo.save(new Script(null, ScriptType.DB, tableId, null,
                "function afterSaveToDB() { throw new Error('veto commit'); }"));

        assertThrows(ApplicationException.class, () ->
                dataService.insertRow(new TableInsertRequest(tableName, Map.of("name", "test")), 1L));

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        assertEquals(0, rowCount);
    }

    @Test
    void bothHooksSucceedingAllowsTheInsertAndProducesOneExecutionLogEntry() {
        String tableName = "hook_it_orders_3";
        createSimpleTable(tableName);
        Long tableId = tableMetadataRepo.findByTableName(tableName).orElseThrow().id();
        scriptRepo.save(new Script(null, ScriptType.DB, tableId, null,
                "function beforeSaveToDB() { console.log('before'); } function afterSaveToDB() { console.log('after'); }"));

        dataService.insertRow(new TableInsertRequest(tableName, Map.of("name", "test")), 1L);

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        assertEquals(1, rowCount);
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_execution_logs WHERE script_id = " +
                        "(SELECT id FROM sys_scripts WHERE table_id = " + tableId + ") AND status = 'SUCCESS'",
                Integer.class);
        assertEquals(1, logCount);
    }

    @Test
    void tableWithNoScriptInsertsNormally() {
        String tableName = "hook_it_orders_4";
        createSimpleTable(tableName);

        dataService.insertRow(new TableInsertRequest(tableName, Map.of("name", "test")), 1L);

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        assertEquals(1, rowCount);
    }

    private void createSimpleTable(String tableName) {
        ColumnMetadata column = ColumnMetadata.builder()
                .columnName("name")
                .dataType("VARCHAR(255)")
                .tableName(tableName)
                .build();
        metadataService.createTable(tableName, new TableCreateRequest(List.of(column), false), 1L);
    }
}
