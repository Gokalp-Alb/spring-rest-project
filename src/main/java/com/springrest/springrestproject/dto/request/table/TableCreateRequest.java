package com.springrest.springrestproject.dto.request.table;

import com.springrest.springrestproject.model.column.ColumnMetadata;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TableCreateRequest(
        @NotEmpty(message = "Columns list cannot be empty") List<ColumnMetadata> columns,
        Boolean isAuditEnabled
) {}