package com.springrest.springrestproject.model.table;

import lombok.*;
import java.time.LocalDateTime;

@Data @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TableContext {
    private Long creatorId;
    private LocalDateTime createdDate;
    private Long lastUpdaterId;
    private LocalDateTime lastChangedDate;
}