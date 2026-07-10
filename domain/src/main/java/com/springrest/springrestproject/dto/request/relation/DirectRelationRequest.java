package com.springrest.springrestproject.dto.request.relation;

import com.springrest.springrestproject.model.relation.DeletePolicy;
import jakarta.validation.constraints.NotBlank;

public record DirectRelationRequest(
        @NotBlank(message = "Table name cannot be empty") String tableName,
        @NotBlank(message = "Related table cannot be empty") String relatedTable,
        DeletePolicy deletePolicy
) {}
