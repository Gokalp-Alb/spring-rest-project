package com.springrest.springrestproject.model.table;

import com.springrest.springrestproject.model.column.ColumnMetadata;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TableMetadata {
    private Long id;
    private String tableName;
    private List<ColumnMetadata> columns;
    private TableContext tableContext;
    private Boolean isAuditEnabled;
}