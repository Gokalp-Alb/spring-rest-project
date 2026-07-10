package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.mapper.TableMapper;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.relation.ResolvedRelation;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.model.SystemDdlLog;
import com.springrest.springrestproject.model.column.ColumnContext;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.column.SystemColumn;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.model.relation.RelationType;
import com.springrest.springrestproject.model.table.TableContext;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.SystemDdlLogRepo;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.model.relation.RelationMetadata;
import com.springrest.springrestproject.repository.RelationMetadataRepo;
import com.springrest.springrestproject.service.implementations.redis.RelationCacheService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataService implements IMetadataService {

    private final TableMetadataRepo tableMetadataRepo;
    private final RelationMetadataRepo relationMetadataRepo;
    private final JdbcTemplate jdbcTemplate;
    private final SystemDdlLogRepo ddlLogRepo;
    private final TableMapper tableMapper;
    private final RelationCacheService relationCacheService;
    private final IRelationService relationService;

    @Override
    @Transactional
    public TableMetadata createTable(String tableName, TableCreateRequest request, Long userId) {
        if (tableName.toLowerCase().endsWith("_log") || tableName.toLowerCase().endsWith("_jt")) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        List<ColumnMetadata> augmentedColumns = new ArrayList<>(request.columns());
        augmentedColumns.addAll(createSystemColumns());
        List<String> colDefs = new ArrayList<>();

        for (ColumnMetadata col : augmentedColumns) {
            String columnDef = col.columnName() + " " + col.dataType();
            if (col.columnContext() != null && Boolean.TRUE.equals(col.columnContext().isUnique())) {
                columnDef += " UNIQUE";
            }
            colDefs.add(columnDef);
        }

        String columnsSql = String.join(", ", colDefs);
        String additionalColumns = columnsSql.isEmpty() ? "" : ", " + columnsSql;
        String createTableSql = String.format("CREATE TABLE IF NOT EXISTS %s (id SERIAL PRIMARY KEY%s);",
                tableName, additionalColumns);
        logSchemaChange(tableName, createTableSql, userId);
        jdbcTemplate.execute(createTableSql);

        //Audit Creation
        if (request.isAuditEnabled() != null && request.isAuditEnabled()) {
            String logTableName = tableName + "_log";
            String logColumnsSql = augmentedColumns.stream()
                    .map(col -> col.columnName() + " " + col.dataType())
                    .collect(Collectors.joining(", "));
            String logColumnsStr = logColumnsSql.isEmpty() ? "" : ", " + logColumnsSql;
            String createLogTableSql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s (log_id BIGSERIAL PRIMARY KEY, id BIGINT%s, operation_type VARCHAR(50), executed_at TIMESTAMP, user_id BIGINT);",
                    logTableName, logColumnsStr);
            logSchemaChange(logTableName, createLogTableSql, userId);
            jdbcTemplate.execute(createLogTableSql);
        }

        TableContext tableContext = new TableContext(userId, LocalDateTime.now(), userId, LocalDateTime.now());

        List<ColumnMetadata> domainColumns = augmentedColumns.stream().map(srcCol -> {
            ColumnContext srcCtx = srcCol.columnContext();
            ColumnContext targetCtx = ColumnContext.builder()
                    .creatorId(userId)
                    .createdDate(LocalDateTime.now())
                    .lastUpdaterId(userId)
                    .lastChangedDate(LocalDateTime.now())
                    .isSensitive(srcCtx != null && Boolean.TRUE.equals(srcCtx.isSensitive()))
                    .isUnique(srcCtx != null && Boolean.TRUE.equals(srcCtx.isUnique()))
                    .validationRegex(srcCtx != null ? srcCtx.validationRegex() : null)
                    .build();

            return ColumnMetadata.builder()
                    .columnName(srcCol.columnName())
                    .dataType(srcCol.dataType())
                    .columnContext(targetCtx)
                    .tableName(tableName)
                    .build();
        }).toList();

        TableMetadata metadata = TableMetadata.builder()
                .tableName(tableName)
                .columns(domainColumns)
                .tableContext(tableContext)
                .isAuditEnabled(request.isAuditEnabled() != null ? request.isAuditEnabled() : false)
                .build();

        return tableMetadataRepo.save(metadata);
    }

    @Override
    public Page<TableResponse> getAllTables(Pageable pageable) {
        Page<TableMetadata> metadataPage = tableMetadataRepo.findAll(pageable);
        return metadataPage.map(tableMapper::toResponse);
    }

    @Override
    public TableResponse getTableById(Long tableId) {
        TableMetadata metadata = tableMetadataRepo.findById(tableId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return tableMapper.toResponse(metadata);
    }

    @Override
    public TableResponse getTableByName(String tableId) {
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return tableMapper.toResponse(metadata);
    }

    @Override
    @Transactional
    public TableResponse deleteTableByName(String tableName, Long userId) {
        if (tableName.toLowerCase().endsWith("_log") || tableName.toLowerCase().endsWith("_jt")) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        List<RelationMetadata> relations = relationMetadataRepo.findByTableName(tableName);

        boolean useCascade = false;
        
        for (RelationMetadata rel : relations) {
            if (rel.relationType() == RelationType.MANY_TO_MANY) {
                String junctionTable = rel.junctionTable();
                String dropJunctionSql = String.format("DROP TABLE IF EXISTS %s CASCADE;", junctionTable);
                logSchemaChange(junctionTable, dropJunctionSql, userId);
                jdbcTemplate.execute(dropJunctionSql);
                
                tableMetadataRepo.findByTableName(junctionTable).ifPresent(tableMetadataRepo::delete);
                relationMetadataRepo.delete(rel);
                
                String otherTable = rel.sourceTable().equalsIgnoreCase(tableName) ? rel.targetTable() : rel.sourceTable();
                relationCacheService.evict(otherTable);
            } else if (rel.targetTable().equalsIgnoreCase(tableName)) {
                TableMetadata childTable = tableMetadataRepo.findByTableName(rel.sourceTable()).orElse(null);
                DeletePolicy policy = rel.sourceDeletePolicy() != null ? rel.sourceDeletePolicy() : DeletePolicy.CASCADE;
                
                if (policy == DeletePolicy.RESTRICT || policy == DeletePolicy.NO_ACTION) {
                    throw new ApplicationException(ErrorCode.RELATION_RESTRICT, rel.sourceTable());
                }

                String dropColSql = String.format("ALTER TABLE %s DROP COLUMN IF EXISTS %s CASCADE;",
                        rel.sourceTable(), rel.sourceColumn());
                logSchemaChange(rel.sourceTable(), dropColSql, userId);
                jdbcTemplate.execute(dropColSql);

                if (childTable != null && Boolean.TRUE.equals(childTable.isAuditEnabled())) {
                    String dropLogColSql = String.format("ALTER TABLE %s_log DROP COLUMN IF EXISTS %s CASCADE;",
                            rel.sourceTable(), rel.sourceColumn());
                    logSchemaChange(rel.sourceTable() + "_log", dropLogColSql, userId);
                    jdbcTemplate.execute(dropLogColSql);
                }

                if (childTable != null) {
                    List<ColumnMetadata> updatedCols = new ArrayList<>(childTable.columns());
                    updatedCols.removeIf(c -> c.columnName().equalsIgnoreCase(rel.sourceColumn()));
                    TableMetadata updatedChildTable = TableMetadata.builder()
                            .id(childTable.id())
                            .tableName(childTable.tableName())
                            .columns(updatedCols)
                            .tableContext(childTable.tableContext())
                            .isAuditEnabled(childTable.isAuditEnabled())
                            .build();
                    tableMetadataRepo.save(updatedChildTable);
                }
                
                relationMetadataRepo.delete(rel);
                
                if (policy == DeletePolicy.CASCADE) {
                    useCascade = true;
                }
            } else if (rel.sourceTable().equalsIgnoreCase(tableName)) {
                relationMetadataRepo.delete(rel);
            }
        }

        String dropTableSql = useCascade 
                ? String.format("DROP TABLE IF EXISTS %s CASCADE;", tableName)
                : String.format("DROP TABLE IF EXISTS %s;", tableName);

        if (Boolean.TRUE.equals(metadata.isAuditEnabled())) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String renameLogSql = String.format("ALTER TABLE %s_log RENAME TO %s_log_archived_%s;", tableName, tableName, timestamp);
            logSchemaChange(tableName + "_log", renameLogSql, userId);
            jdbcTemplate.execute(renameLogSql);
        }

        logSchemaChange(tableName, dropTableSql, userId);
        jdbcTemplate.execute(dropTableSql);
        
        tableMetadataRepo.delete(metadata);
        relationCacheService.evict(tableName);
        
        return new TableResponse(
                metadata.id(),
                tableName,
                metadata.columns(),
                metadata.tableContext()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSchemaChange(String tableName, String sql, Long userId) {
        SystemDdlLog logEntry = SystemDdlLog.builder()
                .tableName(tableName)
                .executedSql(sql)
                .userId(userId)
                .executedAt(LocalDateTime.now())
                .build();

        ddlLogRepo.save(logEntry);
    }

    private List<ColumnMetadata> createSystemColumns() {
        SystemColumn sysCols = SystemColumn.defaults();

        ColumnContext defaultCtx = ColumnContext.builder()
                .isUnique(false)
                .isSensitive(false)
                .build();

        ColumnMetadata c1 = ColumnMetadata.builder()
                .columnName(sysCols.creatorId().name())
                .dataType(sysCols.creatorId().type())
                .columnContext(defaultCtx)
                .build();

        ColumnMetadata c2 = ColumnMetadata.builder()
                .columnName(sysCols.createdDate().name())
                .dataType(sysCols.createdDate().type())
                .columnContext(defaultCtx)
                .build();

        ColumnMetadata c3 = ColumnMetadata.builder()
                .columnName(sysCols.lastUpdaterId().name())
                .dataType(sysCols.lastUpdaterId().type())
                .columnContext(defaultCtx)
                .build();

        ColumnMetadata c4 = ColumnMetadata.builder()
                .columnName(sysCols.lastChangedDate().name())
                .dataType(sysCols.lastChangedDate().type())
                .columnContext(defaultCtx)
                .build();

        return List.of(c1, c2, c3, c4);
    }


    @Override
    public Map<String, Object> generateSchemaForTable(String tableName) {
        Map<String, Map<String, Object>> defs = new LinkedHashMap<>();

        generateTableSchemaInternal(tableName, defs);

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("$schema", "http://json-schema.org/draft-07/schema#");
        
        Map<String, Object> targetSchema = defs.remove(tableName);
        if (targetSchema != null) {
            rootSchema.putAll(targetSchema);
        }

        if (!defs.isEmpty()) {
            rootSchema.put("$defs", defs);
        }

        return rootSchema;
    }

    private void generateTableSchemaInternal(String tableName, Map<String, Map<String, Object>> defs) {
        if (defs.containsKey(tableName)) {
            return;
        }

        Map<String, Object> tableSchema = new LinkedHashMap<>();
        defs.put(tableName, tableSchema);

        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        tableSchema.put("type", "object");
        tableSchema.put("title", tableName);

        Map<String, Object> properties = new LinkedHashMap<>();
        tableSchema.put("properties", properties);

        // PK and System Columns
        properties.put("id", Map.of("type", "integer"));
        properties.put("creator_id", Map.of("type", "integer"));
        properties.put("created_date", Map.of("type", "string", "format", "date-time"));
        properties.put("last_updater_id", Map.of("type", "integer"));
        properties.put("last_changed_date", Map.of("type", "string", "format", "date-time"));

        // Table Columns
        if (metadata.columns() != null) {
            for (ColumnMetadata col : metadata.columns()) {
                properties.put(col.columnName(), parseDataTypeToJsonSchema(col.dataType()));
            }
        }

        // Relations
        List<ResolvedRelation> relations = relationService.getRelationsForTable(tableName);
        if (relations != null) {
            for (ResolvedRelation rel : relations) {
                generateTableSchemaInternal(rel.targetTable(), defs);

                Map<String, Object> relationProperty = new LinkedHashMap<>();
                relationProperty.put("type", "array");
                relationProperty.put("items", Map.of("$ref", "/api/tables/jsonSchema/" + rel.targetTable()));

                properties.put(rel.relationName(), relationProperty);
            }
        }
    }

    private Map<String, Object> parseDataTypeToJsonSchema(String dataType) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (dataType == null) {
            schema.put("type", "string");
            return schema;
        }

        String cleaned = dataType.trim().toUpperCase();

        // VARCHAR(n)
        Pattern varcharPattern = Pattern.compile("^(VARCHAR|CHARACTER VARYING|CHAR)\\s*\\(\\s*(\\d+)\\s*\\)$");
        Matcher varcharMatcher = varcharPattern.matcher(cleaned);
        if (varcharMatcher.matches()) {
            schema.put("type", "string");
            schema.put("maxLength", Integer.parseInt(varcharMatcher.group(2)));
            return schema;
        }

        // TEXT / CLOB
        if (cleaned.startsWith("TEXT") || cleaned.startsWith("CLOB")) {
            schema.put("type", "string");
            return schema;
        }

        // integers
        if (cleaned.startsWith("INT") || cleaned.startsWith("INTEGER") || cleaned.startsWith("SERIAL")
                || cleaned.startsWith("BIGINT") || cleaned.startsWith("BIGSERIAL") || cleaned.startsWith("SMALLINT")) {
            schema.put("type", "integer");
            return schema;
        }

        // decimal / float
        if (cleaned.startsWith("NUMERIC") || cleaned.startsWith("DECIMAL") || cleaned.startsWith("REAL")
                || cleaned.startsWith("DOUBLE") || cleaned.startsWith("FLOAT")) {
            schema.put("type", "number");
            return schema;
        }

        // boolean
        if (cleaned.startsWith("BOOLEAN") || cleaned.startsWith("BOOL")) {
            schema.put("type", "boolean");
            return schema;
        }

        // date/time
        if (cleaned.startsWith("TIMESTAMP") || cleaned.startsWith("DATE") || cleaned.startsWith("TIME")) {
            schema.put("type", "string");
            schema.put("format", "date-time");
            return schema;
        }

        // Default fallback
        schema.put("type", "string");
        return schema;
    }
}