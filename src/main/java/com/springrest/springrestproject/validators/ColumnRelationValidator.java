package com.springrest.springrestproject.validators;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.model.column.ColumnMetadata;
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

    public void validate(List<ColumnMetadata> columns) {
        for (ColumnMetadata col : columns) {
            if (col.getRelationType() != null) {
                validateRelation(col);
            }
        }
    }

    private void validateRelation(ColumnMetadata col) {
        if (col.getRelatedTable() == null || col.getRelatedTable().isBlank()) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST);
        }

        TableMetadata relatedTable = resolveRelatedTable(col);
        validateReferencedColumn(col, relatedTable);
    }

    private TableMetadata resolveRelatedTable(ColumnMetadata col) {
        return tableMetadataRepo.findByTableName(col.getRelatedTable())
                .orElseThrow(() -> {
                    String reason = String.format("referenced table does not exist: %s", col.getRelatedTable());
                    return new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            List.of(new FieldValidationError(col.getColumnName(), reason)),
                            reason
                    );
                });
    }

    private void validateReferencedColumn(ColumnMetadata col, TableMetadata relatedTable) {
        String targetColName = col.getRelatedColumn() != null ? col.getRelatedColumn() : "id";
        if ("id".equalsIgnoreCase(targetColName)) return;

        ColumnMetadata referencedCol = relatedTable.getColumns().stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(targetColName))
                .findFirst()
                .orElseThrow(() -> {
                    String reason = String.format("referenced column does not exist: %s in table %s", targetColName, col.getRelatedTable());
                    return new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            List.of(new FieldValidationError(col.getColumnName(), reason)),
                            reason
                    );
                });

        if (!isUnique(referencedCol)) {
            String reason = "cannot create relation to a non unique column";
            throw new ApplicationException(
                    ErrorCode.BAD_REQUEST,
                    List.of(new FieldValidationError(col.getColumnName(), reason)),
                    reason
            );
        }
    }

    private boolean isUnique(ColumnMetadata col) {
        return col.getColumnContext() != null
                && col.getColumnContext().getIsUnique() != null
                && col.getColumnContext().getIsUnique();
    }
}
