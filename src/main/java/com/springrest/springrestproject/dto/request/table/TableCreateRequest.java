package com.springrest.springrestproject.dto.request.table;

import com.springrest.springrestproject.model.ColumnMetadata;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TableCreateRequest(
        @NotEmpty(message = "Table name cannot be empty") String tableName,
        @NotEmpty(message = "Columns list cannot be empty") List<ColumnMetadata> columns
) {}