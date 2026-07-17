package com.springrest.springrestproject.model;

import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record ExecutionLog(
    Long id,
    String executionId,
    String script,
    String caller,
    ExecutionStatus status,
    String output,
    Long durationMs,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {}
