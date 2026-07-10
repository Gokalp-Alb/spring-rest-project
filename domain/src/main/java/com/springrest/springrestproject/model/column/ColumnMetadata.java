package com.springrest.springrestproject.model.column;

import lombok.Builder;

@Builder
public record ColumnMetadata(
    Long id,
    String columnName,
    String dataType,
    ColumnContext columnContext,
    String tableName
) {}