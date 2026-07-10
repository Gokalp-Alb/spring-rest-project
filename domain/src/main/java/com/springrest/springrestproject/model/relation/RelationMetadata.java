package com.springrest.springrestproject.model.relation;

import lombok.Builder;

@Builder
public record RelationMetadata(
    Long id,
    RelationType relationType,
    String sourceTable,
    String sourceColumn,
    String targetTable,
    String targetColumn,
    String junctionTable,
    DeletePolicy sourceDeletePolicy,
    DeletePolicy targetDeletePolicy,
    RelationContext relationContext
) {}
