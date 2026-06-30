package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyInsertRequest;
import com.springrest.springrestproject.dto.response.relation.RelationResponse;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.model.relation.RelationType;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import com.springrest.springrestproject.service.interfaces.IRelationService;
import com.springrest.springrestproject.validators.ColumnRelationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RelationService implements IRelationService {

    private final TableMetadataRepo tableMetadataRepo;
    private final JdbcTemplate jdbcTemplate;
    private final ColumnRelationValidator columnRelationValidator;

    @Override
    @Transactional
    public RelationResponse createOneToOneRelation(DirectRelationRequest request, Long userId) {
        return createDirectRelation(request, RelationType.ONE_TO_ONE);
    }

    @Override
    @Transactional
    public RelationResponse createManyToOneRelation(DirectRelationRequest request, Long userId) {
        return createDirectRelation(request, RelationType.MANY_TO_ONE);
    }

    private RelationResponse createDirectRelation(DirectRelationRequest request, RelationType relationType) {
        TableMetadata sourceTable = tableMetadataRepo.findByTableName(request.tableName())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        ColumnMetadata col = new ColumnMetadata();
        col.setColumnName(request.columnName());
        col.setDataType("BIGINT");
        col.setRelationType(relationType);
        col.setRelatedTable(request.relatedTable());
        col.setRelatedColumn(request.relatedColumn());
        col.setDeletePolicy(request.deletePolicy());

        columnRelationValidator.validate(List.of(col));

        String targetColName = request.relatedColumn() != null ? request.relatedColumn() : "id";
        DeletePolicy policy = request.deletePolicy() != null ? request.deletePolicy() : DeletePolicy.CASCADE;

        String uniqueString = relationType == RelationType.ONE_TO_ONE ? " UNIQUE" : "";
        String addColSql = String.format("ALTER TABLE %s ADD COLUMN %s BIGINT%s", request.tableName(), request.columnName(), uniqueString);
        jdbcTemplate.execute(addColSql);

        String addConstraintSql = String.format("ALTER TABLE %s ADD CONSTRAINT fk_%s_%s FOREIGN KEY (%s) REFERENCES %s(%s) %s",
                request.tableName(), request.tableName(), request.columnName(), request.columnName(), request.relatedTable(), targetColName, policy.getSql());
        jdbcTemplate.execute(addConstraintSql);

        if (Boolean.TRUE.equals(sourceTable.getIsAuditEnabled())) {
            String addLogColSql = String.format("ALTER TABLE %s_log ADD COLUMN %s BIGINT", request.tableName(), request.columnName());
            jdbcTemplate.execute(addLogColSql);
        }

        sourceTable.getColumns().add(col);
        tableMetadataRepo.save(sourceTable);

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

        TableMetadata junctionMeta = new TableMetadata();
        junctionMeta.setTableName(junctionTableName);
        junctionMeta.setIsAuditEnabled(false);

        ColumnMetadata c1 = new ColumnMetadata();
        c1.setColumnName(col1Name);
        c1.setDataType("BIGINT");
        c1.setRelationType(RelationType.MANY_TO_ONE);
        c1.setRelatedTable(fk1Table);
        c1.setRelatedColumn("id");
        c1.setDeletePolicy(policy1);

        ColumnMetadata c2 = new ColumnMetadata();
        c2.setColumnName(col2Name);
        c2.setDataType("BIGINT");
        c2.setRelationType(RelationType.MANY_TO_ONE);
        c2.setRelatedTable(fk2Table);
        c2.setRelatedColumn("id");
        c2.setDeletePolicy(policy2);

        junctionMeta.setColumns(java.util.List.of(c1, c2));
        tableMetadataRepo.save(junctionMeta);

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
        if (junctionTable.getColumns() == null || junctionTable.getColumns().size() < 2) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "Table is not a valid junction table");
        }
        
        String col1 = junctionTable.getColumns().get(0).getColumnName();
        String col2 = junctionTable.getColumns().get(1).getColumnName();
        
        String sql = String.format("INSERT INTO %s (%s, %s) VALUES (?, ?)", junctionTable.getTableName(), col1, col2);
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
        if (junctionTable.getColumns() == null || junctionTable.getColumns().size() < 2) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "Table is not a valid junction table");
        }
        
        String col1 = junctionTable.getColumns().get(0).getColumnName();
        String col2 = junctionTable.getColumns().get(1).getColumnName();
        
        String sql = String.format("DELETE FROM %s WHERE %s = ? AND %s = ?", junctionTable.getTableName(), col1, col2);
        int rowsAffected = jdbcTemplate.update(sql, request.firstTableId(), request.secondTableId());
        
        if (rowsAffected == 0) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
