package com.springrest.springrestproject.dto.request.relation;

import com.springrest.springrestproject.model.relation.DeletePolicy;
import jakarta.validation.constraints.NotBlank;

public record DirectRelationRequest(
        @NotBlank(message = "Table name cannot be empty") String tableName,
        @NotBlank(message = "Column name cannot be empty") String columnName,
        @NotBlank(message = "Related table cannot be empty") String relatedTable,
        String relatedColumn,
        DeletePolicy deletePolicy
) {}
