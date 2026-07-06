package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyInsertRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.response.relation.RelationResponse;
import com.springrest.springrestproject.dto.response.relation.ResolvedRelation;
import com.springrest.springrestproject.model.column.ColumnContext;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.relation.*;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.RelationMetadataRepo;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.validators.ColumnRelationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelationService implements IRelationService {

    private final TableMetadataRepo tableMetadataRepo;
    private final RelationMetadataRepo relationMetadataRepo;
    private final JdbcTemplate jdbcTemplate;
    private final ColumnRelationValidator columnRelationValidator;

    @Override
    @Transactional
    public RelationResponse createOneToOneRelation(DirectRelationRequest request, Long userId) {
        return createDirectRelation(request, RelationType.ONE_TO_ONE, userId);
    }

    @Override
    @Transactional
    public RelationResponse createManyToOneRelation(DirectRelationRequest request, Long userId) {
        return createDirectRelation(request, RelationType.MANY_TO_ONE, userId);
    }

    private RelationResponse createDirectRelation(DirectRelationRequest request, RelationType relationType, Long userId) {
        TableMetadata sourceTable = tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        String targetColName = request.relatedColumn() != null ? request.relatedColumn() : "id";
        DeletePolicy policy = request.deletePolicy() != null ? request.deletePolicy() : DeletePolicy.CASCADE;

        RelationContext relCtx = new RelationContext(userId, LocalDateTime.now(), userId, LocalDateTime.now());
        RelationMetadata rel = RelationMetadata.builder()
                .relationType(relationType)
                .sourceTable(request.tableName())
                .sourceColumn(request.columnName())
                .targetTable(request.relatedTable())
                .targetColumn(targetColName)
                .sourceDeletePolicy(policy)
                .relationContext(relCtx)
                .build();

        columnRelationValidator.validate(rel);

        String uniqueString = relationType == RelationType.ONE_TO_ONE ? " UNIQUE" : "";
        String addColSql = String.format("ALTER TABLE %s ADD COLUMN %s BIGINT%s", request.tableName(), request.columnName(), uniqueString);
        jdbcTemplate.execute(addColSql);

        String addConstraintSql = String.format("ALTER TABLE %s ADD CONSTRAINT fk_%s_%s FOREIGN KEY (%s) REFERENCES %s(%s) %s",
                request.tableName(), request.tableName(), request.columnName(), request.columnName(), request.relatedTable(), targetColName, policy.getSql());
        jdbcTemplate.execute(addConstraintSql);

        if (Boolean.TRUE.equals(sourceTable.isAuditEnabled())) {
            String addLogColSql = String.format("ALTER TABLE %s_log ADD COLUMN %s BIGINT", request.tableName(), request.columnName());
            jdbcTemplate.execute(addLogColSql);
        }

        ColumnContext context = ColumnContext.builder()
                .creatorId(userId)
                .createdDate(LocalDateTime.now())
                .lastUpdaterId(userId)
                .lastChangedDate(LocalDateTime.now())
                .build();

        ColumnMetadata col = ColumnMetadata.builder()
                .columnName(request.columnName())
                .dataType("BIGINT")
                .columnContext(context)
                .tableName(request.tableName())
                .build();

        List<ColumnMetadata> updatedCols = new ArrayList<>(sourceTable.columns());
        updatedCols.add(col);
        TableMetadata updatedSourceTable = TableMetadata.builder()
                .id(sourceTable.id())
                .tableName(sourceTable.tableName())
                .columns(updatedCols)
                .tableContext(sourceTable.tableContext())
                .isAuditEnabled(sourceTable.isAuditEnabled())
                .build();
        tableMetadataRepo.save(updatedSourceTable);
        
        relationMetadataRepo.save(rel);

        return new RelationResponse(
                request.tableName(),
                request.columnName(),
                request.relatedTable(),
                targetColName,
                relationType,
                policy,
                null
        );
    }

    @Override
    @Transactional
    public RelationResponse createManyToManyRelation(ManyToManyRelationRequest request, Long userId) {
        tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        tableMetadataRepo.findByTableName(request.relatedTable())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        String t1 = request.tableName().toLowerCase();
        String t2 = request.relatedTable().toLowerCase();
        
        DeletePolicy sourcePolicy = request.sourceDeletePolicy() != null ? request.sourceDeletePolicy() : DeletePolicy.CASCADE;
        DeletePolicy targetPolicy = request.targetDeletePolicy() != null ? request.targetDeletePolicy() : DeletePolicy.CASCADE;

        String junctionTableName;
        String col1Name;
        String col2Name;
        String fk1Table;
        String fk2Table;
        DeletePolicy policy1;
        DeletePolicy policy2;

        if (t1.compareTo(t2) <= 0) {
            junctionTableName = t1 + "_" + t2;
            col1Name = t1 + "_id";
            col2Name = t2 + "_id";
            fk1Table = t1;
            fk2Table = t2;
            policy1 = sourcePolicy;
            policy2 = targetPolicy;
        } else {
            junctionTableName = t2 + "_" + t1;
            col1Name = t2 + "_id";
            col2Name = t1 + "_id";
            fk1Table = t2;
            fk2Table = t1;
            policy1 = targetPolicy;
            policy2 = sourcePolicy;
        }

        if (tableMetadataRepo.findByTableName(junctionTableName).isPresent()) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "Junction table already exists: " + junctionTableName);
        }

        String createTableSql = String.format(
                "CREATE TABLE IF NOT EXISTS %s (" +
                "%s BIGINT, " +
                "%s BIGINT, " +
                "PRIMARY KEY (%s, %s), " +
                "CONSTRAINT fk_%s_%s FOREIGN KEY (%s) REFERENCES %s(id) %s, " +
                "CONSTRAINT fk_%s_%s FOREIGN KEY (%s) REFERENCES %s(id) %s" +
                ");",
                junctionTableName,
                col1Name, col2Name,
                col1Name, col2Name,
                junctionTableName, fk1Table, col1Name, fk1Table, policy1.getSql(),
                junctionTableName, fk2Table, col2Name, fk2Table, policy2.getSql()
        );
        jdbcTemplate.execute(createTableSql);

        ColumnContext ctx1 = ColumnContext.builder()
                .creatorId(userId)
                .createdDate(LocalDateTime.now())
                .lastUpdaterId(userId)
                .lastChangedDate(LocalDateTime.now())
                .build();

        ColumnMetadata c1 = ColumnMetadata.builder()
                .columnName(col1Name)
                .dataType("BIGINT")
                .columnContext(ctx1)
                .tableName(junctionTableName)
                .build();

        ColumnContext ctx2 = ColumnContext.builder()
                .creatorId(userId)
                .createdDate(LocalDateTime.now())
                .lastUpdaterId(userId)
                .lastChangedDate(LocalDateTime.now())
                .build();

        ColumnMetadata c2 = ColumnMetadata.builder()
                .columnName(col2Name)
                .dataType("BIGINT")
                .columnContext(ctx2)
                .tableName(junctionTableName)
                .build();

        TableMetadata junctionMeta = TableMetadata.builder()
                .tableName(junctionTableName)
                .isAuditEnabled(false)
                .columns(List.of(c1, c2))
                .build();
        tableMetadataRepo.save(junctionMeta);

        RelationContext relCtx = new RelationContext(userId, LocalDateTime.now(), userId, LocalDateTime.now());
        RelationMetadata rel = RelationMetadata.builder()
                .relationType(RelationType.MANY_TO_MANY)
                .sourceTable(request.tableName())
                .targetTable(request.relatedTable())
                .junctionTable(junctionTableName)
                .sourceDeletePolicy(sourcePolicy)
                .targetDeletePolicy(targetPolicy)
                .relationContext(relCtx)
                .build();

        relationMetadataRepo.save(rel);

        return new RelationResponse(
                request.tableName(),
                null,
                request.relatedTable(),
                null,
                RelationType.MANY_TO_MANY,
                sourcePolicy,
                targetPolicy
        );
    }

    @Override
    @Transactional
    public ManyToManyInsertRequest insertManyToManyDataById(Long relationId, ManyToManyInsertRequest request) {
        TableMetadata junctionTable = tableMetadataRepo.findById(relationId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return insertIntoJunctionTable(junctionTable, request);
    }

    @Override
    @Transactional
    public ManyToManyInsertRequest insertManyToManyDataByName(String tableName, ManyToManyInsertRequest request) {
        TableMetadata junctionTable = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return insertIntoJunctionTable(junctionTable, request);
    }

    private ManyToManyInsertRequest insertIntoJunctionTable(TableMetadata junctionTable, ManyToManyInsertRequest request) {
        if (junctionTable.columns() == null || junctionTable.columns().size() < 2) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "Table is not a valid junction table");
        }
        
        String col1 = junctionTable.columns().get(0).columnName();
        String col2 = junctionTable.columns().get(1).columnName();
        
        String sql = String.format("INSERT INTO %s (%s, %s) VALUES (?, ?)", junctionTable.tableName(), col1, col2);
        jdbcTemplate.update(sql, request.firstTableId(), request.secondTableId());
        
        return request;
    }

    @Override
    @Transactional
    public void deleteManyToManyDataById(Long relationId, ManyToManyInsertRequest request) {
        TableMetadata junctionTable = tableMetadataRepo.findById(relationId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        deleteFromJunctionTable(junctionTable, request);
    }

    @Override
    @Transactional
    public void deleteManyToManyDataByName(String tableName, ManyToManyInsertRequest request) {
        TableMetadata junctionTable = tableMetadataRepo.findByTableName(tableName)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        deleteFromJunctionTable(junctionTable, request);
    }

    private void deleteFromJunctionTable(TableMetadata junctionTable, ManyToManyInsertRequest request) {
        if (junctionTable.columns() == null || junctionTable.columns().size() < 2) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "Table is not a valid junction table");
        }
        
        String col1 = junctionTable.columns().get(0).columnName();
        String col2 = junctionTable.columns().get(1).columnName();
        
        String sql = String.format("DELETE FROM %s WHERE %s = ? AND %s = ?", junctionTable.tableName(), col1, col2);
        int rowsAffected = jdbcTemplate.update(sql, request.firstTableId(), request.secondTableId());
        
        if (rowsAffected == 0) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public List<RelationResponse> getAllRelations() {
        List<RelationMetadata> relations = relationMetadataRepo.findAll();
        return relations.stream()
                .map(rel -> {
                    if (rel.relationType() == RelationType.MANY_TO_MANY) {
                        return new RelationResponse(
                                rel.sourceTable(),
                                null,
                                rel.targetTable(),
                                null,
                                RelationType.MANY_TO_MANY,
                                rel.sourceDeletePolicy(),
                                rel.targetDeletePolicy()
                        );
                    } else {
                        return new RelationResponse(
                                rel.sourceTable(),
                                rel.sourceColumn(),
                                rel.targetTable(),
                                rel.targetColumn(),
                                rel.relationType(),
                                rel.sourceDeletePolicy(),
                                null
                        );
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedRelation> getRelationsForTable(String tableName) {
        List<ResolvedRelation> relations = new ArrayList<>();
        List<RelationMetadata> allRelations = relationMetadataRepo.findAll();

        for (RelationMetadata rel : allRelations) {
            if (rel.relationType() == RelationType.MANY_TO_MANY) {
                if (rel.sourceTable().equalsIgnoreCase(tableName)) {
                    String targetTable = rel.targetTable();
                    String junctionTable = rel.junctionTable();
                    String baseColInJunction = rel.sourceTable().toLowerCase() + "_id";
                    String targetColInJunction = rel.targetTable().toLowerCase() + "_id";
                    
                    relations.add(new ResolvedRelation(targetTable, targetTable, RelationJoinType.M2M, null, null, junctionTable, baseColInJunction, targetColInJunction));
                    relations.add(new ResolvedRelation(targetTable + "_via_" + junctionTable, targetTable, RelationJoinType.M2M, null, null, junctionTable, baseColInJunction, targetColInJunction));
                } else if (rel.targetTable().equalsIgnoreCase(tableName)) {
                    String targetTable = rel.sourceTable();
                    String junctionTable = rel.junctionTable();
                    String baseColInJunction = rel.targetTable().toLowerCase() + "_id";
                    String targetColInJunction = rel.sourceTable().toLowerCase() + "_id";
                    
                    relations.add(new ResolvedRelation(targetTable, targetTable, RelationJoinType.M2M, null, null, junctionTable, baseColInJunction, targetColInJunction));
                    relations.add(new ResolvedRelation(targetTable + "_via_" + junctionTable, targetTable, RelationJoinType.M2M, null, null, junctionTable, baseColInJunction, targetColInJunction));
                }
            } else {
                if (rel.sourceTable().equalsIgnoreCase(tableName)) {
                    String targetTable = rel.targetTable();
                    relations.add(new ResolvedRelation(targetTable, targetTable, RelationJoinType.FORWARD, rel.sourceColumn(), rel.targetColumn(), null, null, null));
                    relations.add(new ResolvedRelation(targetTable + "_via_" + rel.sourceColumn(), targetTable, RelationJoinType.FORWARD, rel.sourceColumn(), rel.targetColumn(), null, null, null));
                } else if (rel.targetTable().equalsIgnoreCase(tableName)) {
                    String sourceTable = rel.sourceTable();
                    relations.add(new ResolvedRelation(sourceTable, sourceTable, RelationJoinType.REVERSE, null, rel.sourceColumn(), null, null, null));
                    relations.add(new ResolvedRelation(sourceTable + "_via_" + rel.sourceColumn(), sourceTable, RelationJoinType.REVERSE, null, rel.sourceColumn(), null, null, null));
                }
            }
        }
        return relations;
    }
}
