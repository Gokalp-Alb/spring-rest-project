package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.ALLOWED_OPERATORS;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.request.table.AuditRequest;
import com.springrest.springrestproject.dto.response.data.DataResponse;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.implementations.Kafka.OutboundKafkaPublisher;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.util.DataEvaluationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataService implements IDataService {

    private final JdbcTemplate jdbcTemplate;
    private final TableMetadataRepo tableMetadataRepo;
    private final AppUserRepo userRepo;
    private final IMetadataService metadataService;
    private final DataEvaluationHelper dataHelper;
    private final OutboundKafkaPublisher kafkaPublisher;

    @Override
    @Transactional
    public DataResponse insertRow(TableInsertRequest request, Long userId) {
        if (request.tableName().toLowerCase().endsWith("_log")) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        TableMetadata metadata = tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        dataHelper.validateRowRegex(metadata, request.rowData());
        dataHelper.validateRowDates(metadata, request.rowData());

        if (metadata.getTableContext() != null) {
            metadata.getTableContext().setLastUpdaterId(userId);
        }

        List<String> columns = new ArrayList<>(request.rowData().keySet());
        List<Object> values = new ArrayList<>(request.rowData().values());

        String columnsSql = String.join(", ", columns);

        String placeholdersSql = columns.stream()
                .map(col -> "?")
                .collect(Collectors.joining(", "));

        String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s) RETURNING id;",
                request.tableName(), columnsSql, placeholdersSql);

        String logSql = dataHelper.rebuildFullSql(insertSql, values);

        metadataService.logSchemaChange(request.tableName(), logSql, userId);
        Long id;
        try {
            id = jdbcTemplate.queryForObject(insertSql, Long.class, values.toArray());
        } catch (DataIntegrityViolationException e) {
            handleDataIntegrityViolation(e);
            throw e;
        }
        kafkaPublisher.publishMutation(request.tableName(), "INSERT", request.rowData(), userId);

        auditLogMutation(AuditRequest.builder()
                .tableMetadata(metadata)
                .recordId(id)
                .rowData(request.rowData())
                .operationType("POST")
                .executedAt(java.time.LocalDateTime.now())
                .userId(userId)
                .build());

        return new DataResponse(
                id,
                request.tableName(),
                "INSERT",
                request.rowData()
        );
    }

    @Override
    public List<Map<String, Object>> executeSelect(QueryRequest request, Long userId, Pageable pageable) {
        // Validate user context
        if (userId != null && userId != 0L) {
            userRepo.findById(userId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.BAD_REQUEST));
        }
        dataHelper.validateQueryDates(request);

        // Identify if log-based queries are requested
        boolean useLogTable = dataHelper.isLogQuery(request);
        List<Long> matchedIds = null;

        // Fetch matching record IDs from log table
        if (useLogTable) {
            String logTableName = request.tableName() + "_log";
            if (!logTableExists(logTableName)) {
                throw new ApplicationException(ErrorCode.LOG_TABLE_NOT_FOUND, request.tableName());
            }
            TableMetadata metadata = tableMetadataRepo.findByTableName(request.tableName())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
            LocalDateTime tableCreatedDate = null;
            if (metadata.getTableContext() != null && metadata.getTableContext().getCreatedDate() != null) {
                tableCreatedDate = metadata.getTableContext().getCreatedDate();
            }

            StringBuilder idQueryBuilder = new StringBuilder(String.format(
                "SELECT DISTINCT id FROM %s WHERE executed_at >= ?", logTableName));
            List<Object> idQueryParams = new ArrayList<>();
            idQueryParams.add(tableCreatedDate);

            if (request.conditions() != null && !request.conditions().isEmpty()) {
                for (QueryRequest.Condition condition : request.conditions()) {
                    idQueryBuilder.append(" AND ");
                    applyCondition(condition, idQueryBuilder, idQueryParams);
                }
            }
            idQueryBuilder.append(" AND operation_type IN ('POST', 'PUT')");

            matchedIds = jdbcTemplate.queryForList(idQueryBuilder.toString(), Long.class, idQueryParams.toArray());
            if (matchedIds.isEmpty()) {
                return new ArrayList<>();
            }
        }

        // Build SQL selection projection
        String fieldsStr = (request.fields() == null || request.fields().isEmpty())
                ? "*"
                : String.join(", ", request.fields());

        StringBuilder sqlBuilder = new StringBuilder(String.format("SELECT %s FROM %s", fieldsStr, request.tableName()));
        List<Object> queryParams = new ArrayList<>();

        // Filter by log matched IDs or direct conditions
        if (useLogTable) {
            String placeholders = matchedIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            sqlBuilder.append(String.format(" WHERE id IN (%s)", placeholders));
            queryParams.addAll(matchedIds);
        } else {
            if (request.conditions() != null && !request.conditions().isEmpty()) {
                sqlBuilder.append(" WHERE ");
                for (int i = 0; i < request.conditions().size(); i++) {
                    QueryRequest.Condition condition = request.conditions().get(i);
                    if (i > 0) {
                        sqlBuilder.append(" AND ");
                    }
                    applyCondition(condition, sqlBuilder, queryParams);
                }
            }
        }

        // Apply sorting clauses
        if (request.sorts() != null && !request.sorts().isEmpty()) {
            sqlBuilder.append(" ORDER BY ");
            for (int i = 0; i < request.sorts().size(); i++) {
                QueryRequest.Sort sort = request.sorts().get(i);
                if (i > 0) {
                    sqlBuilder.append(", ");
                }
                sqlBuilder.append(String.format("%s %s", sort.column(), sort.direction().getValue()));
            }
        }

        // Apply pagination constraints
        sqlBuilder.append(" LIMIT ? OFFSET ?");
        queryParams.add(pageable.getPageSize());
        queryParams.add(pageable.getOffset());

        // Execute base table select query
        return jdbcTemplate.queryForList(sqlBuilder.toString(), queryParams.toArray());
    }

    private void applyCondition(QueryRequest.Condition condition, StringBuilder sqlBuilder, List<Object> queryParams) {
        ALLOWED_OPERATORS op = condition.operator();
        switch (op) {
            case BETWEEN -> handleBetween(condition, sqlBuilder, queryParams);
            case BEFORE -> {
                sqlBuilder.append(String.format("%s < ?", condition.column()));
                queryParams.add(dataHelper.parseIfDateTime(condition.value()));
            }
            case AFTER -> {
                sqlBuilder.append(String.format("%s > ?", condition.column()));
                queryParams.add(dataHelper.parseIfDateTime(condition.value()));
            }
            default -> {
                sqlBuilder.append(String.format("%s %s ?", condition.column(), op.getValue()));
                queryParams.add(condition.value());
            }
        }
    }

    private void handleBetween(QueryRequest.Condition condition, StringBuilder sqlBuilder, List<Object> queryParams) {
        sqlBuilder.append(String.format("%s BETWEEN ? AND ?", condition.column()));
        Object value = condition.value();

        if (value instanceof List<?> list && list.size() >= 2) {
            queryParams.add(dataHelper.parseIfDateTime(list.get(0)));
            queryParams.add(dataHelper.parseIfDateTime(list.get(1)));
        } else if (value instanceof Object[] arr && arr.length >= 2) {
            queryParams.add(dataHelper.parseIfDateTime(arr[0]));
            queryParams.add(dataHelper.parseIfDateTime(arr[1]));
        } else {
            String valStr = String.valueOf(value);
            if (valStr.contains(",")) {
                String[] parts = valStr.split(",");
                queryParams.add(dataHelper.parseIfDateTime(parts[0].trim()));
                queryParams.add(dataHelper.parseIfDateTime(parts[1].trim()));
            } else {
                queryParams.add(dataHelper.parseIfDateTime(value));
                queryParams.add(dataHelper.parseIfDateTime(value));
            }
        }
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getTableData(String tableName, Boolean showSensitive, Pageable pageable, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        String projection = dataHelper.getProjectionClause(metadata, showSensitive);
        String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
        Long totalElements = jdbcTemplate.queryForObject(countSql, Long.class);
        if (totalElements == null) totalElements = 0L;

        String dataSql = String.format("SELECT %s FROM %s LIMIT ? OFFSET ?", projection, tableName);
        List<Map<String, Object>> content = jdbcTemplate.queryForList(dataSql, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, totalElements);
    }



    @Override
    @Transactional
    public DataResponse deleteRowById(String tableName, Long id, Long userId) {
        if (tableName.toLowerCase().endsWith("_log")) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        String deleteSql = String.format("DELETE FROM %s WHERE id = ?;", tableName);

        List<Object> logValues = new ArrayList<>();
        logValues.add(id);
        String fullSqlForLog = dataHelper.rebuildFullSql(deleteSql, logValues);
        metadataService.logSchemaChange(tableName, fullSqlForLog, userId);

        Map<String, Object> beforeState = null;
        if (metadata.getIsAuditEnabled() != null && metadata.getIsAuditEnabled()) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(String.format("SELECT * FROM %s WHERE id = ?;", tableName), id);
            if (!rows.isEmpty()) {
                beforeState = rows.getFirst();
            }
        }

        int rowsAffected = jdbcTemplate.update(deleteSql, id);
        kafkaPublisher.publishMutation(tableName, "DELETE", Map.of("id", id), userId);
        if (rowsAffected == 0) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (metadata.getTableContext() != null) {
            metadata.getTableContext().setLastUpdaterId(userId);
        }

        if (beforeState != null) {
            auditLogMutation(AuditRequest.builder()
                    .tableMetadata(metadata)
                    .recordId(id)
                    .rowData(beforeState)
                    .operationType("DELETE")
                    .executedAt(LocalDateTime.now())
                    .userId(userId)
                    .build());
        }

        return new DataResponse(
                id,
                tableName,
                "DELETE",
                null
        );
    }

    @Override
    @Transactional
    public DataResponse updateRowById(String tableName, Long id, Map<String, Object> updateData, Long userId) {
        if (tableName.toLowerCase().endsWith("_log")) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (updateData == null || updateData.isEmpty()) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST);
        }
        dataHelper.validateRowRegex(metadata, updateData);
        dataHelper.validateRowDates(metadata, updateData);
        List<String> sets = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : updateData.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("id")) {
                sets.add(entry.getKey() + " = ?");
                values.add(entry.getValue());
            }
        }
        String setSql = String.join(", ", sets);
        String updateSql = String.format("UPDATE %s SET %s WHERE id = ?;", tableName, setSql);
        values.add(id);

        String fullSqlForLog = dataHelper.rebuildFullSql(updateSql, values);
        metadataService.logSchemaChange(tableName, fullSqlForLog, userId);

        int rowsAffected;
        try {
            rowsAffected = jdbcTemplate.update(updateSql, values.toArray());
        } catch (DataIntegrityViolationException e) {
            handleDataIntegrityViolation(e);
            throw e;
        }
        Map<String, Object> fullPayload = new HashMap<>(updateData);
        fullPayload.put("id", id);
        kafkaPublisher.publishMutation(tableName, "UPDATE", fullPayload, userId);

        if (rowsAffected == 0) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (metadata.getTableContext() != null) {
            metadata.getTableContext().setLastUpdaterId(userId);
        }

        if (metadata.getIsAuditEnabled() != null && metadata.getIsAuditEnabled()) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(String.format("SELECT * FROM %s WHERE id = ?;", tableName), id);
            if (!rows.isEmpty()) {
                auditLogMutation(AuditRequest.builder()
                        .tableMetadata(metadata)
                        .recordId(id)
                        .rowData(rows.getFirst())
                        .operationType("PUT")
                        .executedAt(LocalDateTime.now())
                        .userId(userId)
                        .build());
            }
        }

        return new DataResponse(
                id,
                tableName,
                "UPDATE",
                updateData
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> findRowById(String tableName, Long id, Boolean showSensitive, Long userId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        String projection = dataHelper.getProjectionClause(metadata, showSensitive);
        String dataSql = String.format("SELECT %s FROM %s WHERE id = ?;", projection, tableName);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, id);
        if (records.isEmpty()) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return records.getFirst();
    }

    private void auditLogMutation(AuditRequest auditReq) {
        TableMetadata metadata = auditReq.tableMetadata();
        if (metadata.getIsAuditEnabled() != null && metadata.getIsAuditEnabled()) {
            String logTableName = metadata.getTableName() + "_log";
            List<String> columns = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            columns.add("id");
            values.add(auditReq.recordId());

            columns.add("operation_type");
            values.add(auditReq.operationType());

            columns.add("executed_at");
            values.add(auditReq.executedAt());

            columns.add("user_id");
            values.add(auditReq.userId() != null ? auditReq.userId() : 0L);

            if (auditReq.rowData() != null && metadata.getColumns() != null) {
                for (var col : metadata.getColumns()) {
                    String colName = col.getColumnName();
                    Object val = auditReq.rowData().entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase(colName))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(null);
                    columns.add(colName);
                    values.add(val);
                }
            }

            String columnsSql = String.join(", ", columns);
            String placeholdersSql = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
            String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s);", logTableName, columnsSql, placeholdersSql);

            jdbcTemplate.update(insertSql, values.toArray());
        }
    }

    private boolean logTableExists(String tableName) {
        String sql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE LOWER(table_name) = ?)";
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, tableName.toLowerCase()));
        } catch (Exception e) {
            return false;
        }
    }

    private void handleDataIntegrityViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        String msg = cause.getMessage();
        if (msg != null && (msg.contains("violates foreign key constraint") || msg.contains("is not present in table"))) {
            Pattern pattern = Pattern.compile("Key \\(([^)]+)\\)\\s*=\\s*\\(([^)]+)\\)");
            Matcher matcher = pattern.matcher(msg);
            if (matcher.find()) {
                String columnName = matcher.group(1).trim();
                String valueStr = matcher.group(2).trim();
                String reason = String.format("The referenced value '%s' does not exist.", valueStr);
                throw new ApplicationException(
                        ErrorCode.BAD_REQUEST,
                        List.of(new FieldValidationError(columnName, reason)),
                        reason
                );
            }
        }
    }

}