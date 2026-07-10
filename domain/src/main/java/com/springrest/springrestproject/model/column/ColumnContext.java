package com.springrest.springrestproject.model.column;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record ColumnContext(
        //Json Property required false is set for sandbox mcp
        @JsonProperty(required = false) Long creatorId,
        @JsonProperty(required = false) LocalDateTime createdDate,
        @JsonProperty(required = false) Long lastUpdaterId,
        @JsonProperty(required = false) LocalDateTime lastChangedDate,
        @JsonProperty(required = false) Boolean isSensitive,
        @JsonProperty(required = false) Boolean isUnique,
        @JsonProperty(required = false) ValidRegexPatterns validationRegex
) {}