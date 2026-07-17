package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ExecutionLogEntry;
import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.core.model.scripting.LogLevel;
import com.springrest.springrestproject.model.ExecutionLog;
import com.springrest.springrestproject.repository.ExecutionLogEntryRepo;
import com.springrest.springrestproject.repository.ExecutionLogRepo;
import com.springrest.springrestproject.service.interfaces.IExecutionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExecutionLogService implements IExecutionLogService {
    private final ExecutionLogRepo executionLogRepo;
    private final ExecutionLogEntryRepo executionLogEntryRepo;

    @Override
    @Transactional
    public ExecutionLog logStart(String executionId, String script, String caller) {
        ExecutionLog log = ExecutionLog.builder()
                .executionId(executionId)
                .script(script)
                .caller(caller)
                .status(ExecutionStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();
        return executionLogRepo.save(log);
    }

    @Override
    @Transactional
    public void logSuccess(String executionId, String output) {
        ExecutionLog log = findOrThrow(executionId);
        ExecutionLog updated = ExecutionLog.builder()
                .id(log.id())
                .executionId(log.executionId())
                .script(log.script())
                .caller(log.caller())
                .status(ExecutionStatus.SUCCESS)
                .output(output)
                .durationMs(Duration.between(log.createdAt(), LocalDateTime.now()).toMillis())
                .errorMessage(log.errorMessage())
                .createdAt(log.createdAt())
                .finishedAt(LocalDateTime.now())
                .build();
        executionLogRepo.save(updated);
    }

    @Override
    @Transactional
    public void logFailure(String executionId, String errorMessage) {
        logFailure(executionId, errorMessage, ExecutionStatus.FAILED);
    }

    @Override
    @Transactional
    public void logFailure(String executionId, String errorMessage, ExecutionStatus status) {
        ExecutionLog log = findOrThrow(executionId);
        ExecutionLog updated = ExecutionLog.builder()
                .id(log.id())
                .executionId(log.executionId())
                .script(log.script())
                .caller(log.caller())
                .status(status)
                .output(log.output())
                .durationMs(Duration.between(log.createdAt(), LocalDateTime.now()).toMillis())
                .errorMessage(errorMessage)
                .createdAt(log.createdAt())
                .finishedAt(LocalDateTime.now())
                .build();
        executionLogRepo.save(updated);
    }

    @Override
    @Transactional
    public void append(String executionId, LogLevel level, String message, String stackTrace) {
        int nextSequence = executionLogEntryRepo.countByExecutionId(executionId) + 1;
        executionLogEntryRepo.insert(new ExecutionLogEntry(null, executionId, level, message, null, nextSequence, stackTrace));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionLogEntry> getEntries(String executionId) {
        return executionLogEntryRepo.findByExecutionIdOrderBySequence(executionId);
    }

    private ExecutionLog findOrThrow(String executionId) {
        return executionLogRepo.findByExecutionId(executionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "Execution log not found for execution ID: " + executionId));
    }
}
