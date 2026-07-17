package com.springrest.scripting.model;

import com.springrest.scripting.util.ValueToJsonConverter;
import com.springrest.springrestproject.service.implementations.ExecutionLogService;

public record ScriptExecutionContext(
    String executionId,
    ExecutionLogService logService,
    ValueToJsonConverter converter
) {}
