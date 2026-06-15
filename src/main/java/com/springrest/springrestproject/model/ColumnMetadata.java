package com.springrest.springrestproject.model;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ColumnMetadata {
    private String columnName;
    private String dataType;
}
