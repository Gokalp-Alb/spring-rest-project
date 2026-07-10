package com.springrest.springrestproject.validators;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.model.relation.RelationMetadata;
import com.springrest.springrestproject.model.relation.RelationType;
import com.springrest.springrestproject.model.table.TableMetadata;
import com.springrest.springrestproject.repository.TableMetadataRepo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ColumnRelationValidator {

    private final TableMetadataRepo tableMetadataRepo;

    public ColumnRelationValidator(TableMetadataRepo tableMetadataRepo) {
        this.tableMetadataRepo = tableMetadataRepo;
    }

    public void validate(RelationMetadata relation) {
        if (relation.relationType() != null) {
            validateRelation(relation);
        }
    }

    private void validateRelation(RelationMetadata relation) {
        if (relation.targetTable() == null || relation.targetTable().isBlank()) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST);
        }

        TableMetadata relatedTable = resolveRelatedTable(relation);
        validateReferencedColumn(relation, relatedTable);
    }

    private TableMetadata resolveRelatedTable(RelationMetadata relation) {
        return tableMetadataRepo.findByTableName(relation.targetTable())
                .orElseThrow(() -> {
                    String reason = String.format("referenced table does not exist: %s", relation.targetTable());
                    return new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            List.of(new FieldValidationError(relation.sourceColumn(), reason)),
                            reason
                    );
                });
    }

    private void validateReferencedColumn(RelationMetadata relation, TableMetadata relatedTable) {
        String targetColName = relation.targetColumn() != null ? relation.targetColumn() : "id";
        if ("id".equalsIgnoreCase(targetColName)) return;

        ColumnMetadata referencedCol = relatedTable.columns().stream()
                .filter(c -> c.columnName().equalsIgnoreCase(targetColName))
                .findFirst()
                .orElseThrow(() -> {
                    String reason = String.format("referenced column does not exist: %s in table %s", targetColName, relation.targetTable());
                    return new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            List.of(new FieldValidationError(relation.sourceColumn(), reason)),
                            reason
                    );
                });

        if (relation.relationType() == RelationType.ONE_TO_ONE && !isUnique(referencedCol)) {
            String reason = "cannot create relation to a non unique column";
            throw new ApplicationException(
                    ErrorCode.BAD_REQUEST,
                    List.of(new FieldValidationError(relation.sourceColumn(), reason)),
                    reason
            );
        }
    }

    private boolean isUnique(ColumnMetadata col) {
        return col.columnContext() != null
                && col.columnContext().isUnique() != null
                && col.columnContext().isUnique();
    }
}
