package com.springrest.springrestproject.model.column;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ColumnMetadata {
    private Long id;
    private String columnName;
    private String dataType;
    private ColumnContext columnContext;
    private RelationType relationType;
    private String relatedTable;
    private String relatedColumn;
    private DeletePolicy deletePolicy;
    private String tableName;
}