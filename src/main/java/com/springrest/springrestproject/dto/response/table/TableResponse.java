package com.springrest.springrestproject.dto.response.table;

import com.springrest.springrestproject.model.ColumnMetadata;
import java.util.List;

public record TableResponse(Long id, String tableName, List<ColumnMetadata> columns) {}
