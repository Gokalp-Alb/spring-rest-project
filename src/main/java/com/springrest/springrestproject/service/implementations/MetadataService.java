package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.mapper.TableMapper;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.model.SystemDdlLog;
import com.springrest.springrestproject.model.column.ColumnContext;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.column.DeletePolicy;
import com.springrest.springrestproject.model.column.RelationType;
import com.springrest.springrestproject.model.table.TableContext;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.SystemDdlLogRepo;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.validators.ColumnRelationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataService implements IMetadataService {

    private final TableMetadataRepo tableMetadataRepo;
    private final SystemDdlLogRepo ddlLogRepo;
    private final JdbcTemplate jdbcTemplate;
    private final TableMapper tableMapper;
    private final ColumnRelationValidator columnRelationValidator;

    @Override
    @Transactional
    public TableMetadata createTable(String tableName, TableCreateRequest request, Long userId) {
        if (tableName.toLowerCase().endsWith("_log")) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        columnRelationValidator.validate(request.columns());

        List<String> colDefs = new ArrayList<>();
        List<String> constraintDefs = new ArrayList<>();

        for (ColumnMetadata col : request.columns()) {
            String columnDef = col.getColumnName() + " " + col.getDataType();
            if (col.getRelationType() == RelationType.ONE_TO_ONE) {
                columnDef += " UNIQUE";
                String targetColName = col.getRelatedColumn() != null ? col.getRelatedColumn() : "id";
                DeletePolicy policy = col.getDeletePolicy() != null ? col.getDeletePolicy() : DeletePolicy.CASCADE;
                constraintDefs.add(String.format("CONSTRAINT fk_%s_%s FOREIGN KEY (%s) REFERENCES %s(%s) %s",
                        tableName, col.getColumnName(), col.getColumnName(), col.getRelatedTable(), targetColName, policy.getSql()));
            } else {
                if (col.getColumnContext() != null && col.getColumnContext().getIsUnique()) {
                    columnDef += " UNIQUE";
                }
            }
            colDefs.add(columnDef);
        }

        String columnsSql = String.join(", ", colDefs);
        if (!constraintDefs.isEmpty()) {
            columnsSql += ", " + String.join(", ", constraintDefs);
        }
        String createTableSql = String.format("CREATE TABLE IF NOT EXISTS %s (id SERIAL PRIMARY KEY, %s);",
                tableName, columnsSql);
        logSchemaChange(tableName, createTableSql, userId);
        jdbcTemplate.execute(createTableSql);

        //Audit Creation
        if (request.isAuditEnabled() != null && request.isAuditEnabled()) {
            String logTableName = tableName + "_log";
            String logColumnsSql = request.columns().stream()
                    .map(col -> col.getColumnName() + " " + col.getDataType())
                    .collect(Collectors.joining(", "));
            String logColumnsStr = logColumnsSql.isEmpty() ? "" : ", " + logColumnsSql;
            String createLogTableSql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s (log_id BIGSERIAL PRIMARY KEY, id BIGINT%s, operation_type VARCHAR(50), executed_at TIMESTAMP, user_id BIGINT);",
                    logTableName, logColumnsStr);
            logSchemaChange(logTableName, createLogTableSql, userId);
            jdbcTemplate.execute(createLogTableSql);
        }

        TableContext tableContext = new TableContext();
        tableContext.setCreatorId(userId);
        tableContext.setCreatedDate(java.time.LocalDateTime.now());
        tableContext.setLastUpdaterId(userId);
        tableContext.setLastChangedDate(java.time.LocalDateTime.now());

        List<ColumnMetadata> domainColumns = request.columns().stream().map(srcCol -> {
            ColumnMetadata targetCol = new ColumnMetadata();
            targetCol.setColumnName(srcCol.getColumnName());
            targetCol.setDataType(srcCol.getDataType());
            targetCol.setRelationType(srcCol.getRelationType());
            targetCol.setRelatedTable(srcCol.getRelatedTable());
            targetCol.setRelatedColumn(srcCol.getRelatedColumn() != null ? srcCol.getRelatedColumn() : "id");
            targetCol.setDeletePolicy(srcCol.getDeletePolicy() != null ? srcCol.getDeletePolicy() : DeletePolicy.CASCADE);

            ColumnContext srcCtx = srcCol.getColumnContext();
            ColumnContext targetCtx = new ColumnContext();
            targetCtx.setCreatorId(userId);
            targetCtx.setCreatedDate(java.time.LocalDateTime.now());
            targetCtx.setLastUpdaterId(userId);
            targetCtx.setLastChangedDate(java.time.LocalDateTime.now());
            targetCtx.setIsSensitive(srcCtx != null && srcCtx.getIsSensitive() != null ? srcCtx.getIsSensitive() : false);
            targetCtx.setIsUnique(srcCtx != null && srcCtx.getIsUnique() != null ? srcCtx.getIsUnique() : false);
            targetCtx.setValidationRegex(srcCtx != null ? srcCtx.getValidationRegex() : null);

            targetCol.setColumnContext(targetCtx);
            return targetCol;
        }).toList();

        TableMetadata metadata = new TableMetadata();
        metadata.setTableName(tableName);
        metadata.setColumns(domainColumns);
        metadata.setTableContext(tableContext);
        metadata.setIsAuditEnabled(request.isAuditEnabled() != null ? request.isAuditEnabled() : false);

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
        TableMetadata metadata = tableMetadataRepo.findByName(tableId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return tableMapper.toResponse(metadata);
    }

    @Override
    @Transactional
    public TableResponse deleteTableByName(String tableName, Long userId) {
        if (tableName.toLowerCase().endsWith("_log")) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        TableMetadata metadata = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        List<ColumnMetadata> incomingFKs = tableMetadataRepo.findColumnsPointingToTable(tableName);

        boolean useCascade = false;
        for (ColumnMetadata fkCol : incomingFKs) {
            DeletePolicy policy = fkCol.getDeletePolicy() != null ? fkCol.getDeletePolicy() : DeletePolicy.CASCADE;
            switch (policy) {
                case CASCADE -> useCascade = true;

                case RESTRICT, NO_ACTION -> throw new ApplicationException(
                            ErrorCode.RELATION_RESTRICT,
                            fkCol.getTableName()
                    );

                case SET_NULL -> {
                    String constraintName = String.format("fk_%s_%s", fkCol.getTableName(), fkCol.getColumnName());

                    String dropConstraintSql = String.format("ALTER TABLE %s DROP CONSTRAINT IF EXISTS %s;",
                            fkCol.getTableName(), constraintName);
                    logSchemaChange(fkCol.getTableName(), dropConstraintSql, userId);
                    jdbcTemplate.execute(dropConstraintSql);

                    // Nullify column dynamically
                    String nullifySql = String.format("UPDATE %s SET %s = NULL;",
                            fkCol.getTableName(), fkCol.getColumnName());
                    logSchemaChange(fkCol.getTableName(), nullifySql, userId);
                    jdbcTemplate.execute(nullifySql);
                }
            }
        }

        String dropTableSql = useCascade 
                ? String.format("DROP TABLE IF EXISTS %s CASCADE;", tableName)
                : String.format("DROP TABLE IF EXISTS %s;", tableName);

        logSchemaChange(tableName, dropTableSql, userId);
        jdbcTemplate.execute(dropTableSql);
        tableMetadataRepo.delete(metadata);
        return new TableResponse(
                metadata.getId(),
                tableName,
                metadata.getColumns(),
                metadata.getTableContext()
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

}