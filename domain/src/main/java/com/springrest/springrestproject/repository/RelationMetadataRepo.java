package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.annotation.PersistenceCache;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.model.relation.RelationContext;
import com.springrest.springrestproject.model.relation.RelationMetadata;
import com.springrest.springrestproject.model.relation.RelationType;
import com.springrest.springrestproject.service.implementations.redis.RelationCacheService;
import com.springrest.springrestproject.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static jooq.generated.Tables.*;

@Repository
@RequiredArgsConstructor
public class RelationMetadataRepo {
    private final DSLContext dsl;
    private final RelationCacheService relationCacheService;

    @Transactional
    public RelationMetadata save(RelationMetadata metadata) {
        var relCtx = metadata.relationContext();

        if (metadata.id() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(SYS_RELATION_METADATA)
                            .set(SYS_RELATION_METADATA.RELATION_TYPE, metadata.relationType() != null ? metadata.relationType().name() : null)
                            .set(SYS_RELATION_METADATA.SOURCE_TABLE, metadata.sourceTable())
                            .set(SYS_RELATION_METADATA.SOURCE_COLUMN, metadata.sourceColumn())
                            .set(SYS_RELATION_METADATA.TARGET_TABLE, metadata.targetTable())
                            .set(SYS_RELATION_METADATA.TARGET_COLUMN, metadata.targetColumn())
                            .set(SYS_RELATION_METADATA.JUNCTION_TABLE, metadata.junctionTable())
                            .set(SYS_RELATION_METADATA.SOURCE_DELETE_POLICY, metadata.sourceDeletePolicy() != null ? metadata.sourceDeletePolicy().name() : null)
                            .set(SYS_RELATION_METADATA.TARGET_DELETE_POLICY, metadata.targetDeletePolicy() != null ? metadata.targetDeletePolicy().name() : null)
                            .set(SYS_RELATION_METADATA.CREATOR_ID, relCtx != null ? relCtx.creatorId() : null)
                            .set(SYS_RELATION_METADATA.CREATED_DATE, relCtx != null ? relCtx.createdDate() : LocalDateTime.now())
                            .set(SYS_RELATION_METADATA.LAST_UPDATER_ID, relCtx != null ? relCtx.lastUpdaterId() : null)
                            .set(SYS_RELATION_METADATA.LAST_CHANGED_DATE, relCtx != null ? relCtx.lastChangedDate() : LocalDateTime.now())
                            .returning(SYS_RELATION_METADATA.ID)
                            .fetchOne())
                    .getValue(SYS_RELATION_METADATA.ID);

            RelationMetadata savedMetadata = RelationMetadata.builder()
                    .id(generatedId)
                    .relationType(metadata.relationType())
                    .sourceTable(metadata.sourceTable())
                    .sourceColumn(metadata.sourceColumn())
                    .targetTable(metadata.targetTable())
                    .targetColumn(metadata.targetColumn())
                    .junctionTable(metadata.junctionTable())
                    .sourceDeletePolicy(metadata.sourceDeletePolicy())
                    .targetDeletePolicy(metadata.targetDeletePolicy())
                    .relationContext(metadata.relationContext())
                    .build();
            logRelationMetadataMutation(savedMetadata, "POST");
            
            relationCacheService.evict(savedMetadata.sourceTable());
            relationCacheService.evict(savedMetadata.targetTable());
            if (savedMetadata.junctionTable() != null) {
                relationCacheService.evict(savedMetadata.junctionTable());
            }
            return savedMetadata;
        } else {
            dsl.update(SYS_RELATION_METADATA)
                    .set(SYS_RELATION_METADATA.RELATION_TYPE, metadata.relationType() != null ? metadata.relationType().name() : null)
                    .set(SYS_RELATION_METADATA.SOURCE_TABLE, metadata.sourceTable())
                    .set(SYS_RELATION_METADATA.SOURCE_COLUMN, metadata.sourceColumn())
                    .set(SYS_RELATION_METADATA.TARGET_TABLE, metadata.targetTable())
                    .set(SYS_RELATION_METADATA.TARGET_COLUMN, metadata.targetColumn())
                    .set(SYS_RELATION_METADATA.JUNCTION_TABLE, metadata.junctionTable())
                    .set(SYS_RELATION_METADATA.SOURCE_DELETE_POLICY, metadata.sourceDeletePolicy() != null ? metadata.sourceDeletePolicy().name() : null)
                    .set(SYS_RELATION_METADATA.TARGET_DELETE_POLICY, metadata.targetDeletePolicy() != null ? metadata.targetDeletePolicy().name() : null)
                    .set(SYS_RELATION_METADATA.LAST_UPDATER_ID, relCtx != null ? relCtx.lastUpdaterId() : null)
                    .set(SYS_RELATION_METADATA.LAST_CHANGED_DATE, relCtx != null ? relCtx.lastChangedDate() : LocalDateTime.now())
                    .where(SYS_RELATION_METADATA.ID.eq(metadata.id()))
                    .execute();
            logRelationMetadataMutation(metadata, "PUT");
            
            relationCacheService.evict(metadata.sourceTable());
            relationCacheService.evict(metadata.targetTable());
            if (metadata.junctionTable() != null) {
                relationCacheService.evict(metadata.junctionTable());
            }
            return metadata;
        }
    }

    @Transactional
    public void delete(RelationMetadata metadata) {
        logRelationMetadataMutation(metadata, "DELETE");

        dsl.deleteFrom(SYS_RELATION_METADATA)
                .where(SYS_RELATION_METADATA.ID.eq(metadata.id()))
                .execute();

        relationCacheService.evict(metadata.sourceTable());
        relationCacheService.evict(metadata.targetTable());
        if (metadata.junctionTable() != null) {
            relationCacheService.evict(metadata.junctionTable());
        }
    }

    private void logRelationMetadataMutation(RelationMetadata metadata, String operation) {
        Long executorId = SecurityUtils.getCurrentUserId();
        if (executorId == null) {
            executorId = 0L;
        }
        var relCtx = metadata.relationContext();
        dsl.insertInto(SYS_RELATION_METADATA_LOG)
                .set(SYS_RELATION_METADATA_LOG.ID, metadata.id())
                .set(SYS_RELATION_METADATA_LOG.RELATION_TYPE, metadata.relationType() != null ? metadata.relationType().name() : null)
                .set(SYS_RELATION_METADATA_LOG.SOURCE_TABLE, metadata.sourceTable())
                .set(SYS_RELATION_METADATA_LOG.SOURCE_COLUMN, metadata.sourceColumn())
                .set(SYS_RELATION_METADATA_LOG.TARGET_TABLE, metadata.targetTable())
                .set(SYS_RELATION_METADATA_LOG.TARGET_COLUMN, metadata.targetColumn())
                .set(SYS_RELATION_METADATA_LOG.JUNCTION_TABLE, metadata.junctionTable())
                .set(SYS_RELATION_METADATA_LOG.SOURCE_DELETE_POLICY, metadata.sourceDeletePolicy() != null ? metadata.sourceDeletePolicy().name() : null)
                .set(SYS_RELATION_METADATA_LOG.TARGET_DELETE_POLICY, metadata.targetDeletePolicy() != null ? metadata.targetDeletePolicy().name() : null)
                .set(SYS_RELATION_METADATA_LOG.CREATOR_ID, relCtx != null ? relCtx.creatorId() : null)
                .set(SYS_RELATION_METADATA_LOG.CREATED_DATE, relCtx != null ? relCtx.createdDate() : LocalDateTime.now())
                .set(SYS_RELATION_METADATA_LOG.LAST_UPDATER_ID, relCtx != null ? relCtx.lastUpdaterId() : null)
                .set(SYS_RELATION_METADATA_LOG.LAST_CHANGED_DATE, relCtx != null ? relCtx.lastChangedDate() : LocalDateTime.now())
                .set(SYS_RELATION_METADATA_LOG.OPERATION_TYPE, operation)
                .set(SYS_RELATION_METADATA_LOG.EXECUTED_AT, LocalDateTime.now())
                .set(SYS_RELATION_METADATA_LOG.USER_ID, executorId)
                .execute();
    }

    public List<RelationMetadata> findAll() {
        return dsl.selectFrom(SYS_RELATION_METADATA)
                .fetch(this::mapRecordToRelationMetadata);
    }


    @PersistenceCache(cacheName = "relations", tableName = "#tableName")
    public List<RelationMetadata> findByTableName(String tableName) {
        return dsl.selectFrom(SYS_RELATION_METADATA)
                .where(SYS_RELATION_METADATA.SOURCE_TABLE.equalIgnoreCase(tableName)
                        .or(SYS_RELATION_METADATA.TARGET_TABLE.equalIgnoreCase(tableName))
                        .or(SYS_RELATION_METADATA.JUNCTION_TABLE.equalIgnoreCase(tableName)))
                .fetch(this::mapRecordToRelationMetadata);
    }

    private RelationMetadata mapRecordToRelationMetadata(Record record) {
        String relTypeStr = record.get(SYS_RELATION_METADATA.RELATION_TYPE);
        String srcDelPolicyStr = record.get(SYS_RELATION_METADATA.SOURCE_DELETE_POLICY);
        String trgDelPolicyStr = record.get(SYS_RELATION_METADATA.TARGET_DELETE_POLICY);

        RelationContext ctx = null;
        if (record.get(SYS_RELATION_METADATA.CREATOR_ID) != null || record.get(SYS_RELATION_METADATA.CREATED_DATE) != null) {
            ctx = new RelationContext(
                record.get(SYS_RELATION_METADATA.CREATOR_ID),
                record.get(SYS_RELATION_METADATA.CREATED_DATE),
                record.get(SYS_RELATION_METADATA.LAST_UPDATER_ID),
                record.get(SYS_RELATION_METADATA.LAST_CHANGED_DATE)
            );
        }

        return RelationMetadata.builder()
                .id(record.get(SYS_RELATION_METADATA.ID))
                .relationType(relTypeStr != null ? RelationType.valueOf(relTypeStr) : null)
                .sourceTable(record.get(SYS_RELATION_METADATA.SOURCE_TABLE))
                .sourceColumn(record.get(SYS_RELATION_METADATA.SOURCE_COLUMN))
                .targetTable(record.get(SYS_RELATION_METADATA.TARGET_TABLE))
                .targetColumn(record.get(SYS_RELATION_METADATA.TARGET_COLUMN))
                .junctionTable(record.get(SYS_RELATION_METADATA.JUNCTION_TABLE))
                .sourceDeletePolicy(srcDelPolicyStr != null ? DeletePolicy.valueOf(srcDelPolicyStr) : null)
                .targetDeletePolicy(trgDelPolicyStr != null ? DeletePolicy.valueOf(trgDelPolicyStr) : null)
                .relationContext(ctx)
                .build();
    }
}
