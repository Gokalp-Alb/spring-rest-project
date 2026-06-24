package com.springrest.springrestproject.model;

import lombok.*;
import java.time.LocalDateTime;

@Data @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ColumnContext {
    private Long creatorId;
    private LocalDateTime createdDate;
    private Long lastUpdaterId;
    private LocalDateTime lastChangedDate;
    private Boolean isSensitive = false;
    private Boolean isUnique = false;
    private String validationRegex;
}