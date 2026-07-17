package com.springrest.springrestproject.service.interfaces;

import com.springrest.springrestproject.core.model.scripting.ExecutionLogEntry;
import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.core.model.scripting.LogLevel;
import com.springrest.springrestproject.model.ExecutionLog;

import java.util.List;

public interface IExecutionLogService {
    ExecutionLog logStart(String executionId, String script, String caller);
    void logSuccess(String executionId, String output);
    void logFailure(String executionId, String errorMessage);
    void logFailure(String executionId, String errorMessage, ExecutionStatus status);
    void append(String executionId, LogLevel level, String message, String stackTrace);
    List<ExecutionLogEntry> getEntries(String executionId);
}
