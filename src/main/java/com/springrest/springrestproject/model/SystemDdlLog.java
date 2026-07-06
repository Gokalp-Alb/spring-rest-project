package com.springrest.springrestproject.model;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record SystemDdlLog(
    Long id,
    String tableName,
    String executedSql,
    Long userId,
    LocalDateTime executedAt
) {}