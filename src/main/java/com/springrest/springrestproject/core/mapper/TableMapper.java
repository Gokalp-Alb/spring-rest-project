package com.springrest.springrestproject.core.mapper;

import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.model.table.TableMetadata;
import org.springframework.stereotype.Component;

@Component
public class TableMapper {
    public TableResponse toResponse(TableMetadata metadata) {
        return new TableResponse(
                metadata.getId(),
                metadata.getTableName(),
                metadata.getColumns(),
                metadata.getTableContext()
        );
    }
}