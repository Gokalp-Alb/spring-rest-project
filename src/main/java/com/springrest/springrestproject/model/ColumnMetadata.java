package com.springrest.springrestproject.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ColumnMetadata {
    private Long id;
    private String columnName;
    private String dataType;
    private ColumnContext columnContext;
}