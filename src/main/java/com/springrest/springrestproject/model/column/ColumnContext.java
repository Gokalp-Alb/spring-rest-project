package com.springrest.springrestproject.model.column;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record ColumnContext(
    Long creatorId,
    LocalDateTime createdDate,
    Long lastUpdaterId,
    LocalDateTime lastChangedDate,
    Boolean isSensitive,
    Boolean isUnique,
    ValidRegexPatterns validationRegex
) {}