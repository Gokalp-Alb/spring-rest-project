package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.annotation.PersistenceCache;
import com.springrest.springrestproject.model.column.ColumnContext;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.column.ValidRegexPatterns;
import com.springrest.springrestproject.model.table.TableContext;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.service.implementations.redis.TableMetadataCacheService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.*;

@Repository
@RequiredArgsConstructor
public class TableMetadataRepo {
    private final DSLContext dsl;
    private final TableMetadataCacheService tableMetadataCacheService;

    @Transactional
    public TableMetadata save(TableMetadata metadata) {
        var tableCtx = metadata.tableContext();
        Long tableId;

        boolean isAuditEnabled = metadata.isAuditEnabled() != null ? metadata.isAuditEnabled() : false;
        if (metadata.id() == null) {
            tableId = Objects.requireNonNull(dsl.insertInto(SYS_TABLE_METADATA)
                            .set(SYS_TABLE_METADATA.TABLE_NAME, metadata.tableName())
                            .set(SYS_TABLE_METADATA.CREATOR_ID, tableCtx != null ? tableCtx.creatorId() : null)
                            .set(SYS_TABLE_METADATA.CREATED_DATE, tableCtx != null ? tableCtx.createdDate() : LocalDateTime.now())
                            .set(SYS_TABLE_METADATA.LAST_UPDATER_ID, tableCtx != null ? tableCtx.lastUpdaterId() : null)
                            .set(SYS_TABLE_METADATA.LAST_CHANGED_DATE, tableCtx != null ? tableCtx.lastChangedDate() : LocalDateTime.now())
                            .set(SYS_TABLE_METADATA.IS_AUDIT_ENABLED, isAuditEnabled)
                            .returning(SYS_TABLE_METADATA.ID)
                            .fetchOne())
                    .getValue(SYS_TABLE_METADATA.ID);

            TableMetadata tempMetadataForLog = TableMetadata.builder()
                    .id(tableId)
                    .tableName(metadata.tableName())
                    .tableContext(metadata.tableContext())
                    .isAuditEnabled(metadata.isAuditEnabled())
                    .build();
            logTableMetadataMutation(tempMetadataForLog, "POST");
        } else {
            tableId = metadata.id();
            TableMetadata tempMetadataForLog = TableMetadata.builder()
                    .id(tableId)
                    .tableName(metadata.tableName())
                    .tableContext(metadata.tableContext())
                    .isAuditEnabled(metadata.isAuditEnabled())
                    .build();
            logTableMetadataMutation(tempMetadataForLog, "PUT");

            List<ColumnMetadata> oldColumns = fetchColumnsForTable(tableId);
            for (ColumnMetadata oldCol : oldColumns) {
                logColumnMetadataMutation(oldCol, tableId, "DELETE");
            }

            dsl.update(SYS_TABLE_METADATA)
                    .set(SYS_TABLE_METADATA.TABLE_NAME, metadata.tableName())
                    .set(SYS_TABLE_METADATA.LAST_UPDATER_ID, tableCtx != null ? tableCtx.lastUpdaterId() : null)
                    .set(SYS_TABLE_METADATA.LAST_CHANGED_DATE, tableCtx != null ? tableCtx.lastChangedDate() : LocalDateTime.now())
                    .set(SYS_TABLE_METADATA.IS_AUDIT_ENABLED, isAuditEnabled)
                    .where(SYS_TABLE_METADATA.ID.eq(tableId))
                    .execute();

            dsl.deleteFrom(SYS_COLUMN_METADATA)
                    .where(SYS_COLUMN_METADATA.TABLE_ID.eq(tableId))
                    .execute();
        }

        List<ColumnMetadata> savedColumns = new ArrayList<>();
        if (metadata.columns() != null && !metadata.columns().isEmpty()) {
            for (ColumnMetadata col : metadata.columns()) {
                var colCtx = col.columnContext();
                Long generatedColId = Objects.requireNonNull(dsl.insertInto(SYS_COLUMN_METADATA)
                                .set(SYS_COLUMN_METADATA.TABLE_ID, tableId)
                                .set(SYS_COLUMN_METADATA.COLUMN_NAME, col.columnName())
                                .set(SYS_COLUMN_METADATA.DATA_TYPE, col.dataType())
                                .set(SYS_COLUMN_METADATA.CREATOR_ID, colCtx != null ? colCtx.creatorId() : null)
                                .set(SYS_COLUMN_METADATA.CREATED_DATE, colCtx != null ? colCtx.createdDate() : LocalDateTime.now())
                                .set(SYS_COLUMN_METADATA.LAST_UPDATER_ID, colCtx != null ? colCtx.lastUpdaterId() : null)
                                .set(SYS_COLUMN_METADATA.LAST_CHANGED_DATE, colCtx != null ? colCtx.lastChangedDate() : LocalDateTime.now())
                                .set(SYS_COLUMN_METADATA.IS_SENSITIVE, colCtx != null && Boolean.TRUE.equals(colCtx.isSensitive()))
                                .set(SYS_COLUMN_METADATA.IS_UNIQUE, colCtx != null && Boolean.TRUE.equals(colCtx.isUnique()))
                                .set(SYS_COLUMN_METADATA.VALIDATION_REGEX, colCtx != null && colCtx.validationRegex() != null ? colCtx.validationRegex().name() : null)
                                .returning(SYS_COLUMN_METADATA.ID)
                                .fetchOne())
                        .getValue(SYS_COLUMN_METADATA.ID);
                
                ColumnMetadata savedCol = ColumnMetadata.builder()
                        .id(generatedColId)
                        .columnName(col.columnName())
                        .dataType(col.dataType())
                        .columnContext(col.columnContext())
                        .tableName(metadata.tableName())
                        .build();

                logColumnMetadataMutation(savedCol, tableId, "POST");
                savedColumns.add(savedCol);
            }
        }

        TableMetadata savedMetadata = TableMetadata.builder()
                .id(tableId)
                .tableName(metadata.tableName())
                .columns(savedColumns)
                .tableContext(metadata.tableContext())
                .isAuditEnabled(metadata.isAuditEnabled())
                .build();
        
        tableMetadataCacheService.evict(savedMetadata.tableName());
        return savedMetadata;
    }

    @Transactional
    public void delete(TableMetadata metadata) {
        logTableMetadataMutation(metadata, "DELETE");
        List<ColumnMetadata> oldColumns = fetchColumnsForTable(metadata.id());
        for (ColumnMetadata oldCol : oldColumns) {
            logColumnMetadataMutation(oldCol, metadata.id(), "DELETE");
        }

        dsl.deleteFrom(SYS_COLUMN_METADATA)
                .where(SYS_COLUMN_METADATA.TABLE_ID.eq(metadata.id()))
                .execute();

        dsl.deleteFrom(SYS_TABLE_METADATA)
                .where(SYS_TABLE_METADATA.ID.eq(metadata.id()))
                .execute();
                
        tableMetadataCacheService.evict(metadata.tableName());
    }

    private void logTableMetadataMutation(TableMetadata metadata, String operation) {
        Long executorId = com.springrest.springrestproject.util.SecurityUtils.getCurrentUserId();
        if (executorId == null) {
            executorId = 0L;
        }
        var tableCtx = metadata.tableContext();
        dsl.insertInto(SYS_TABLE_METADATA_LOG)
                .set(SYS_TABLE_METADATA_LOG.ID, metadata.id())
                .set(SYS_TABLE_METADATA_LOG.TABLE_NAME, metadata.tableName())
                .set(SYS_TABLE_METADATA_LOG.CREATOR_ID, tableCtx != null ? tableCtx.creatorId() : null)
                .set(SYS_TABLE_METADATA_LOG.CREATED_DATE, tableCtx != null ? tableCtx.createdDate() : LocalDateTime.now())
                .set(SYS_TABLE_METADATA_LOG.LAST_UPDATER_ID, tableCtx != null ? tableCtx.lastUpdaterId() : null)
                .set(SYS_TABLE_METADATA_LOG.LAST_CHANGED_DATE, tableCtx != null ? tableCtx.lastChangedDate() : LocalDateTime.now())
                .set(SYS_TABLE_METADATA_LOG.IS_AUDIT_ENABLED, metadata.isAuditEnabled() != null ? metadata.isAuditEnabled() : false)
                .set(SYS_TABLE_METADATA_LOG.OPERATION_TYPE, operation)
                .set(SYS_TABLE_METADATA_LOG.EXECUTED_AT, LocalDateTime.now())
                .set(SYS_TABLE_METADATA_LOG.USER_ID, executorId)
                .execute();
    }

    private void logColumnMetadataMutation(ColumnMetadata col, Long tableId, String operation) {
        Long executorId = com.springrest.springrestproject.util.SecurityUtils.getCurrentUserId();
        if (executorId == null) {
            executorId = 0L;
        }
        var colCtx = col.columnContext();
        dsl.insertInto(SYS_COLUMN_METADATA_LOG)
                .set(SYS_COLUMN_METADATA_LOG.ID, col.id())
                .set(SYS_COLUMN_METADATA_LOG.TABLE_ID, tableId)
                .set(SYS_COLUMN_METADATA_LOG.COLUMN_NAME, col.columnName())
                .set(SYS_COLUMN_METADATA_LOG.DATA_TYPE, col.dataType())
                .set(SYS_COLUMN_METADATA_LOG.CREATOR_ID, colCtx != null ? colCtx.creatorId() : null)
                .set(SYS_COLUMN_METADATA_LOG.CREATED_DATE, colCtx != null ? colCtx.createdDate() : LocalDateTime.now())
                .set(SYS_COLUMN_METADATA_LOG.LAST_UPDATER_ID, colCtx != null ? colCtx.lastUpdaterId() : null)
                .set(SYS_COLUMN_METADATA_LOG.LAST_CHANGED_DATE, colCtx != null ? colCtx.lastChangedDate() : LocalDateTime.now())
                .set(SYS_COLUMN_METADATA_LOG.IS_SENSITIVE, colCtx != null && Boolean.TRUE.equals(colCtx.isSensitive()))
                .set(SYS_COLUMN_METADATA_LOG.IS_UNIQUE, colCtx != null && Boolean.TRUE.equals(colCtx.isUnique()))
                .set(SYS_COLUMN_METADATA_LOG.VALIDATION_REGEX, colCtx != null && colCtx.validationRegex() != null ? colCtx.validationRegex().name() : null)
                .set(SYS_COLUMN_METADATA_LOG.OPERATION_TYPE, operation)
                .set(SYS_COLUMN_METADATA_LOG.EXECUTED_AT, LocalDateTime.now())
                .set(SYS_COLUMN_METADATA_LOG.USER_ID, executorId)
                .execute();
    }

    public Page<TableMetadata> findAll(Pageable pageable) {
        int total = dsl.fetchCount(SYS_TABLE_METADATA);

        List<TableMetadata> tables = dsl.selectFrom(SYS_TABLE_METADATA)
                .limit(pageable.getPageSize())
                .offset((int) pageable.getOffset())
                .fetch(this::mapRecordToTableMetadata);

        return new PageImpl<>(tables, pageable, total);
    }

    private List<ColumnMetadata> fetchColumnsForTable(Long tableId) {
        return dsl.selectFrom(SYS_COLUMN_METADATA)
                .where(SYS_COLUMN_METADATA.TABLE_ID.eq(tableId))
                .fetch(this::mapRecordToColumnMetadata);
    }

    public Optional<TableMetadata> findById(Long tableId) {
        Record record = dsl.selectFrom(SYS_TABLE_METADATA)
                .where(SYS_TABLE_METADATA.ID.eq(tableId))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(mapRecordToTableMetadata(record));
    }

    @PersistenceCache(cacheName = "table-metadata", tableName = "#tableName")
    public Optional<TableMetadata> findByTableName(String tableName) {
        Record record = dsl.selectFrom(SYS_TABLE_METADATA)
                .where(SYS_TABLE_METADATA.TABLE_NAME.eq(tableName))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        TableMetadata metadata = mapRecordToTableMetadata(record);
        return Optional.of(metadata);
    }

    private TableMetadata mapRecordToTableMetadata(Record record) {
        Long id = record.get(SYS_TABLE_METADATA.ID);
        String name = record.get(SYS_TABLE_METADATA.TABLE_NAME);
        Boolean audit = record.get(SYS_TABLE_METADATA.IS_AUDIT_ENABLED);

        TableContext ctx = null;
        if (record.get(SYS_TABLE_METADATA.CREATOR_ID) != null || record.get(SYS_TABLE_METADATA.CREATED_DATE) != null) {
            ctx = new TableContext(
                record.get(SYS_TABLE_METADATA.CREATOR_ID),
                record.get(SYS_TABLE_METADATA.CREATED_DATE),
                record.get(SYS_TABLE_METADATA.LAST_UPDATER_ID),
                record.get(SYS_TABLE_METADATA.LAST_CHANGED_DATE),
                null
            );
        }

        return TableMetadata.builder()
                .id(id)
                .tableName(name)
                .isAuditEnabled(audit)
                .tableContext(ctx)
                .columns(fetchColumnsForTable(id))
                .build();
    }

    private ColumnMetadata mapRecordToColumnMetadata(Record record) {
        ColumnContext ctx = null;
        try {
            if (record.get(SYS_COLUMN_METADATA.CREATOR_ID) != null || record.get(SYS_COLUMN_METADATA.CREATED_DATE) != null) {
                ctx = ColumnContext.builder()
                        .creatorId(record.get(SYS_COLUMN_METADATA.CREATOR_ID))
                        .createdDate(record.get(SYS_COLUMN_METADATA.CREATED_DATE))
                        .lastUpdaterId(record.get(SYS_COLUMN_METADATA.LAST_UPDATER_ID))
                        .lastChangedDate(record.get(SYS_COLUMN_METADATA.LAST_CHANGED_DATE))
                        .isSensitive(record.get(SYS_COLUMN_METADATA.IS_SENSITIVE))
                        .isUnique(record.get(SYS_COLUMN_METADATA.IS_UNIQUE))
                        .validationRegex(ValidRegexPatterns.fromValue(record.get(SYS_COLUMN_METADATA.VALIDATION_REGEX)))
                        .build();
            }
        } catch (IllegalArgumentException ignored) {
        }

        return ColumnMetadata.builder()
                .id(record.get(SYS_COLUMN_METADATA.ID))
                .columnName(record.get(SYS_COLUMN_METADATA.COLUMN_NAME))
                .dataType(record.get(SYS_COLUMN_METADATA.DATA_TYPE))
                .columnContext(ctx)
                .build();
    }
}