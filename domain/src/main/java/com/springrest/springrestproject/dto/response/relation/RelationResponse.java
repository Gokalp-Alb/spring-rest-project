package com.springrest.springrestproject.dto.response.relation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.model.relation.RelationType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelationResponse(
        String sourceTable,
        String sourceColumn,
        String targetTable,
        String targetColumn,
        RelationType relationType,
        DeletePolicy sourceDeletePolicy,
        DeletePolicy targetDeletePolicy, // Optional, used for many-to-many junction tables
        String junctionTableName
) {
    public RelationResponse(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            RelationType relationType,
            DeletePolicy sourceDeletePolicy,
            DeletePolicy targetDeletePolicy
    ) {
        this(sourceTable, sourceColumn, targetTable, targetColumn, relationType, sourceDeletePolicy, targetDeletePolicy, null);
    }
}
