package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.model.ExecutionLog;
import jooq.generated.tables.records.ExecutionLogsRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.EXECUTION_LOGS;

@Repository
@RequiredArgsConstructor
public class ExecutionLogRepo {
    private final DSLContext dsl;

    @Transactional(readOnly = true)
    public Optional<ExecutionLog> findByExecutionId(String executionId) {
        return dsl.selectFrom(EXECUTION_LOGS)
                .where(EXECUTION_LOGS.EXECUTION_ID.eq(executionId))
                .fetchOptional()
                .map(this::toExecutionLog);
    }

    @Transactional
    public ExecutionLog save(ExecutionLog log) {
        if (log.id() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(EXECUTION_LOGS)
                            .set(EXECUTION_LOGS.EXECUTION_ID, log.executionId())
                            .set(EXECUTION_LOGS.SCRIPT, log.script())
                            .set(EXECUTION_LOGS.CALLER, log.caller())
                            .set(EXECUTION_LOGS.STATUS, log.status().name())
                            .set(EXECUTION_LOGS.OUTPUT, log.output())
                            .set(EXECUTION_LOGS.DURATION_MS, log.durationMs())
                            .set(EXECUTION_LOGS.ERROR_MESSAGE, log.errorMessage())
                            .set(EXECUTION_LOGS.CREATED_AT, log.createdAt())
                            .set(EXECUTION_LOGS.FINISHED_AT, log.finishedAt())
                            .returning(EXECUTION_LOGS.ID)
                            .fetchOne())
                    .getValue(EXECUTION_LOGS.ID);

            return ExecutionLog.builder()
                    .id(generatedId)
                    .executionId(log.executionId())
                    .script(log.script())
                    .caller(log.caller())
                    .status(log.status())
                    .output(log.output())
                    .durationMs(log.durationMs())
                    .errorMessage(log.errorMessage())
                    .createdAt(log.createdAt())
                    .finishedAt(log.finishedAt())
                    .build();
        } else {
            dsl.update(EXECUTION_LOGS)
                    .set(EXECUTION_LOGS.EXECUTION_ID, log.executionId())
                    .set(EXECUTION_LOGS.SCRIPT, log.script())
                    .set(EXECUTION_LOGS.CALLER, log.caller())
                    .set(EXECUTION_LOGS.STATUS, log.status().name())
                    .set(EXECUTION_LOGS.OUTPUT, log.output())
                    .set(EXECUTION_LOGS.DURATION_MS, log.durationMs())
                    .set(EXECUTION_LOGS.ERROR_MESSAGE, log.errorMessage())
                    .set(EXECUTION_LOGS.CREATED_AT, log.createdAt())
                    .set(EXECUTION_LOGS.FINISHED_AT, log.finishedAt())
                    .where(EXECUTION_LOGS.ID.eq(log.id()))
                    .execute();
            return log;
        }
    }

    private ExecutionLog toExecutionLog(ExecutionLogsRecord record) {
        return ExecutionLog.builder()
                .id(record.getId())
                .executionId(record.getExecutionId())
                .script(record.getScript())
                .caller(record.getCaller())
                .status(ExecutionStatus.valueOf(record.getStatus()))
                .output(record.getOutput())
                .durationMs(record.getDurationMs())
                .errorMessage(record.getErrorMessage())
                .createdAt(record.getCreatedAt())
                .finishedAt(record.getFinishedAt())
                .build();
    }
}
