package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.query.ALLOWED_OPERATORS;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.dto.response.data.QueryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DataServiceLogQueryTest {
    @Autowired
    private IDataService dataService;

    @Autowired
    private IMetadataService metadataService;

    @Autowired
    private TableMetadataRepo tableMetadataRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String testTableName = "test_log_query";
    private final String testLogTableName = "test_log_query_log";

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + testLogTableName);
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + testTableName);
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS test_date_validation");
        } catch (Exception ignored) {}
        
        tableMetadataRepo.findByTableName(testTableName).ifPresent(metadata -> tableMetadataRepo.delete(metadata));
        tableMetadataRepo.findByTableName("test_date_validation").ifPresent(metadata -> tableMetadataRepo.delete(metadata));
    }


    @Test
    void shouldQueryLogTableSuccessfullyWhenLogExists() {
        // Create table with audit log
        List<ColumnMetadata> columns = new ArrayList<>();
        ColumnMetadata col = new ColumnMetadata();
        col.setColumnName("val");
        col.setDataType("VARCHAR(255)");
        columns.add(col);

        TableCreateRequest createRequest = new TableCreateRequest(columns, true);
        metadataService.createTable(testTableName, createRequest, 0L);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String timeApple = now.plusHours(1).format(formatter);
        String timeBanana = now.plusHours(2).format(formatter);
        String timeCherry = now.plusHours(3).format(formatter);

        String queryBetweenStart = now.plusHours(1).plusMinutes(30).format(formatter);
        String queryBetweenEnd = now.plusHours(2).plusMinutes(30).format(formatter);
        String queryBefore = now.plusHours(1).plusMinutes(30).format(formatter);
        String queryAfter = now.plusHours(2).plusMinutes(30).format(formatter);

        // Insert directly into base table so that they exist
        jdbcTemplate.execute("INSERT INTO " + testTableName + " (id, val, created_date, last_changed_date) VALUES (1, 'apple', '" + timeApple + "', '" + timeApple + "')");
        jdbcTemplate.execute("INSERT INTO " + testTableName + " (id, val, created_date, last_changed_date) VALUES (2, 'banana', '" + timeBanana + "', '" + timeBanana + "')");
        jdbcTemplate.execute("INSERT INTO " + testTableName + " (id, val, created_date, last_changed_date) VALUES (3, 'cherry', '" + timeCherry + "', '" + timeCherry + "')");

        // Insert directly into log table for testing queries
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (1, 'apple', 'POST', '" + timeApple + "', 0)");
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (2, 'banana', 'POST', '" + timeBanana + "', 0)");
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (3, 'cherry', 'POST', '" + timeCherry + "', 0)");

        Pageable pageable = PageRequest.of(0, 10);

        // Test BETWEEN
        QueryRequest betweenRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.BETWEEN, List.of(queryBetweenStart, queryBetweenEnd))),
                List.of(),
                List.of()
        );
        QueryResponse resultsBetween = dataService.executeSelect(betweenRequest, 0L, pageable);
        assertEquals(1, resultsBetween.data().size());
        assertEquals("banana", resultsBetween.data().getFirst().get("val"));

        // Test BEFORE
        QueryRequest beforeRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.BEFORE, queryBefore)),
                List.of(),
                List.of()
        );
        QueryResponse resultsBefore = dataService.executeSelect(beforeRequest, 0L, pageable);
        assertEquals(1, resultsBefore.data().size());
        assertEquals("apple", resultsBefore.data().getFirst().get("val"));

        // Test AFTER
        QueryRequest afterRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.AFTER, queryAfter)),
                List.of(),
                List.of()
        );
        QueryResponse resultsAfter = dataService.executeSelect(afterRequest, 0L, pageable);
        assertEquals(1, resultsAfter.data().size());
        assertEquals("cherry", resultsAfter.data().getFirst().get("val"));
    }

    @Test
    void shouldNotReturnDeletedRowsWhenQueryingLogs() {
        // Create table with audit log
        List<ColumnMetadata> columns = new ArrayList<>();
        ColumnMetadata col = new ColumnMetadata();
        col.setColumnName("val");
        col.setDataType("VARCHAR(255)");
        columns.add(col);

        TableCreateRequest createRequest = new TableCreateRequest(columns, true);
        metadataService.createTable(testTableName, createRequest, 0L);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String timeApple = now.plusHours(1).format(formatter);
        String timeBanana = now.plusHours(2).format(formatter);
        String queryAfter = now.plusMinutes(30).format(formatter);

        // Apple is created and then deleted (so it does NOT exist in the base table)
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (1, 'apple', 'POST', '" + timeApple + "', 0)");
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (1, 'apple', 'DELETE', '" + timeApple + "', 0)");

        // Banana is created and remains in the base table (so we simulate it exists)
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (2, 'banana', 'POST', '" + timeBanana + "', 0)");
        jdbcTemplate.execute("INSERT INTO " + testTableName + " (id, val, created_date, last_changed_date) VALUES (2, 'banana', '" + timeBanana + "', '" + timeBanana + "')");

        Pageable pageable = PageRequest.of(0, 10);

        // Query AFTER queryAfter
        QueryRequest afterRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.AFTER, queryAfter)),
                List.of(),
                List.of()
        );
        QueryResponse results = dataService.executeSelect(afterRequest, 0L, pageable);
        
        // Should only return banana log (since apple was deleted and doesn't exist in the base table)
        assertEquals(1, results.data().size());
        assertEquals("banana", results.data().getFirst().get("val"));
    }

    @Test
    void shouldOnlyReturnLogsFromCurrentLifecycleOnRecreatedTable() throws Exception {
        // Create table with audit log
        List<ColumnMetadata> columns = new ArrayList<>();
        ColumnMetadata col = new ColumnMetadata();
        col.setColumnName("val");
        col.setDataType("VARCHAR(255)");
        columns.add(col);

        TableCreateRequest createRequest = new TableCreateRequest(columns, true);
        metadataService.createTable(testTableName, createRequest, 0L);

        // Populate table (1st lifecycle)
        com.springrest.springrestproject.dto.request.data.TableInsertRequest insertReq1 = 
                new com.springrest.springrestproject.dto.request.data.TableInsertRequest(testTableName, Map.of("val", "old_apple"));
        dataService.insertRow(insertReq1, 0L);

        // Delete table (leaves log table intact)
        metadataService.deleteTableByName(testTableName, 0L);

        // Sleep to ensure time difference at second-level precision
        Thread.sleep(1200);

        // Recreate table with the same name (2nd lifecycle)
        metadataService.createTable(testTableName, createRequest, 0L);

        // Populate recreated table
        com.springrest.springrestproject.dto.request.data.TableInsertRequest insertReq2 = 
                new com.springrest.springrestproject.dto.request.data.TableInsertRequest(testTableName, Map.of("val", "new_banana"));
        dataService.insertRow(insertReq2, 0L);

        // Get 2nd table's creation time
        TableMetadata metadata =
                tableMetadataRepo.findByTableName(testTableName).orElseThrow();
        java.time.LocalDateTime tCreate2 = metadata.getTableContext().getCreatedDate();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String tCreate2Str = tCreate2.format(formatter);

        Pageable pageable = PageRequest.of(0, 10);

        // 1. Query BEFORE tCreate2 - should return nothing (old log records are filtered out)
        QueryRequest beforeRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.BEFORE, tCreate2Str)),
                List.of(),
                List.of()
        );
        QueryResponse resultsBefore = dataService.executeSelect(beforeRequest, 0L, pageable);
        assertTrue(resultsBefore.data().isEmpty());

        // 2. Query AFTER tCreate2 - should return whatever we populated the 2nd table with (new_banana)
        QueryRequest afterRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.AFTER, tCreate2Str)),
                List.of(),
                List.of()
        );
        QueryResponse resultsAfter = dataService.executeSelect(afterRequest, 0L, pageable);
        assertEquals(1, resultsAfter.data().size());
        assertEquals("new_banana", resultsAfter.data().getFirst().get("val"));
    }

    @Test
    void shouldThrowInvalidDateFormatWhenQueryingWithMalformedTimestamp() {
        Pageable pageable = PageRequest.of(0, 10);

        // Query with malformed timestamp value "invalid-date-string" for executed_at
        QueryRequest invalidRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.AFTER, "invalid-date-string")),
                List.of(),
                List.of()
        );

        ApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class, () -> 
                dataService.executeSelect(invalidRequest, 0L, pageable)
        );
        assertEquals(ErrorCode.INVALID_DATE_FORMAT, exception.getErrorCode());
    }

    @Test
    void shouldThrowInvalidDateFormatWhenInsertingRowWithMalformedDate() {
        // Create table with DATE column
        List<ColumnMetadata> columns = new ArrayList<>();
        ColumnMetadata col = new ColumnMetadata();
        col.setColumnName("birth_date");
        col.setDataType("DATE");
        columns.add(col);

        TableCreateRequest createRequest = new TableCreateRequest(columns, false);
        metadataService.createTable("test_date_validation", createRequest, 0L);

        try {
            com.springrest.springrestproject.dto.request.data.TableInsertRequest insertReq =
                    new com.springrest.springrestproject.dto.request.data.TableInsertRequest(
                            "test_date_validation",
                            Map.of("birth_date", "2026/06/29") // Invalid format, expected yyyy-MM-dd
                    );

            ApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class, () ->
                    dataService.insertRow(insertReq, 0L)
            );
            assertEquals(ErrorCode.INVALID_DATE_FORMAT, exception.getErrorCode());
        } finally {
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS test_date_validation");
            } catch (Exception ignored) {}
            tableMetadataRepo.findByTableName("test_date_validation").ifPresent(metadata -> tableMetadataRepo.delete(metadata));
        }
    }

    @Test
    void shouldQueryAuditLogWithVariousOperationTypes() {
        // Create table with audit log
        List<ColumnMetadata> columns = new ArrayList<>();
        ColumnMetadata col = new ColumnMetadata();
        col.setColumnName("val");
        col.setDataType("VARCHAR(255)");
        columns.add(col);

        TableCreateRequest createRequest = new TableCreateRequest(columns, true);
        metadataService.createTable(testTableName, createRequest, 0L);

        // Insert log entries with POST (CREATE), PUT, and DELETE operation types
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (1, 'apple', 'POST', NOW(), 0)");
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (2, 'banana', 'PUT', NOW(), 0)");
        jdbcTemplate.execute("INSERT INTO " + testLogTableName + " (id, val, operation_type, executed_at, user_id) VALUES (3, 'cherry', 'DELETE', NOW(), 0)");

        Pageable pageable = PageRequest.of(0, 10);

        // 1. Query for CREATE (POST)
        QueryRequest createQuery = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(),
                List.of(new QueryRequest.Condition("operation_type", ALLOWED_OPERATORS.EQUALS, "CREATE")),
                List.of()
        );
        QueryResponse resCreate = dataService.executeSelect(createQuery, 0L, pageable);
        assertEquals(1, resCreate.auditData().size());
        assertEquals("POST", resCreate.auditData().getFirst().operationType());
        assertEquals("apple", resCreate.auditData().getFirst().rowData().get("val"));

        // 2. Query for combination (PUT,CREATE)
        QueryRequest comboQuery = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(),
                List.of(new QueryRequest.Condition("operation_type", ALLOWED_OPERATORS.EQUALS, "PUT,CREATE")),
                List.of()
        );
        QueryResponse resCombo = dataService.executeSelect(comboQuery, 0L, pageable);
        assertEquals(2, resCombo.auditData().size());
        boolean hasPost = resCombo.auditData().stream().anyMatch(audit -> "POST".equals(audit.operationType()));
        boolean hasPut = resCombo.auditData().stream().anyMatch(audit -> "PUT".equals(audit.operationType()));
        assertTrue(hasPost && hasPut);

        // 3. Query for wildcard * (all operation types)
        QueryRequest wildcardQuery = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(),
                List.of(new QueryRequest.Condition("operation_type", ALLOWED_OPERATORS.EQUALS, "*")),
                List.of()
        );
        QueryResponse resWildcard = dataService.executeSelect(wildcardQuery, 0L, pageable);
        assertEquals(3, resWildcard.auditData().size());

        // 4. Query with invalid operation type
        QueryRequest invalidQuery = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(),
                List.of(new QueryRequest.Condition("operation_type", ALLOWED_OPERATORS.EQUALS, "INVALID")),
                List.of()
        );
        ApplicationException exception = assertThrows(ApplicationException.class, () ->
                dataService.executeSelect(invalidQuery, 0L, pageable)
        );
        assertEquals(ErrorCode.INVALID_OPERATOR, exception.getErrorCode());
    }
}
