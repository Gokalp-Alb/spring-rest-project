package com.springrest.springrestproject.core.model.scripting;

import java.time.LocalDateTime;

public record ExecutionLogEntry(
    Long logId,
    String executionId,
    LogLevel level,
    String message,
    LocalDateTime loggedAt,
    int sequenceNumber,
    String stackTrace
) {}
