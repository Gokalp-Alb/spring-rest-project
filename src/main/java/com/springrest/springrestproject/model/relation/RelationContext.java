package com.springrest.springrestproject.model.relation;

import java.time.LocalDateTime;

public record RelationContext(
    Long creatorId,
    LocalDateTime createdDate,
    Long lastUpdaterId,
    LocalDateTime lastChangedDate
) {}
