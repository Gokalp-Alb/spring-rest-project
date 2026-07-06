package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.ALLOWED_OPERATORS;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.dto.request.table.AuditRequest;
import com.springrest.springrestproject.dto.response.data.AuditLogResponse;
import com.springrest.springrestproject.dto.response.data.DataResponse;
import com.springrest.springrestproject.dto.response.data.QueryResponse;
import com.springrest.springrestproject.dto.response.relation.ResolvedRelation;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.column.SystemColumn;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.implementations.Kafka.OutboundKafkaPublisher;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.model.relation.RelationJoinType;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataService implements IDataService {

    private final JdbcTemplate jdbcTemplate;
    private final TableMetadataRepo tableMetadataRepo;
    private final AppUserRepo userRepo;
    private final IMetadataService metadataService;
    private final IRelationService relationService;
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



        Map<String, Object> finalRowData = new HashMap<>(request.rowData());
        SystemColumn sysCols = SystemColumn.defaults();
        finalRowData.put(sysCols.creatorId().name(), userId);
        finalRowData.put(sysCols.createdDate().name(), LocalDateTime.now());
        finalRowData.put(sysCols.lastUpdaterId().name(), userId);
        finalRowData.put(sysCols.lastChangedDate().name(), LocalDateTime.now());

        List<String> columns = new ArrayList<>(finalRowData.keySet());
        List<Object> values = new ArrayList<>(finalRowData.values());

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
        kafkaPublisher.publishMutation(request.tableName(), "INSERT", finalRowData, userId);

        auditLogMutation(AuditRequest.builder()
                .tableMetadata(metadata)
                .recordId(id)
                .rowData(finalRowData)
                .operationType("POST")
                .executedAt(java.time.LocalDateTime.now())
                .userId(userId)
                .build());

        return new DataResponse(
                id,
                request.tableName(),
                "INSERT",
                finalRowData
        );
    }

    @Override
    public QueryResponse executeSelect(QueryRequest request, Long userId, Pageable pageable) {
        if (userId != null && userId != 0L) {
            userRepo.findById(userId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.BAD_REQUEST));
        }
        dataHelper.validateQueryDates(request);

        List<Map<String, Object>> data = executeMainSelect(request, pageable);

        if (request.relations() != null && !request.relations().isEmpty() && !data.isEmpty()) {
            data = fetchAndAttachRelations(request.tableName(), request.relations(), data);
        }

        List<AuditLogResponse> auditData = new ArrayList<>();
        if (request.audit() != null && !request.audit().isEmpty()) {
            auditData = executeAuditSelect(request, pageable);
        }

        return new QueryResponse(data, auditData);
    }

    private List<Map<String, Object>> executeMainSelect(QueryRequest request, Pageable pageable) {
        String fieldsStr;
        if (request.fields() == null || request.fields().isEmpty()) {
            fieldsStr = "T.*";
        } else {
            fieldsStr = request.fields().stream()
                    .map(field -> "T." + field)
                    .collect(Collectors.joining(", "));
        }

        String distinctStr = (request.relations() != null && !request.relations().isEmpty()) ? "DISTINCT " : "";
        StringBuilder sqlBuilder = new StringBuilder(String.format("SELECT %s%s FROM %s T", distinctStr, fieldsStr, request.tableName()));
        List<Object> queryParams = new ArrayList<>();

        if (request.relations() != null && !request.relations().isEmpty()) {
            List<ResolvedRelation> resolvedRelations = relationService.getRelationsForTable(request.tableName());
            int relationIndex = 0;
            for (QueryRequest.RelationQuery relQuery : request.relations()) {
                String relName = relQuery.relation();
                ResolvedRelation match = resolvedRelations.stream()
                        .filter(r -> r.relationName().equalsIgnoreCase(relName))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    String aliasTarget = "B" + relationIndex;
                    String aliasJunction = "J" + relationIndex;
                    if (RelationJoinType.M2M == match.type()) {
                        sqlBuilder.append(String.format(" INNER JOIN %s %s ON T.id = %s.%s",
                                match.junctionTable(), aliasJunction, aliasJunction, match.junctionBaseCol()));
                        sqlBuilder.append(String.format(" INNER JOIN %s %s ON %s.%s = %s.id",
                                match.targetTable(), aliasTarget, aliasJunction, match.junctionTargetCol(), aliasTarget));
                    } else if (RelationJoinType.FORWARD == match.type()) {
                        String relatedCol = match.targetColumn() != null ? match.targetColumn() : "id";
                        sqlBuilder.append(String.format(" INNER JOIN %s %s ON T.%s = %s.%s",
                                match.targetTable(), aliasTarget, match.baseColumn(), aliasTarget, relatedCol));
                    } else if (RelationJoinType.REVERSE == match.type()) {
                        sqlBuilder.append(String.format(" INNER JOIN %s %s ON T.id = %s.%s",
                                match.targetTable(), aliasTarget, aliasTarget, match.targetColumn()));
                    }
                    relationIndex++;
                }
            }
        }

        if (request.conditions() != null && !request.conditions().isEmpty()) {
            sqlBuilder.append(" WHERE ");
            for (int i = 0; i < request.conditions().size(); i++) {
                QueryRequest.Condition condition = request.conditions().get(i);
                if (i > 0) {
                    sqlBuilder.append(" AND ");
                }
                applyCondition(condition, sqlBuilder, queryParams, false, "T");
            }
        }

        applySorts(request.sorts(), sqlBuilder, null, "T");

        sqlBuilder.append(" LIMIT ? OFFSET ?");
        queryParams.add(pageable.getPageSize());
        queryParams.add(pageable.getOffset());

        return jdbcTemplate.queryForList(sqlBuilder.toString(), queryParams.toArray());
    }

    private List<AuditLogResponse> executeAuditSelect(QueryRequest request, Pageable pageable) {
        String logTableName = request.tableName() + "_log";
        if (!logTableExists(logTableName)) {
            throw new ApplicationException(ErrorCode.LOG_TABLE_NOT_FOUND, request.tableName());
        }

        String fieldsStr = (request.fields() == null || request.fields().isEmpty())
                ? "*"
                : String.join(", ", request.fields()) + ", operation_type, executed_at, user_id";

        if (request.fields() != null && !request.fields().isEmpty()) {
            List<String> auditFields = new ArrayList<>(request.fields());
            if (!auditFields.contains("operation_type")) auditFields.add("operation_type");
            if (!auditFields.contains("executed_at")) auditFields.add("executed_at");
            if (!auditFields.contains("user_id")) auditFields.add("user_id");
            fieldsStr = String.join(", ", auditFields);
        }

        StringBuilder sqlBuilder = new StringBuilder(String.format("SELECT %s FROM %s", fieldsStr, logTableName));
        List<Object> queryParams = new ArrayList<>();

        if (request.audit() != null && !request.audit().isEmpty()) {
            sqlBuilder.append(" WHERE ");
            for (int i = 0; i < request.audit().size(); i++) {
                QueryRequest.Condition condition = request.audit().get(i);
                if (i > 0) {
                    sqlBuilder.append(" AND ");
                }
                if ("operation_type".equalsIgnoreCase(condition.column())) {
                    List<String> ops = dataHelper.parseOperationTypes(String.valueOf(condition.value()));
                    if (condition.operator() == ALLOWED_OPERATORS.EQUALS) {
                        if (ops.size() == 1) {
                            sqlBuilder.append("operation_type = ?");
                            queryParams.add(ops.getFirst());
                        } else {
                            String placeholders = ops.stream().map(o -> "?").collect(Collectors.joining(", "));
                            sqlBuilder.append(String.format("operation_type IN (%s)", placeholders));
                            queryParams.addAll(ops);
                        }
                    } else if (condition.operator() == ALLOWED_OPERATORS.NOT_EQUALS) {
                        if (ops.size() == 1) {
                            sqlBuilder.append("operation_type != ?");
                            queryParams.add(ops.getFirst());
                        } else {
                            String placeholders = ops.stream().map(o -> "?").collect(Collectors.joining(", "));
                            sqlBuilder.append(String.format("operation_type NOT IN (%s)", placeholders));
                            queryParams.addAll(ops);
                        }
                    } else {
                        applyCondition(condition, sqlBuilder, queryParams, true, null);
                    }
                } else {
                    applyCondition(condition, sqlBuilder, queryParams, true, null);
                }
            }
        }

        applySorts(request.sorts(), sqlBuilder, "executed_at DESC", null);

        sqlBuilder.append(" LIMIT ? OFFSET ?");
        queryParams.add(pageable.getPageSize());
        queryParams.add(pageable.getOffset());

        List<Map<String, Object>> logResults = jdbcTemplate.queryForList(sqlBuilder.toString(), queryParams.toArray());
        
        List<AuditLogResponse> responseList = new ArrayList<>();
        for (Map<String, Object> row : logResults) {
            String opType = (String) row.remove("operation_type");
            
            Object executedAtObj = row.remove("executed_at");
            java.time.LocalDateTime executedAt = null;
            if (executedAtObj instanceof java.sql.Timestamp ts) {
                executedAt = ts.toLocalDateTime();
            } else if (executedAtObj instanceof java.time.LocalDateTime dt) {
                executedAt = dt;
            }
            
            Object userIdObj = row.remove("user_id");
            Long uId = null;
            if (userIdObj instanceof Number num) {
                uId = num.longValue();
            }

            responseList.add(new AuditLogResponse(opType, executedAt, uId, row));
        }

        return responseList;
    }

    private void applySorts(List<QueryRequest.Sort> sorts, StringBuilder sqlBuilder, String defaultSort, String tablePrefix) {
        if (sorts != null && !sorts.isEmpty()) {
            sqlBuilder.append(" ORDER BY ");
            for (int i = 0; i < sorts.size(); i++) {
                QueryRequest.Sort sort = sorts.get(i);
                if (i > 0) {
                    sqlBuilder.append(", ");
                }
                String column = (tablePrefix != null && !tablePrefix.isEmpty()) ? tablePrefix + "." + sort.column() : sort.column();
                sqlBuilder.append(String.format("%s %s", column, sort.direction().getValue()));
            }
        } else if (defaultSort != null) {
            sqlBuilder.append(" ORDER BY ").append(defaultSort);
        }
    }

    private void applyCondition(QueryRequest.Condition condition, StringBuilder sqlBuilder, List<Object> queryParams, boolean isAuditQuery, String tablePrefix) {
        ALLOWED_OPERATORS op = condition.operator();
        final boolean validNonAuditQuery = !isAuditQuery && !"created_date".equalsIgnoreCase(condition.column()) && !"last_changed_date".equalsIgnoreCase(condition.column());
        String colPrefix = (tablePrefix != null && !tablePrefix.isEmpty()) ? tablePrefix + "." : "";
        switch (op) {
            case BETWEEN -> handleBetween(condition, sqlBuilder, queryParams, isAuditQuery, tablePrefix);
            case BEFORE -> {
                if (validNonAuditQuery) {
                    sqlBuilder.append(String.format("(%screated_date < ? OR %slast_changed_date < ?)", colPrefix, colPrefix));
                    Object parsedVal = dataHelper.parseIfDateTime(condition.value());
                    queryParams.add(parsedVal);
                    queryParams.add(parsedVal);
                } else {
                    sqlBuilder.append(String.format("%s%s < ?", colPrefix, condition.column()));
                    queryParams.add(dataHelper.parseIfDateTime(condition.value()));
                }
            }
            case AFTER -> {
                if (validNonAuditQuery) {
                    sqlBuilder.append(String.format("(%screated_date > ? OR %slast_changed_date > ?)", colPrefix, colPrefix));
                    Object parsedVal = dataHelper.parseIfDateTime(condition.value());
                    queryParams.add(parsedVal);
                    queryParams.add(parsedVal);
                } else {
                    sqlBuilder.append(String.format("%s%s > ?", colPrefix, condition.column()));
                    queryParams.add(dataHelper.parseIfDateTime(condition.value()));
                }
            }
            default -> {
                sqlBuilder.append(String.format("%s%s %s ?", colPrefix, condition.column(), op.getValue()));
                queryParams.add(condition.value());
            }
        }
    }

    private void handleBetween(QueryRequest.Condition condition, StringBuilder sqlBuilder, List<Object> queryParams, boolean isAuditQuery, String tablePrefix) {
        Object value = condition.value();
        List<Object> parsedValues = new ArrayList<>();

        if (value instanceof List<?> list && list.size() >= 2) {
            parsedValues.add(dataHelper.parseIfDateTime(list.get(0)));
            parsedValues.add(dataHelper.parseIfDateTime(list.get(1)));
        } else if (value instanceof Object[] arr && arr.length >= 2) {
            parsedValues.add(dataHelper.parseIfDateTime(arr[0]));
            parsedValues.add(dataHelper.parseIfDateTime(arr[1]));
        } else {
            String valStr = String.valueOf(value);
            if (valStr.contains(",")) {
                String[] parts = valStr.split(",");
                parsedValues.add(dataHelper.parseIfDateTime(parts[0].trim()));
                parsedValues.add(dataHelper.parseIfDateTime(parts[1].trim()));
            } else {
                parsedValues.add(dataHelper.parseIfDateTime(value));
                parsedValues.add(dataHelper.parseIfDateTime(value));
            }
        }

        String colPrefix = (tablePrefix != null && !tablePrefix.isEmpty()) ? tablePrefix + "." : "";
        if (!isAuditQuery && !"created_date".equalsIgnoreCase(condition.column()) && !"last_changed_date".equalsIgnoreCase(condition.column())) {
            sqlBuilder.append(String.format("((%screated_date BETWEEN ? AND ?) OR (%slast_changed_date BETWEEN ? AND ?))", colPrefix, colPrefix));
            queryParams.add(parsedValues.get(0));
            queryParams.add(parsedValues.get(1));
        } else {
            sqlBuilder.append(String.format("%s%s BETWEEN ? AND ?", colPrefix, condition.column()));
        }
        queryParams.add(parsedValues.get(0));
        queryParams.add(parsedValues.get(1));
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
        if (metadata.isAuditEnabled() != null && metadata.isAuditEnabled()) {
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

        Map<String, Object> finalUpdateData = new HashMap<>(updateData);
        SystemColumn sysCols = SystemColumn.defaults();
        finalUpdateData.remove(sysCols.creatorId().name());
        finalUpdateData.remove(sysCols.createdDate().name());
        finalUpdateData.put(sysCols.lastUpdaterId().name(), userId);
        finalUpdateData.put(sysCols.lastChangedDate().name(), LocalDateTime.now());

        List<String> sets = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : finalUpdateData.entrySet()) {
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
        Map<String, Object> fullPayload = new HashMap<>(finalUpdateData);
        fullPayload.put("id", id);
        kafkaPublisher.publishMutation(tableName, "UPDATE", fullPayload, userId);

        if (rowsAffected == 0) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (metadata.isAuditEnabled() != null && metadata.isAuditEnabled()) {
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
                finalUpdateData
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
        if (metadata.isAuditEnabled() != null && metadata.isAuditEnabled()) {
            String logTableName = metadata.tableName() + "_log";
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

            if (auditReq.rowData() != null && metadata.columns() != null) {
                for (ColumnMetadata col : metadata.columns()) {
                    String colName = col.columnName();
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


    private List<Map<String, Object>> fetchAndAttachRelations(String tableName, List<QueryRequest.RelationQuery> requestedRelations, List<Map<String, Object>> data) {
        List<ResolvedRelation> resolvedRelations = relationService.getRelationsForTable(tableName);

        List<Object> baseIds = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Object idVal = row.get("id");
            if (idVal != null) {
                baseIds.add(idVal);
            }
        }
        if (baseIds.isEmpty()) {
            return data;
        }

        List<Map<String, Object>> mutableData = new ArrayList<>();
        for (Map<String, Object> row : data) {
            mutableData.add(new HashMap<>(row));
        }

        for (QueryRequest.RelationQuery relQuery : requestedRelations) {
            String relName = relQuery.relation();

            ResolvedRelation match = resolvedRelations.stream()
                    .filter(r -> r.relationName().equalsIgnoreCase(relName))
                    .findFirst()
                    .orElse(null);

            if (match == null) {
                for (Map<String, Object> row : mutableData) {
                    row.put(relName, List.of());
                }
                continue;
            }

            List<Map<String, Object>> relatedRows = new ArrayList<>();
            String targetTable = match.targetTable();

            if (RelationJoinType.M2M == match.type()) {
                String placeholders = baseIds.stream().map(id -> "?").collect(Collectors.joining(", "));
                String sql = String.format(
                        "SELECT J.%s AS base_id, B.* FROM %s B JOIN %s J ON B.id = J.%s WHERE J.%s IN (%s)",
                        match.junctionBaseCol(), targetTable, match.junctionTable(), match.junctionTargetCol(), match.junctionBaseCol(), placeholders
                );
                relatedRows = jdbcTemplate.queryForList(sql, baseIds.toArray());

            } else if (RelationJoinType.FORWARD == match.type()) {
                List<Object> fkValues = new ArrayList<>();
                for (Map<String, Object> row : mutableData) {
                    Object val = row.get(match.baseColumn());
                    if (val != null) {
                        fkValues.add(val);
                    }
                }
                if (!fkValues.isEmpty()) {
                    String relatedCol = match.targetColumn() != null ? match.targetColumn() : "id";
                    String placeholders = fkValues.stream().map(v -> "?").collect(Collectors.joining(", "));
                    String sql = String.format("SELECT * FROM %s WHERE %s IN (%s)", targetTable, relatedCol, placeholders);
                    List<Map<String, Object>> targetRows = jdbcTemplate.queryForList(sql, fkValues.toArray());

                    Map<Object, Map<String, Object>> targetMap = new HashMap<>();
                    for (Map<String, Object> tRow : targetRows) {
                        Object targetVal = tRow.get(relatedCol);
                        if (targetVal == null) {
                            for (Map.Entry<String, Object> entry : tRow.entrySet()) {
                                if (entry.getKey().equalsIgnoreCase(relatedCol)) {
                                    targetVal = entry.getValue();
                                    break;
                                }
                            }
                        }
                        if (targetVal != null) {
                            targetMap.put(String.valueOf(targetVal), tRow);
                            targetMap.put(targetVal, tRow);
                        }
                    }

                    for (Map<String, Object> row : mutableData) {
                        Object fkVal = row.get(match.baseColumn());
                        if (fkVal != null && (targetMap.containsKey(fkVal) || targetMap.containsKey(String.valueOf(fkVal)))) {
                            Object matchedRow = targetMap.get(fkVal);
                            if (matchedRow == null) {
                                matchedRow = targetMap.get(String.valueOf(fkVal));
                            }
                            row.put(relName, List.of(matchedRow));
                        } else {
                            row.put(relName, List.of());
                        }
                    }
                } else {
                    for (Map<String, Object> row : mutableData) {
                        row.put(relName, List.of());
                    }
                }
                continue;

            } else if (RelationJoinType.REVERSE == match.type()) {
                String placeholders = baseIds.stream().map(id -> "?").collect(Collectors.joining(", "));
                String sql = String.format(
                        "SELECT %s AS base_id, B.* FROM %s B WHERE %s IN (%s)",
                        match.targetColumn(), targetTable, match.targetColumn(), placeholders
                );
                relatedRows = jdbcTemplate.queryForList(sql, baseIds.toArray());
            }

            Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
            for (Map<String, Object> rRow : relatedRows) {
                Object baseIdVal = rRow.remove("base_id");
                if (baseIdVal != null) {
                    String baseIdStr = String.valueOf(baseIdVal);
                    grouped.computeIfAbsent(baseIdStr, k -> new ArrayList<>()).add(rRow);
                }
            }

            for (Map<String, Object> row : mutableData) {
                Object idVal = row.get("id");
                if (idVal != null) {
                    String idStr = String.valueOf(idVal);
                    row.put(relName, grouped.getOrDefault(idStr, List.of()));
                } else {
                    row.put(relName, List.of());
                }
            }
        }

        return mutableData;
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