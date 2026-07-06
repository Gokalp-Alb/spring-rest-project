package com.springrest.springrestproject.model.table;

import com.springrest.springrestproject.model.column.ColumnMetadata;
import lombok.Builder;
import java.util.List;

@Builder
public record TableMetadata(
    Long id,
    String tableName,
    List<ColumnMetadata> columns,
    TableContext tableContext,
    Boolean isAuditEnabled
) {}