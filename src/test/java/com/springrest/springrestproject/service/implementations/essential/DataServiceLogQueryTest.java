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
    void shouldThrowApplicationErrorWhenLogDoesNotExist() {
        // Create table without audit log
        List<ColumnMetadata> columns = new ArrayList<>();
        ColumnMetadata col = new ColumnMetadata();
        col.setColumnName("value");
        col.setDataType("VARCHAR(255)");
        columns.add(col);

        TableCreateRequest createRequest = new TableCreateRequest(columns, false);
        metadataService.createTable(testTableName, createRequest, 0L);

        // Query using BETWEEN - this should trigger log table check
        QueryRequest queryRequest = new QueryRequest(
                testTableName,
                List.of("value"),
                List.of(new QueryRequest.Condition("value", ALLOWED_OPERATORS.BETWEEN, List.of("A", "Z"))),
                List.of()
        );

        Pageable pageable = PageRequest.of(0, 10);
        ApplicationException exception = assertThrows(ApplicationException.class, () -> dataService.executeSelect(queryRequest, 0L, pageable));

        assertEquals(ErrorCode.LOG_TABLE_NOT_FOUND, exception.getErrorCode());
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
        jdbcTemplate.execute("INSERT INTO " + testTableName + " (id, val) VALUES (1, 'apple')");
        jdbcTemplate.execute("INSERT INTO " + testTableName + " (id, val) VALUES (2, 'banana')");
        jdbcTemplate.execute("INSERT INTO " + testTableName + " (id, val) VALUES (3, 'cherry')");

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
                List.of()
        );
        List<Map<String, Object>> resultsBetween = dataService.executeSelect(betweenRequest, 0L, pageable);
        assertEquals(1, resultsBetween.size());
        assertEquals("banana", resultsBetween.getFirst().get("val"));

        // Test BEFORE
        QueryRequest beforeRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.BEFORE, queryBefore)),
                List.of()
        );
        List<Map<String, Object>> resultsBefore = dataService.executeSelect(beforeRequest, 0L, pageable);
        assertEquals(1, resultsBefore.size());
        assertEquals("apple", resultsBefore.getFirst().get("val"));

        // Test AFTER
        QueryRequest afterRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.AFTER, queryAfter)),
                List.of()
        );
        List<Map<String, Object>> resultsAfter = dataService.executeSelect(afterRequest, 0L, pageable);
        assertEquals(1, resultsAfter.size());
        assertEquals("cherry", resultsAfter.getFirst().get("val"));
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
        jdbcTemplate.execute("INSERT INTO " + testTableName + " (id, val) VALUES (2, 'banana')");

        Pageable pageable = PageRequest.of(0, 10);

        // Query AFTER queryAfter
        QueryRequest afterRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.AFTER, queryAfter)),
                List.of()
        );
        List<Map<String, Object>> results = dataService.executeSelect(afterRequest, 0L, pageable);
        
        // Should only return banana log (since apple was deleted and doesn't exist in the base table)
        assertEquals(1, results.size());
        assertEquals("banana", results.getFirst().get("val"));
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
                List.of()
        );
        List<Map<String, Object>> resultsBefore = dataService.executeSelect(beforeRequest, 0L, pageable);
        assertTrue(resultsBefore.isEmpty());

        // 2. Query AFTER tCreate2 - should return whatever we populated the 2nd table with (new_banana)
        QueryRequest afterRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.AFTER, tCreate2Str)),
                List.of()
        );
        List<Map<String, Object>> resultsAfter = dataService.executeSelect(afterRequest, 0L, pageable);
        assertEquals(1, resultsAfter.size());
        assertEquals("new_banana", resultsAfter.getFirst().get("val"));
    }

    @Test
    void shouldThrowInvalidDateFormatWhenQueryingWithMalformedTimestamp() {
        Pageable pageable = PageRequest.of(0, 10);

        // Query with malformed timestamp value "invalid-date-string" for executed_at
        QueryRequest invalidRequest = new QueryRequest(
                testTableName,
                List.of("val"),
                List.of(new QueryRequest.Condition("executed_at", ALLOWED_OPERATORS.AFTER, "invalid-date-string")),
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
}
