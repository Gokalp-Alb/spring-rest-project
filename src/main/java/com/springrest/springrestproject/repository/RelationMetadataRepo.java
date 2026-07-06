package com.springrest.springrestproject.repository;

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
import java.util.stream.Collectors;

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
            Long generatedId = Objects.requireNonNull(dsl.insertInto(RELATION_METADATA)
                            .set(RELATION_METADATA.RELATION_TYPE, metadata.relationType() != null ? metadata.relationType().name() : null)
                            .set(RELATION_METADATA.SOURCE_TABLE, metadata.sourceTable())
                            .set(RELATION_METADATA.SOURCE_COLUMN, metadata.sourceColumn())
                            .set(RELATION_METADATA.TARGET_TABLE, metadata.targetTable())
                            .set(RELATION_METADATA.TARGET_COLUMN, metadata.targetColumn())
                            .set(RELATION_METADATA.JUNCTION_TABLE, metadata.junctionTable())
                            .set(RELATION_METADATA.SOURCE_DELETE_POLICY, metadata.sourceDeletePolicy() != null ? metadata.sourceDeletePolicy().name() : null)
                            .set(RELATION_METADATA.TARGET_DELETE_POLICY, metadata.targetDeletePolicy() != null ? metadata.targetDeletePolicy().name() : null)
                            .set(RELATION_METADATA.CREATOR_ID, relCtx != null ? relCtx.creatorId() : null)
                            .set(RELATION_METADATA.CREATED_DATE, relCtx != null ? relCtx.createdDate() : LocalDateTime.now())
                            .set(RELATION_METADATA.LAST_UPDATER_ID, relCtx != null ? relCtx.lastUpdaterId() : null)
                            .set(RELATION_METADATA.LAST_CHANGED_DATE, relCtx != null ? relCtx.lastChangedDate() : LocalDateTime.now())
                            .returning(RELATION_METADATA.ID)
                            .fetchOne())
                    .getValue(RELATION_METADATA.ID);

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
            dsl.update(RELATION_METADATA)
                    .set(RELATION_METADATA.RELATION_TYPE, metadata.relationType() != null ? metadata.relationType().name() : null)
                    .set(RELATION_METADATA.SOURCE_TABLE, metadata.sourceTable())
                    .set(RELATION_METADATA.SOURCE_COLUMN, metadata.sourceColumn())
                    .set(RELATION_METADATA.TARGET_TABLE, metadata.targetTable())
                    .set(RELATION_METADATA.TARGET_COLUMN, metadata.targetColumn())
                    .set(RELATION_METADATA.JUNCTION_TABLE, metadata.junctionTable())
                    .set(RELATION_METADATA.SOURCE_DELETE_POLICY, metadata.sourceDeletePolicy() != null ? metadata.sourceDeletePolicy().name() : null)
                    .set(RELATION_METADATA.TARGET_DELETE_POLICY, metadata.targetDeletePolicy() != null ? metadata.targetDeletePolicy().name() : null)
                    .set(RELATION_METADATA.LAST_UPDATER_ID, relCtx != null ? relCtx.lastUpdaterId() : null)
                    .set(RELATION_METADATA.LAST_CHANGED_DATE, relCtx != null ? relCtx.lastChangedDate() : LocalDateTime.now())
                    .where(RELATION_METADATA.ID.eq(metadata.id()))
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

        dsl.deleteFrom(RELATION_METADATA)
                .where(RELATION_METADATA.ID.eq(metadata.id()))
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
        dsl.insertInto(RELATION_METADATA_LOG)
                .set(RELATION_METADATA_LOG.ID, metadata.id())
                .set(RELATION_METADATA_LOG.RELATION_TYPE, metadata.relationType() != null ? metadata.relationType().name() : null)
                .set(RELATION_METADATA_LOG.SOURCE_TABLE, metadata.sourceTable())
                .set(RELATION_METADATA_LOG.SOURCE_COLUMN, metadata.sourceColumn())
                .set(RELATION_METADATA_LOG.TARGET_TABLE, metadata.targetTable())
                .set(RELATION_METADATA_LOG.TARGET_COLUMN, metadata.targetColumn())
                .set(RELATION_METADATA_LOG.JUNCTION_TABLE, metadata.junctionTable())
                .set(RELATION_METADATA_LOG.SOURCE_DELETE_POLICY, metadata.sourceDeletePolicy() != null ? metadata.sourceDeletePolicy().name() : null)
                .set(RELATION_METADATA_LOG.TARGET_DELETE_POLICY, metadata.targetDeletePolicy() != null ? metadata.targetDeletePolicy().name() : null)
                .set(RELATION_METADATA_LOG.CREATOR_ID, relCtx != null ? relCtx.creatorId() : null)
                .set(RELATION_METADATA_LOG.CREATED_DATE, relCtx != null ? relCtx.createdDate() : LocalDateTime.now())
                .set(RELATION_METADATA_LOG.LAST_UPDATER_ID, relCtx != null ? relCtx.lastUpdaterId() : null)
                .set(RELATION_METADATA_LOG.LAST_CHANGED_DATE, relCtx != null ? relCtx.lastChangedDate() : LocalDateTime.now())
                .set(RELATION_METADATA_LOG.OPERATION_TYPE, operation)
                .set(RELATION_METADATA_LOG.EXECUTED_AT, LocalDateTime.now())
                .set(RELATION_METADATA_LOG.USER_ID, executorId)
                .execute();
    }

    public List<RelationMetadata> findAll() {
        return dsl.selectFrom(RELATION_METADATA)
                .fetch(this::mapRecordToRelationMetadata);
    }

    public List<RelationMetadata> findByTableName(String tableName) {
        return relationCacheService.get(tableName)
                .orElseGet(() -> {
                    List<RelationMetadata> result = dsl.selectFrom(RELATION_METADATA)
                            .where(RELATION_METADATA.SOURCE_TABLE.equalIgnoreCase(tableName)
                                    .or(RELATION_METADATA.TARGET_TABLE.equalIgnoreCase(tableName))
                                    .or(RELATION_METADATA.JUNCTION_TABLE.equalIgnoreCase(tableName)))
                            .fetch(this::mapRecordToRelationMetadata);
                    relationCacheService.put(tableName, result);
                    return result;
                });
    }

    public List<RelationMetadata> findIncomingFKs(String tableName) {
        return findByTableName(tableName).stream()
                .filter(r -> r.targetTable().equalsIgnoreCase(tableName))
                .collect(Collectors.toList());
    }

    private RelationMetadata mapRecordToRelationMetadata(Record record) {
        String relTypeStr = record.get(RELATION_METADATA.RELATION_TYPE);
        String srcDelPolicyStr = record.get(RELATION_METADATA.SOURCE_DELETE_POLICY);
        String trgDelPolicyStr = record.get(RELATION_METADATA.TARGET_DELETE_POLICY);

        RelationContext ctx = null;
        if (record.get(RELATION_METADATA.CREATOR_ID) != null || record.get(RELATION_METADATA.CREATED_DATE) != null) {
            ctx = new RelationContext(
                record.get(RELATION_METADATA.CREATOR_ID),
                record.get(RELATION_METADATA.CREATED_DATE),
                record.get(RELATION_METADATA.LAST_UPDATER_ID),
                record.get(RELATION_METADATA.LAST_CHANGED_DATE)
            );
        }

        return RelationMetadata.builder()
                .id(record.get(RELATION_METADATA.ID))
                .relationType(relTypeStr != null ? RelationType.valueOf(relTypeStr) : null)
                .sourceTable(record.get(RELATION_METADATA.SOURCE_TABLE))
                .sourceColumn(record.get(RELATION_METADATA.SOURCE_COLUMN))
                .targetTable(record.get(RELATION_METADATA.TARGET_TABLE))
                .targetColumn(record.get(RELATION_METADATA.TARGET_COLUMN))
                .junctionTable(record.get(RELATION_METADATA.JUNCTION_TABLE))
                .sourceDeletePolicy(srcDelPolicyStr != null ? DeletePolicy.valueOf(srcDelPolicyStr) : null)
                .targetDeletePolicy(trgDelPolicyStr != null ? DeletePolicy.valueOf(trgDelPolicyStr) : null)
                .relationContext(ctx)
                .build();
    }
}
