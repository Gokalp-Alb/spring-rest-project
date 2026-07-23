package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.core.governance.SystemGovernanceGuard;
import com.springrest.springrestproject.core.hooks.ScriptHookInvoker;
import com.springrest.springrestproject.core.hooks.ScriptHookSession;
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
import com.springrest.springrestproject.model.relation.RelationJoinType;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.implementations.Kafka.OutboundKafkaPublisher;
import com.springrest.springrestproject.service.interfaces.IDataService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.util.DataEvaluationHelper;
import com.springrest.springrestproject.validators.SqlIdentifierValidator;
import org.springframework.context.annotation.Lazy;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DataService implements IDataService {

    private final JdbcTemplate jdbcTemplate;
    private final TableMetadataRepo tableMetadataRepo;
    private final AppUserRepo userRepo;
    private final IMetadataService metadataService;
    private final IRelationService relationService;
    private final DataEvaluationHelper dataHelper;
    private final OutboundKafkaPublisher kafkaPublisher;
    private final SqlIdentifierValidator sqlIdentifierValidator;
    private final SystemGovernanceGuard governanceGuard;
    private final ScriptHookInvoker hookInvoker;

    // Explicit constructor (rather than Lombok's @RequiredArgsConstructor) so that @Lazy can be
    // placed directly on the hookInvoker parameter. @Lazy breaks a genuine circular bean
    // dependency: DataService -> ScriptHookInvoker (ScriptHookInvokerImpl, from the scripting
    // module) -> IDataService (used by its TablesProxy to let scripts read/write table data) ->
    // DataService again. Spring cannot satisfy that cycle via constructor injection without one
    // side being a lazy proxy, and relying on Lombok to copy the annotation onto the generated
    // constructor parameter is not guaranteed.
    public DataService(JdbcTemplate jdbcTemplate,
                        TableMetadataRepo tableMetadataRepo,
                        AppUserRepo userRepo,
                        IMetadataService metadataService,
                        IRelationService relationService,
                        DataEvaluationHelper dataHelper,
                        OutboundKafkaPublisher kafkaPublisher,
                        SqlIdentifierValidator sqlIdentifierValidator,
                        SystemGovernanceGuard governanceGuard,
                        @Lazy ScriptHookInvoker hookInvoker) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableMetadataRepo = tableMetadataRepo;
        this.userRepo = userRepo;
        this.metadataService = metadataService;
        this.relationService = relationService;
        this.dataHelper = dataHelper;
        this.kafkaPublisher = kafkaPublisher;
        this.sqlIdentifierValidator = sqlIdentifierValidator;
        this.governanceGuard = governanceGuard;
        this.hookInvoker = hookInvoker;
    }

    @Override
    @Transactional
    public DataResponse insertRow(TableInsertRequest request, Long userId) {
        sqlIdentifierValidator.validate(request.tableName());
        governanceGuard.assertNotSystemTable(request.tableName());
        if (request.rowData() != null) {
            governanceGuard.assertNoRestrictedColumnWrite(request.rowData().keySet());
        }
        if (request.rowData() != null) {
            for (String key : request.rowData().keySet()) {
                if (!"relations".equals(key)) {
                    sqlIdentifierValidator.validate(key);
                }
            }
        }
        if (request.tableName().toLowerCase().endsWith("_log")) {
            throw new ApplicationException(ErrorCode.SYSTEM_LOG_MUTATION_DENIED);
        }
        TableMetadata metadata = tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new ApplicationException(ErrorCode.TABLE_NOT_FOUND, request.tableName()));
        dataHelper.validateRowRegex(metadata, request.rowData());
        dataHelper.validateRowDates(metadata, request.rowData());

        try (ScriptHookSession dbHookSession =
                     hookInvoker.openDbHookSession(metadata.id(), userId).orElse(null)) {
            if (dbHookSession != null) {
                dbHookSession.invokeIfDefined("beforeSaveToDB");
            }

            Map<String, Object> finalRowData = request.rowData() != null ? new HashMap<>(request.rowData()) : new HashMap<>();

            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, Object>>> relations = (Map<String, List<Map<String, Object>>>) finalRowData.remove("relations");

            if (relations != null && !relations.isEmpty()) {
                List<ResolvedRelation> tableRelations = relationService.getRelationsForTable(request.tableName());
                for (Map.Entry<String, List<Map<String, Object>>> entry : relations.entrySet()) {
                    String relationName = entry.getKey();
                    List<Map<String, Object>> relatedItems = entry.getValue();

                    ResolvedRelation relation = tableRelations.stream()
                            .filter(r -> r.relationName().equalsIgnoreCase(relationName))
                            .findFirst()
                            .orElseThrow(() -> new ApplicationException(ErrorCode.BAD_REQUEST, "Relation not found: " + relationName));

                    if (relation.type() == RelationJoinType.FORWARD) {
                        if (relatedItems.size() > 1) {
                            throw new ApplicationException(ErrorCode.BAD_REQUEST, "Multiple items provided for a Many-to-One or One-to-One relation: " + relationName);
                        }
                        Map<String, Object> item = new HashMap<>(relatedItems.getFirst());
                        Object relatedId = item.get("id");
                        if (relatedId != null) {
                            finalRowData.put(relation.baseColumn(), Long.valueOf(relatedId.toString()));
                        } else {
                            TableInsertRequest nestedRequest = new TableInsertRequest(relation.targetTable(), item);
                            DataResponse nestedResponse = insertRow(nestedRequest, userId);
                            finalRowData.put(relation.baseColumn(), nestedResponse.id());
                        }
                    }
                }
            }

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

            if (id == null) {
                throw new ApplicationException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to insert row or retrieve generated ID.");
            }

            if (dbHookSession != null) {
                dbHookSession.invokeIfDefined("afterSaveToDB");
            }

            if (relations != null && !relations.isEmpty()) {
                List<ResolvedRelation> tableRelations = relationService.getRelationsForTable(request.tableName());
                for (Map.Entry<String, List<Map<String, Object>>> entry : relations.entrySet()) {
                    String relationName = entry.getKey();
                    List<Map<String, Object>> relatedItems = entry.getValue();

                    ResolvedRelation relation = tableRelations.stream()
                            .filter(r -> r.relationName().equalsIgnoreCase(relationName))
                            .findFirst()
                            .orElse(null);

                    if (relation != null && relation.type() == RelationJoinType.REVERSE) {
                        for (Map<String, Object> item : relatedItems) {
                            Map<String, Object> itemData = new HashMap<>(item);
                            Object relatedId = itemData.get("id");

                            if (relatedId != null) {
                                updateRowById(relation.targetTable(), Long.valueOf(relatedId.toString()), Map.of(relation.targetColumn(), id), userId);
                            } else {
                                itemData.put(relation.targetColumn(), id);

                                TableInsertRequest nestedRequest = new TableInsertRequest(relation.targetTable(), itemData);
                                insertRow(nestedRequest, userId);
                            }
                        }
                    }
                }
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
    }

    private void validateQueryRequest(QueryRequest request, boolean isRoot) {
        if (request == null) return;
        if (isRoot || request.tableName() != null) {
            sqlIdentifierValidator.validate(request.tableName());
        }
        if (request.fields() != null) {
            for (String field : request.fields()) {
                sqlIdentifierValidator.validate(field);
            }
        }
        if (request.conditions() != null) {
            for (QueryRequest.Condition cond : request.conditions()) {
                sqlIdentifierValidator.validate(cond.column());
            }
        }
        if (request.audit() != null) {
            for (QueryRequest.Condition cond : request.audit()) {
                sqlIdentifierValidator.validate(cond.column());
            }
        }
        if (request.sorts() != null) {
            for (QueryRequest.Sort sort : request.sorts()) {
                sqlIdentifierValidator.validate(sort.column());
            }
        }
        if (request.relations() != null) {
            for (Map.Entry<String, QueryRequest> entry : request.relations().entrySet()) {
                sqlIdentifierValidator.validate(entry.getKey());
                validateQueryRequest(entry.getValue(), false);
            }
        }
    }

    private int getRelationDepth(QueryRequest request) {
        if (request == null || request.relations() == null || request.relations().isEmpty()) {
            return 0;
        }
        int maxSubDepth = 0;
        for (QueryRequest subRequest : request.relations().values()) {
            maxSubDepth = Math.max(maxSubDepth, getRelationDepth(subRequest));
        }
        return 1 + maxSubDepth;
    }

    @Override
    public QueryResponse executeSelect(QueryRequest request, Long userId, Pageable pageable) {
        validateQueryRequest(request, true);
        if (getRelationDepth(request) > 3) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "Relation depth exceeds the maximum cap of 3 levels");
        }
        if (userId != null && userId != 0L && !userRepo.existsByUserId(userId)) {
            throw new ApplicationException(
                    ErrorCode.USER_CONTEXT_INVALID,
                    List.of(new FieldValidationError("userId", "Executing user does not exist")),
                    userId
            );
        }
        TableMetadata metadata = tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new ApplicationException(ErrorCode.TABLE_NOT_FOUND, request.tableName()));
        dataHelper.validateQueryDates(request);

        List<Map<String, Object>> data = executeMainSelect(request, pageable, metadata);

        if (request.relations() != null && !request.relations().isEmpty() && !data.isEmpty()) {
            data = fetchAndAttachRelations(request.tableName(), request.relations(), data);
        }

        List<AuditLogResponse> auditData = new ArrayList<>();
        if (request.audit() != null && !request.audit().isEmpty()) {
            auditData = executeAuditSelect(request, pageable);
        }

        return new QueryResponse(data, auditData);
    }

    private List<Map<String, Object>> executeMainSelect(QueryRequest request, Pageable pageable, TableMetadata metadata) {
        String fieldsStr = buildFieldsString(request.fields(), request.relations(), relationService.getRelationsForTable(request.tableName()), "T", metadata);

        String distinctStr = (request.relations() != null && !request.relations().isEmpty()) ? "DISTINCT " : "";
        StringBuilder sqlBuilder = new StringBuilder(String.format("SELECT %s%s FROM %s T", distinctStr, fieldsStr, request.tableName()));
        
        StringBuilder joinSql = new StringBuilder();
        StringBuilder whereSql = new StringBuilder();
        List<Object> queryParams = new ArrayList<>();

        if (request.relations() != null && !request.relations().isEmpty()) {
            buildJoinsAndConditions("T", request.tableName(), request.relations(), joinSql, whereSql, queryParams, new AtomicInteger(0));
        }

        if (request.conditions() != null && !request.conditions().isEmpty()) {
            for (int i = 0; i < request.conditions().size(); i++) {
                if (i > 0) whereSql.append(" AND ");
                applyCondition(request.conditions().get(i), whereSql, queryParams, false, "T");
            }
        }

        sqlBuilder.append(joinSql);
        if (!whereSql.isEmpty()) {
            sqlBuilder.append(" WHERE ").append(whereSql);
        }

        applySorts(request.sorts(), sqlBuilder, null, "T");

        sqlBuilder.append(" LIMIT ? OFFSET ?");
        queryParams.add(pageable.getPageSize());
        queryParams.add(pageable.getOffset());

        return jdbcTemplate.queryForList(sqlBuilder.toString(), queryParams.toArray());
    }

    private Set<String> sensitiveColumnsFor(TableMetadata metadata) {
        if (metadata == null || metadata.columns() == null) {
            return Set.of();
        }
        return metadata.columns().stream()
                .filter(col -> col.columnContext() != null && Boolean.TRUE.equals(col.columnContext().isSensitive()))
                .map(col -> col.columnName().toLowerCase())
                .collect(Collectors.toSet());
    }

    private String buildFieldsString(List<String> requestedFields, Map<String, QueryRequest> relations, List<ResolvedRelation> resolvedRelations, String alias, TableMetadata metadata) {
        Set<String> sensitiveColumns = sensitiveColumnsFor(metadata);

        List<String> fields;
        if (requestedFields == null || requestedFields.isEmpty()) {
            if (sensitiveColumns.isEmpty() || metadata == null || metadata.columns() == null) {
                return alias + ".*";
            }
            fields = metadata.columns().stream().map(ColumnMetadata::columnName).collect(Collectors.toCollection(ArrayList::new));
        } else {
            fields = new ArrayList<>(requestedFields);
        }
        if (!fields.contains("id")) {
            fields.add("id");
        }

        if (relations != null && resolvedRelations != null) {
            for (String relName : relations.keySet()) {
                resolvedRelations.stream()
                        .filter(r -> r.relationName().equalsIgnoreCase(relName) && r.type() == RelationJoinType.FORWARD)
                        .findFirst()
                        .ifPresent(r -> {
                            if (r.baseColumn() != null && !fields.contains(r.baseColumn())) {
                                fields.add(r.baseColumn());
                            }
                        });
            }
        }

        return fields.stream()
                .map(field -> sensitiveColumns.contains(field.toLowerCase())
                        ? "'********' AS " + field
                        : alias + "." + field)
                .collect(Collectors.joining(", "));
    }

    private ResolvedRelation resolveRelationOrThrow(List<ResolvedRelation> resolvedRelations, String relName, String tableName) {
        return resolvedRelations.stream()
                .filter(r -> r.relationName().equalsIgnoreCase(relName))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RELATION_NOT_FOUND,
                        List.of(new FieldValidationError("relations", "Relation '" + relName + "' does not exist on table '" + tableName + "'")),
                        relName,
                        tableName
                ));
    }

    private void buildJoinsAndConditions(String parentAlias, String tableName, Map<String, QueryRequest> relations, StringBuilder joinSql, StringBuilder whereSql, List<Object> whereParams, AtomicInteger aliasCounter) {
        if (relations == null || relations.isEmpty()) return;
        List<ResolvedRelation> resolvedRelations = relationService.getRelationsForTable(tableName);
        for (Map.Entry<String, QueryRequest> entry : relations.entrySet()) {
            String relName = entry.getKey();
            QueryRequest relQuery = entry.getValue();

            ResolvedRelation match = resolveRelationOrThrow(resolvedRelations, relName, tableName);

            String currentAlias = "B" + aliasCounter.getAndIncrement();
            if (RelationJoinType.M2M == match.type()) {
                String junctionAlias = "J" + aliasCounter.getAndIncrement();
                joinSql.append(String.format(" LEFT JOIN %s %s ON %s.id = %s.%s", match.junctionTable(), junctionAlias, parentAlias, junctionAlias, match.junctionBaseCol()));
                joinSql.append(String.format(" LEFT JOIN %s %s ON %s.%s = %s.id", match.targetTable(), currentAlias, junctionAlias, match.junctionTargetCol(), currentAlias));
            } else if (RelationJoinType.FORWARD == match.type()) {
                String relatedCol = match.targetColumn() != null ? match.targetColumn() : "id";
                joinSql.append(String.format(" LEFT JOIN %s %s ON %s.%s = %s.%s", match.targetTable(), currentAlias, parentAlias, match.baseColumn(), currentAlias, relatedCol));
            } else if (RelationJoinType.REVERSE == match.type()) {
                joinSql.append(String.format(" LEFT JOIN %s %s ON %s.id = %s.%s", match.targetTable(), currentAlias, parentAlias, currentAlias, match.targetColumn()));
            }

            if (relQuery.conditions() != null && !relQuery.conditions().isEmpty()) {
                for (QueryRequest.Condition cond : relQuery.conditions()) {
                    joinSql.append(" AND ");
                    applyCondition(cond, joinSql, whereParams, false, currentAlias);
                }
            }

            buildJoinsAndConditions(currentAlias, match.targetTable(), relQuery.relations(), joinSql, whereSql, whereParams, aliasCounter);
        }
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
        sqlIdentifierValidator.validate(tableName);
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TABLE_NOT_FOUND, tableName));

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
        sqlIdentifierValidator.validate(tableName);
        governanceGuard.assertNotSystemTable(tableName);
        TableMetadata metadata = logAndRestrictedCheck(tableName, id);
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
            throw new ApplicationException(ErrorCode.ROW_NOT_FOUND, id, tableName);
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
        sqlIdentifierValidator.validate(tableName);
        governanceGuard.assertNotSystemTable(tableName);
        if (updateData != null) {
            governanceGuard.assertNoRestrictedColumnWrite(updateData.keySet());
        }
        if (updateData != null) {
            for (String key : updateData.keySet()) {
                sqlIdentifierValidator.validate(key);
            }
        }
        TableMetadata metadata = logAndRestrictedCheck(tableName, id);
        if (updateData == null || updateData.isEmpty()) {
            throw new ApplicationException(
                    ErrorCode.EMPTY_UPDATE_PAYLOAD,
                    List.of(new FieldValidationError("updateData", "Update payload cannot be empty"))
            );
        }
        dataHelper.validateRowRegex(metadata, updateData);
        dataHelper.validateRowDates(metadata, updateData);

        try (ScriptHookSession dbHookSession =
                     hookInvoker.openDbHookSession(metadata.id(), userId).orElse(null)) {
            if (dbHookSession != null) {
                dbHookSession.invokeIfDefined("beforeSaveToDB");
            }

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

            if (dbHookSession != null) {
                dbHookSession.invokeIfDefined("afterSaveToDB");
            }

            kafkaPublisher.publishMutation(tableName, "UPDATE", fullPayload, userId);

            if (rowsAffected == 0) {
                throw new ApplicationException(ErrorCode.ROW_NOT_FOUND, id, tableName);
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
    }

    private TableMetadata logAndRestrictedCheck(String tableName, Long id) {
        if (tableName.toLowerCase().endsWith("_log")) {
            throw new ApplicationException(ErrorCode.SYSTEM_LOG_MUTATION_DENIED);
        }
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TABLE_NOT_FOUND, tableName));
        Boolean currentRestricted = jdbcTemplate.queryForObject(
                String.format("SELECT is_restricted FROM %s WHERE id = ?", tableName), Boolean.class, id);
        governanceGuard.assertRowMutable(Boolean.TRUE.equals(currentRestricted));
        return metadata;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> findRowById(String tableName, Long id, Boolean showSensitive, Long userId) {
        sqlIdentifierValidator.validate(tableName);
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TABLE_NOT_FOUND, tableName));

        String projection = dataHelper.getProjectionClause(metadata, showSensitive);
        String dataSql = String.format("SELECT %s FROM %s WHERE id = ?;", projection, tableName);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, id);
        if (records.isEmpty()) {
            throw new ApplicationException(ErrorCode.ROW_NOT_FOUND, id, tableName);
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


    private String buildSortString(List<QueryRequest.Sort> sorts) {
        if (sorts != null && !sorts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sorts.size(); i++) {
                QueryRequest.Sort sort = sorts.get(i);
                if (i > 0) sb.append(", ");
                String column = "B" + "." + sort.column();
                sb.append(String.format("%s %s", column, sort.direction().getValue()));
            }
            return sb.toString();
        }
        return "B.id ASC";
    }

    private List<Map<String, Object>> fetchAndAttachRelations(String tableName, Map<String, QueryRequest> requestedRelations, List<Map<String, Object>> data) {
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

        for (Map.Entry<String, QueryRequest> entry : requestedRelations.entrySet()) {
            String relName = entry.getKey();
            QueryRequest relQuery = entry.getValue();

            ResolvedRelation match = resolveRelationOrThrow(resolvedRelations, relName, tableName);

            List<Map<String, Object>> relatedRows;
            String targetTable = match.targetTable();

            StringBuilder sqlBuilder = new StringBuilder();
            List<Object> queryParams = new ArrayList<>();
            
            TableMetadata targetMetadata = tableMetadataRepo.findByTableName(targetTable).orElse(null);
            String fieldsStr = buildFieldsString(relQuery.fields(), relQuery.relations(), relationService.getRelationsForTable(targetTable), "B", targetMetadata);

            boolean shouldPaginate = relQuery.page() != null && relQuery.size() != null && RelationJoinType.FORWARD != match.type();
            String orderBy = buildSortString(relQuery.sorts());

            if (RelationJoinType.M2M == match.type()) {
                String placeholders = baseIds.stream().map(id -> "?").collect(Collectors.joining(", "));
                if (shouldPaginate) {
                    sqlBuilder.append(String.format("SELECT * FROM (SELECT J.%s AS base_id, %s, ROW_NUMBER() OVER (PARTITION BY J.%s ORDER BY %s) AS rn FROM %s B JOIN %s J ON B.id = J.%s WHERE J.%s IN (%s)", match.junctionBaseCol(), fieldsStr, match.junctionBaseCol(), orderBy, targetTable, match.junctionTable(), match.junctionTargetCol(), match.junctionBaseCol(), placeholders));
                } else {
                    sqlBuilder.append(String.format("SELECT J.%s AS base_id, %s FROM %s B JOIN %s J ON B.id = J.%s WHERE J.%s IN (%s)", match.junctionBaseCol(), fieldsStr, targetTable, match.junctionTable(), match.junctionTargetCol(), match.junctionBaseCol(), placeholders));
                }
                queryParams.addAll(baseIds);
            } else if (RelationJoinType.FORWARD == match.type()) {
                List<Object> fkValues = new ArrayList<>();
                for (Map<String, Object> row : mutableData) {
                    Object val = row.get(match.baseColumn());
                    if (val != null) {
                        fkValues.add(val);
                    }
                }
                if (fkValues.isEmpty()) {
                    for (Map<String, Object> row : mutableData) {
                        row.put(relName, List.of());
                    }
                    continue;
                }
                String relatedCol = match.targetColumn() != null ? match.targetColumn() : "id";
                String placeholders = fkValues.stream().map(v -> "?").collect(Collectors.joining(", "));
                sqlBuilder.append(String.format("SELECT B.%s AS base_id, %s FROM %s B WHERE B.%s IN (%s)", relatedCol, fieldsStr, targetTable, relatedCol, placeholders));
                queryParams.addAll(fkValues);
            } else if (RelationJoinType.REVERSE == match.type()) {
                String placeholders = baseIds.stream().map(id -> "?").collect(Collectors.joining(", "));
                if (shouldPaginate) {
                    sqlBuilder.append(String.format("SELECT * FROM (SELECT B.%s AS base_id, %s, ROW_NUMBER() OVER (PARTITION BY B.%s ORDER BY %s) AS rn FROM %s B WHERE B.%s IN (%s)", match.targetColumn(), fieldsStr, match.targetColumn(), orderBy, targetTable, match.targetColumn(), placeholders));
                } else {
                    sqlBuilder.append(String.format("SELECT B.%s AS base_id, %s FROM %s B WHERE B.%s IN (%s)", match.targetColumn(), fieldsStr, targetTable, match.targetColumn(), placeholders));
                }
                queryParams.addAll(baseIds);
            }

            if (relQuery.conditions() != null && !relQuery.conditions().isEmpty()) {
                for (QueryRequest.Condition cond : relQuery.conditions()) {
                    sqlBuilder.append(" AND ");
                    applyCondition(cond, sqlBuilder, queryParams, false, "B");
                }
            }

            if (shouldPaginate) {
                sqlBuilder.append(") AS paged WHERE paged.rn > ? AND paged.rn <= ?");
                int offset = relQuery.page() * relQuery.size();
                int limit = offset + relQuery.size();
                queryParams.add(offset);
                queryParams.add(limit);
            } else {
                applySorts(relQuery.sorts(), sqlBuilder, null, "B");
            }

            relatedRows = jdbcTemplate.queryForList(sqlBuilder.toString(), queryParams.toArray());

            if (relQuery.relations() != null && !relQuery.relations().isEmpty() && !relatedRows.isEmpty()) {
                relatedRows = fetchAndAttachRelations(targetTable, relQuery.relations(), relatedRows);
            }

            Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
            for (Map<String, Object> rRow : relatedRows) {
                Object baseIdVal = rRow.remove("base_id");
                rRow.remove("rn");
                if (baseIdVal != null) {
                    String baseIdStr = String.valueOf(baseIdVal);
                    grouped.computeIfAbsent(baseIdStr, k -> new ArrayList<>()).add(rRow);
                }
            }

            for (Map<String, Object> row : mutableData) {
                Object fkOrIdVal;
                if (RelationJoinType.FORWARD == match.type()) {
                    fkOrIdVal = row.get(match.baseColumn());
                } else {
                    fkOrIdVal = row.get("id");
                }
                
                if (fkOrIdVal != null) {
                    String idStr = String.valueOf(fkOrIdVal);
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