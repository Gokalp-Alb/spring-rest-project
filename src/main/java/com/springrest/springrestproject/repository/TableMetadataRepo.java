package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.ColumnMetadata;
import com.springrest.springrestproject.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.COLUMN_METADATA;
import static jooq.generated.Tables.TABLE_METADATA;

@Repository
@RequiredArgsConstructor
public class TableMetadataRepo {
    private final DSLContext dsl;

    @Transactional
    public TableMetadata save(TableMetadata metadata) {
        var tableCtx = metadata.getTableContext();

        if (metadata.getId() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(TABLE_METADATA)
                            .set(TABLE_METADATA.TABLE_NAME, metadata.getTableName())
                            .set(TABLE_METADATA.CREATOR_ID, tableCtx != null ? tableCtx.getCreatorId() : null)
                            .set(TABLE_METADATA.CREATED_DATE, tableCtx != null ? tableCtx.getCreatedDate() : java.time.LocalDateTime.now())
                            .set(TABLE_METADATA.LAST_UPDATER_ID, tableCtx != null ? tableCtx.getLastUpdaterId() : null)
                            .set(TABLE_METADATA.LAST_CHANGED_DATE, tableCtx != null ? tableCtx.getLastChangedDate() : java.time.LocalDateTime.now())
                            .returning(TABLE_METADATA.ID)
                            .fetchOne())
                    .getValue(TABLE_METADATA.ID);

            metadata.setId(generatedId);
        } else {
            dsl.update(TABLE_METADATA)
                    .set(TABLE_METADATA.TABLE_NAME, metadata.getTableName())
                    .set(TABLE_METADATA.LAST_UPDATER_ID, tableCtx != null ? tableCtx.getLastUpdaterId() : null)
                    .set(TABLE_METADATA.LAST_CHANGED_DATE, tableCtx != null ? tableCtx.getLastChangedDate() : java.time.LocalDateTime.now())
                    .where(TABLE_METADATA.ID.eq(metadata.getId()))
                    .execute();

            dsl.deleteFrom(COLUMN_METADATA)
                    .where(COLUMN_METADATA.TABLE_ID.eq(metadata.getId()))
                    .execute();
        }

        if (metadata.getColumns() != null && !metadata.getColumns().isEmpty()) {
            var batchQueries = dsl.batch(
                    metadata.getColumns().stream().map(col -> {
                        var colCtx = col.getColumnContext();
                        return (org.jooq.Query) dsl.insertInto(COLUMN_METADATA)
                                .set(COLUMN_METADATA.TABLE_ID, metadata.getId())
                                .set(COLUMN_METADATA.COLUMN_NAME, col.getColumnName())
                                .set(COLUMN_METADATA.DATA_TYPE, col.getDataType())
                                .set(COLUMN_METADATA.CREATOR_ID, colCtx != null ? colCtx.getCreatorId() : null)
                                .set(COLUMN_METADATA.CREATED_DATE, colCtx != null ? colCtx.getCreatedDate() : java.time.LocalDateTime.now())
                                .set(COLUMN_METADATA.LAST_UPDATER_ID, colCtx != null ? colCtx.getLastUpdaterId() : null)
                                .set(COLUMN_METADATA.LAST_CHANGED_DATE, colCtx != null ? colCtx.getLastChangedDate() : java.time.LocalDateTime.now())
                                .set(COLUMN_METADATA.IS_SENSITIVE, colCtx != null ? colCtx.getIsSensitive() : false)
                                .set(COLUMN_METADATA.IS_UNIQUE, colCtx != null ? colCtx.getIsUnique() : false)
                                .set(COLUMN_METADATA.VALIDATION_REGEX, colCtx != null ? colCtx.getValidationRegex() : null);
                    }).toList()
            );

            batchQueries.execute();
        }
        return metadata;
    }

    @Transactional
    public void delete(TableMetadata metadata) {
        dsl.deleteFrom(COLUMN_METADATA)
                .where(COLUMN_METADATA.TABLE_ID.eq(metadata.getId()))
                .execute();

        dsl.deleteFrom(TABLE_METADATA)
                .where(TABLE_METADATA.ID.eq(metadata.getId()))
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
        }

        return new PageImpl<>(tables, pageable, total);
    }

    private List<ColumnMetadata> fetchColumnsForTable(Long tableId) {
        return dsl.selectFrom(COLUMN_METADATA)
                .where(COLUMN_METADATA.TABLE_ID.eq(tableId))
                .fetch(record -> {
                    ColumnMetadata col = new ColumnMetadata();
                    col.setColumnName(record.get(COLUMN_METADATA.COLUMN_NAME));
                    col.setDataType(record.get(COLUMN_METADATA.DATA_TYPE));

                    var ctx = new com.springrest.springrestproject.model.ColumnContext();
                    ctx.setCreatorId(record.get(COLUMN_METADATA.CREATOR_ID));
                    ctx.setCreatedDate(record.get(COLUMN_METADATA.CREATED_DATE));
                    ctx.setLastUpdaterId(record.get(COLUMN_METADATA.LAST_UPDATER_ID));
                    ctx.setLastChangedDate(record.get(COLUMN_METADATA.LAST_CHANGED_DATE));
                    ctx.setIsSensitive(record.get(COLUMN_METADATA.IS_SENSITIVE));
                    ctx.setIsUnique(record.get(COLUMN_METADATA.IS_UNIQUE));
                    ctx.setValidationRegex(record.get(COLUMN_METADATA.VALIDATION_REGEX));

                    col.setColumnContext(ctx);
                    return col;
                });
    }

    public Optional<TableMetadata> findById(Long tableId) {
        TableMetadata metadata = dsl.selectFrom(TABLE_METADATA)
                .where(TABLE_METADATA.ID.eq(tableId))
                .fetchOneInto(TableMetadata.class);
        if (metadata != null) {
            metadata.setColumns(fetchColumnsForTable(tableId));
        }

        return Optional.ofNullable(metadata);
    }

    public Optional<TableMetadata> findByTableName(String tableName) {
        TableMetadata metadata = dsl.selectFrom(TABLE_METADATA)
                .where(TABLE_METADATA.TABLE_NAME.eq(tableName))
                .fetchOneInto(TableMetadata.class);
        if (metadata != null) {
            metadata.setColumns(fetchColumnsForTable(metadata.getId()));
        }

        return Optional.ofNullable(metadata);
    }
}