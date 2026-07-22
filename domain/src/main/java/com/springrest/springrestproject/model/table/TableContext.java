package com.springrest.springrestproject.model.table;

import java.time.LocalDateTime;

public record TableContext(
    Long creatorId,
    LocalDateTime createdDate,
    Long lastUpdaterId,
    LocalDateTime lastChangedDate,
    Boolean isRestricted
) {}