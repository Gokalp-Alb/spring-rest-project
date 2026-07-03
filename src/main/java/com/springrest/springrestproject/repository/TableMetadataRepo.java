package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.column.ColumnContext;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.model.relation.RelationType;
import com.springrest.springrestproject.model.table.TableContext;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.service.implementations.redis.RelationCacheService;
import com.springrest.springrestproject.service.implementations.redis.TableMetadataCacheService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.*;

@Repository
@RequiredArgsConstructor
public class TableMetadataRepo {
    private final DSLContext dsl;
    private final RelationCacheService relationCacheService;
    private final TableMetadataCacheService tableMetadataCacheService;

    @Transactional
    public TableMetadata save(TableMetadata metadata) {
        var tableCtx = metadata.getTableContext();

        if (metadata.getId() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(TABLE_METADATA)
                            .set(TABLE_METADATA.TABLE_NAME, metadata.getTableName())
                            .set(TABLE_METADATA.CREATOR_ID, tableCtx != null ? tableCtx.getCreatorId() : null)
                            .set(TABLE_METADATA.CREATED_DATE, tableCtx != null ? tableCtx.getCreatedDate() : LocalDateTime.now())
                            .set(TABLE_METADATA.LAST_UPDATER_ID, tableCtx != null ? tableCtx.getLastUpdaterId() : null)
                            .set(TABLE_METADATA.LAST_CHANGED_DATE, tableCtx != null ? tableCtx.getLastChangedDate() : LocalDateTime.now())
                            .set(TABLE_METADATA.IS_AUDIT_ENABLED, metadata.getIsAuditEnabled() != null ? metadata.getIsAuditEnabled() : false)
                            .returning(TABLE_METADATA.ID)
                            .fetchOne())
                    .getValue(TABLE_METADATA.ID);

            metadata.setId(generatedId);
            logTableMetadataMutation(metadata, "POST");
        } else {
            logTableMetadataMutation(metadata, "PUT");
            List<ColumnMetadata> oldColumns = fetchColumnsForTable(metadata.getId());
            for (ColumnMetadata oldCol : oldColumns) {
                logColumnMetadataMutation(oldCol, metadata.getId(), "DELETE");
            }

            dsl.update(TABLE_METADATA)
                    .set(TABLE_METADATA.TABLE_NAME, metadata.getTableName())
                    .set(TABLE_METADATA.LAST_UPDATER_ID, tableCtx != null ? tableCtx.getLastUpdaterId() : null)
                    .set(TABLE_METADATA.LAST_CHANGED_DATE, tableCtx != null ? tableCtx.getLastChangedDate() : LocalDateTime.now())
                    .set(TABLE_METADATA.IS_AUDIT_ENABLED, metadata.getIsAuditEnabled() != null ? metadata.getIsAuditEnabled() : false)
                    .where(TABLE_METADATA.ID.eq(metadata.getId()))
                    .execute();

            dsl.deleteFrom(COLUMN_METADATA)
                    .where(COLUMN_METADATA.TABLE_ID.eq(metadata.getId()))
                    .execute();
        }

        if (metadata.getColumns() != null && !metadata.getColumns().isEmpty()) {
            for (ColumnMetadata col : metadata.getColumns()) {
                var colCtx = col.getColumnContext();
                Long generatedColId = Objects.requireNonNull(dsl.insertInto(COLUMN_METADATA)
                                .set(COLUMN_METADATA.TABLE_ID, metadata.getId())
                                .set(COLUMN_METADATA.COLUMN_NAME, col.getColumnName())
                                .set(COLUMN_METADATA.DATA_TYPE, col.getDataType())
                                .set(COLUMN_METADATA.CREATOR_ID, colCtx != null ? colCtx.getCreatorId() : null)
                                .set(COLUMN_METADATA.CREATED_DATE, colCtx != null ? colCtx.getCreatedDate() : LocalDateTime.now())
                                .set(COLUMN_METADATA.LAST_UPDATER_ID, colCtx != null ? colCtx.getLastUpdaterId() : null)
                                .set(COLUMN_METADATA.LAST_CHANGED_DATE, colCtx != null ? colCtx.getLastChangedDate() : LocalDateTime.now())
                                .set(COLUMN_METADATA.IS_SENSITIVE, colCtx != null ? colCtx.getIsSensitive() : false)
                                .set(COLUMN_METADATA.IS_UNIQUE, colCtx != null ? colCtx.getIsUnique() : false)
                                .set(COLUMN_METADATA.VALIDATION_REGEX, colCtx != null ? colCtx.getValidationRegex() : null)
                                .set(COLUMN_METADATA.RELATION_TYPE, col.getRelationType() != null ? col.getRelationType().name() : null)
                                .set(COLUMN_METADATA.RELATED_TABLE, col.getRelatedTable())
                                .set(COLUMN_METADATA.RELATED_COLUMN, col.getRelatedColumn())
                                .set(COLUMN_METADATA.DELETE_POLICY, col.getDeletePolicy() != null ? col.getDeletePolicy().name() : null)
                                .returning(COLUMN_METADATA.ID)
                                .fetchOne())
                        .getValue(COLUMN_METADATA.ID);
                col.setId(generatedColId);
                logColumnMetadataMutation(col, metadata.getId(), "POST");
            }
        }
        
        tableMetadataCacheService.evict(metadata.getTableName());
        return metadata;
    }

    @Transactional
    public void delete(TableMetadata metadata) {
        logTableMetadataMutation(metadata, "DELETE");
        List<ColumnMetadata> oldColumns = fetchColumnsForTable(metadata.getId());
        for (ColumnMetadata oldCol : oldColumns) {
            logColumnMetadataMutation(oldCol, metadata.getId(), "DELETE");
        }

        dsl.deleteFrom(COLUMN_METADATA)
                .where(COLUMN_METADATA.TABLE_ID.eq(metadata.getId()))
                .execute();

        dsl.deleteFrom(TABLE_METADATA)
                .where(TABLE_METADATA.ID.eq(metadata.getId()))
                .execute();
                
        tableMetadataCacheService.evict(metadata.getTableName());
    }

    private void logTableMetadataMutation(TableMetadata metadata, String operation) {
        Long executorId = com.springrest.springrestproject.util.SecurityUtils.getCurrentUserId();
        if (executorId == null) {
            executorId = 0L;
        }
        var tableCtx = metadata.getTableContext();
        dsl.insertInto(TABLE_METADATA_LOG)
                .set(TABLE_METADATA_LOG.ID, metadata.getId())
                .set(TABLE_METADATA_LOG.TABLE_NAME, metadata.getTableName())
                .set(TABLE_METADATA_LOG.CREATOR_ID, tableCtx != null ? tableCtx.getCreatorId() : null)
                .set(TABLE_METADATA_LOG.CREATED_DATE, tableCtx != null ? tableCtx.getCreatedDate() : java.time.LocalDateTime.now())
                .set(TABLE_METADATA_LOG.LAST_UPDATER_ID, tableCtx != null ? tableCtx.getLastUpdaterId() : null)
                .set(TABLE_METADATA_LOG.LAST_CHANGED_DATE, tableCtx != null ? tableCtx.getLastChangedDate() : java.time.LocalDateTime.now())
                .set(TABLE_METADATA_LOG.IS_AUDIT_ENABLED, metadata.getIsAuditEnabled() != null ? metadata.getIsAuditEnabled() : false)
                .set(TABLE_METADATA_LOG.OPERATION_TYPE, operation)
                .set(TABLE_METADATA_LOG.EXECUTED_AT, java.time.LocalDateTime.now())
                .set(TABLE_METADATA_LOG.USER_ID, executorId)
                .execute();
    }

    private void logColumnMetadataMutation(ColumnMetadata col, Long tableId, String operation) {
        Long executorId = com.springrest.springrestproject.util.SecurityUtils.getCurrentUserId();
        if (executorId == null) {
            executorId = 0L;
        }
        var colCtx = col.getColumnContext();
        dsl.insertInto(COLUMN_METADATA_LOG)
                .set(COLUMN_METADATA_LOG.ID, col.getId())
                .set(COLUMN_METADATA_LOG.TABLE_ID, tableId)
                .set(COLUMN_METADATA_LOG.COLUMN_NAME, col.getColumnName())
                .set(COLUMN_METADATA_LOG.DATA_TYPE, col.getDataType())
                .set(COLUMN_METADATA_LOG.CREATOR_ID, colCtx != null ? colCtx.getCreatorId() : null)
                .set(COLUMN_METADATA_LOG.CREATED_DATE, colCtx != null ? colCtx.getCreatedDate() : java.time.LocalDateTime.now())
                .set(COLUMN_METADATA_LOG.LAST_UPDATER_ID, colCtx != null ? colCtx.getLastUpdaterId() : null)
                .set(COLUMN_METADATA_LOG.LAST_CHANGED_DATE, colCtx != null ? colCtx.getLastChangedDate() : java.time.LocalDateTime.now())
                .set(COLUMN_METADATA_LOG.IS_SENSITIVE, colCtx != null ? colCtx.getIsSensitive() : false)
                .set(COLUMN_METADATA_LOG.IS_UNIQUE, colCtx != null ? colCtx.getIsUnique() : false)
                .set(COLUMN_METADATA_LOG.VALIDATION_REGEX, colCtx != null ? colCtx.getValidationRegex() : null)
                .set(COLUMN_METADATA_LOG.RELATION_TYPE, col.getRelationType() != null ? col.getRelationType().name() : null)
                .set(COLUMN_METADATA_LOG.RELATED_TABLE, col.getRelatedTable())
                .set(COLUMN_METADATA_LOG.RELATED_COLUMN, col.getRelatedColumn())
                .set(COLUMN_METADATA_LOG.DELETE_POLICY, col.getDeletePolicy() != null ? col.getDeletePolicy().name() : null)
                .set(COLUMN_METADATA_LOG.OPERATION_TYPE, operation)
                .set(COLUMN_METADATA_LOG.EXECUTED_AT, java.time.LocalDateTime.now())
                .set(COLUMN_METADATA_LOG.USER_ID, executorId)
                .execute();
    }

    public Page<TableMetadata> findAll(Pageable pageable) {
        int total = dsl.fetchCount(TABLE_METADATA);

        List<TableMetadata> tables = dsl.selectFrom(TABLE_METADATA)
                .limit(pageable.getPageSize())
                .offset((int) pageable.getOffset())
                .fetchInto(TableMetadata.class);

        for (TableMetadata table : tables) {
            table.setColumns(fetchColumnsForTable(table.getId()));
            populateTableContext(table);
        }

        return new PageImpl<>(tables, pageable, total);
    }

    private List<ColumnMetadata> fetchColumnsForTable(Long tableId) {
        return dsl.selectFrom(COLUMN_METADATA)
                .where(COLUMN_METADATA.TABLE_ID.eq(tableId))
                .fetch(this::mapRecordToColumnMetadata);
    }

    public Optional<TableMetadata> findById(Long tableId) {
        TableMetadata metadata = dsl.selectFrom(TABLE_METADATA)
                .where(TABLE_METADATA.ID.eq(tableId))
                .fetchOneInto(TableMetadata.class);
        if (metadata != null) {
            metadata.setColumns(fetchColumnsForTable(tableId));
            populateTableContext(metadata);
        }

        return Optional.ofNullable(metadata);
    }

    public Optional<TableMetadata> findByTableName(String tableName) {
        return tableMetadataCacheService.get(tableName).or(() -> {
            TableMetadata metadata = dsl.selectFrom(TABLE_METADATA)
                    .where(TABLE_METADATA.TABLE_NAME.eq(tableName))
                    .fetchOneInto(TableMetadata.class);
            if (metadata != null) {
                metadata.setColumns(fetchColumnsForTable(metadata.getId()));
                populateTableContext(metadata);
                tableMetadataCacheService.put(metadata);
            }
            return Optional.ofNullable(metadata);
        });
    }

    private void populateTableContext(TableMetadata metadata) {
        if (metadata == null) return;
        var record = dsl.select(TABLE_METADATA.CREATOR_ID, TABLE_METADATA.CREATED_DATE,
                                TABLE_METADATA.LAST_UPDATER_ID, TABLE_METADATA.LAST_CHANGED_DATE)
                .from(TABLE_METADATA)
                .where(TABLE_METADATA.ID.eq(metadata.getId()))
                .fetchOne();
        if (record != null) {
            TableContext ctx = new TableContext();
            ctx.setCreatorId(record.get(TABLE_METADATA.CREATOR_ID));
            ctx.setCreatedDate(record.get(TABLE_METADATA.CREATED_DATE));
            ctx.setLastUpdaterId(record.get(TABLE_METADATA.LAST_UPDATER_ID));
            ctx.setLastChangedDate(record.get(TABLE_METADATA.LAST_CHANGED_DATE));
            metadata.setTableContext(ctx);
        }
    }

    public List<ColumnMetadata> getIncomingFKs(String tableName) {
        return relationCacheService.get(tableName)
                .orElseGet(() -> {
                    List<ColumnMetadata> result = findColumnsPointingToTable(tableName);
                    relationCacheService.put(tableName, result);
                    return result;
                });
    }

    public List<ColumnMetadata> findAllRelationColumns() {
        return dsl.select(
                    COLUMN_METADATA.ID,
                    COLUMN_METADATA.COLUMN_NAME,
                    COLUMN_METADATA.DATA_TYPE,
                    COLUMN_METADATA.RELATION_TYPE,
                    COLUMN_METADATA.RELATED_TABLE,
                    COLUMN_METADATA.RELATED_COLUMN,
                    COLUMN_METADATA.DELETE_POLICY,
                    TABLE_METADATA.TABLE_NAME
                )
                .from(COLUMN_METADATA)
                .join(TABLE_METADATA).on(COLUMN_METADATA.TABLE_ID.eq(TABLE_METADATA.ID))
                .where(COLUMN_METADATA.RELATION_TYPE.isNotNull())
                .fetch(record -> {
                    ColumnMetadata col = mapRecordToColumnMetadata(record);
                    col.setTableName(record.get(TABLE_METADATA.TABLE_NAME));
                    return col;
                });
    }

    private List<ColumnMetadata> findColumnsPointingToTable(String tableName) {
        return dsl.select(
                    COLUMN_METADATA.ID,
                    COLUMN_METADATA.COLUMN_NAME,
                    COLUMN_METADATA.DATA_TYPE,
                    COLUMN_METADATA.RELATION_TYPE,
                    COLUMN_METADATA.RELATED_TABLE,
                    COLUMN_METADATA.RELATED_COLUMN,
                    COLUMN_METADATA.DELETE_POLICY,
                    TABLE_METADATA.TABLE_NAME
                )
                .from(COLUMN_METADATA)
                .join(TABLE_METADATA).on(COLUMN_METADATA.TABLE_ID.eq(TABLE_METADATA.ID))
                .where(COLUMN_METADATA.RELATED_TABLE.equalIgnoreCase(tableName))
                .fetch(record -> {
                    ColumnMetadata col = mapRecordToColumnMetadata(record);
                    col.setTableName(record.get(TABLE_METADATA.TABLE_NAME));
                    return col;
                });
    }

    private ColumnMetadata mapRecordToColumnMetadata(Record record) {
        ColumnMetadata col = new ColumnMetadata();
        col.setId(record.get(COLUMN_METADATA.ID));
        col.setColumnName(record.get(COLUMN_METADATA.COLUMN_NAME));
        col.setDataType(record.get(COLUMN_METADATA.DATA_TYPE));

        try {
            if (record.get(COLUMN_METADATA.CREATOR_ID) != null || record.get(COLUMN_METADATA.CREATED_DATE) != null) {
                var ctx = new ColumnContext();
                ctx.setCreatorId(record.get(COLUMN_METADATA.CREATOR_ID));
                ctx.setCreatedDate(record.get(COLUMN_METADATA.CREATED_DATE));
                ctx.setLastUpdaterId(record.get(COLUMN_METADATA.LAST_UPDATER_ID));
                ctx.setLastChangedDate(record.get(COLUMN_METADATA.LAST_CHANGED_DATE));
                ctx.setIsSensitive(record.get(COLUMN_METADATA.IS_SENSITIVE));
                ctx.setIsUnique(record.get(COLUMN_METADATA.IS_UNIQUE));
                ctx.setValidationRegex(record.get(COLUMN_METADATA.VALIDATION_REGEX));
                col.setColumnContext(ctx);
            }
        } catch (IllegalArgumentException ignored) {
        }

        String relTypeStr = record.get(COLUMN_METADATA.RELATION_TYPE);
        col.setRelationType(relTypeStr != null ? RelationType.valueOf(relTypeStr) : null);
        col.setRelatedTable(record.get(COLUMN_METADATA.RELATED_TABLE));
        col.setRelatedColumn(record.get(COLUMN_METADATA.RELATED_COLUMN));
        String delPolicyStr = record.get(COLUMN_METADATA.DELETE_POLICY);
        col.setDeletePolicy(delPolicyStr != null ? DeletePolicy.valueOf(delPolicyStr) : null);

        return col;
    }

    public List<String> findJunctionTableNames() {
        return dsl.select(TABLE_METADATA.TABLE_NAME)
                .from(TABLE_METADATA)
                .join(COLUMN_METADATA).on(COLUMN_METADATA.TABLE_ID.eq(TABLE_METADATA.ID))
                .groupBy(TABLE_METADATA.ID, TABLE_METADATA.TABLE_NAME)
                .having(DSL.count().eq(2)
                        .and(DSL.count(DSL.when(COLUMN_METADATA.RELATION_TYPE.eq(RelationType.MANY_TO_ONE.name()), 1)).eq(2)))
                .fetch(TABLE_METADATA.TABLE_NAME);
    }
}