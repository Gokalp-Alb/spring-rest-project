package com.springrest.springrestproject.dto.response.table;

import com.springrest.springrestproject.model.ColumnMetadata;
import com.springrest.springrestproject.model.TableContext;
import java.util.List;

public record TableResponse(
        Long id,
        String tableName,
        List<ColumnMetadata> columns,
        TableContext tableContext
) {}
