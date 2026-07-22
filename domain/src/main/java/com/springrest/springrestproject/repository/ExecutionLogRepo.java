package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.model.ExecutionLog;
import jooq.generated.tables.records.SysExecutionLogsRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.SYS_EXECUTION_LOGS;

@Repository
@RequiredArgsConstructor
public class ExecutionLogRepo {
    private final DSLContext dsl;

    @Transactional(readOnly = true)
    public Optional<ExecutionLog> findByExecutionId(String executionId) {
        return dsl.selectFrom(SYS_EXECUTION_LOGS)
                .where(SYS_EXECUTION_LOGS.EXECUTION_ID.eq(executionId))
                .fetchOptional()
                .map(this::toExecutionLog);
    }

    @Transactional
    public ExecutionLog save(ExecutionLog log) {
        if (log.id() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(SYS_EXECUTION_LOGS)
                            .set(SYS_EXECUTION_LOGS.EXECUTION_ID, log.executionId())
                            .set(SYS_EXECUTION_LOGS.SCRIPT, log.script())
                            .set(SYS_EXECUTION_LOGS.CALLER, log.caller())
                            .set(SYS_EXECUTION_LOGS.STATUS, log.status().name())
                            .set(SYS_EXECUTION_LOGS.OUTPUT, log.output())
                            .set(SYS_EXECUTION_LOGS.DURATION_MS, log.durationMs())
                            .set(SYS_EXECUTION_LOGS.ERROR_MESSAGE, log.errorMessage())
                            .set(SYS_EXECUTION_LOGS.CREATED_AT, log.createdAt())
                            .set(SYS_EXECUTION_LOGS.FINISHED_AT, log.finishedAt())
                            .returning(SYS_EXECUTION_LOGS.ID)
                            .fetchOne())
                    .getValue(SYS_EXECUTION_LOGS.ID);

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
            dsl.update(SYS_EXECUTION_LOGS)
                    .set(SYS_EXECUTION_LOGS.EXECUTION_ID, log.executionId())
                    .set(SYS_EXECUTION_LOGS.SCRIPT, log.script())
                    .set(SYS_EXECUTION_LOGS.CALLER, log.caller())
                    .set(SYS_EXECUTION_LOGS.STATUS, log.status().name())
                    .set(SYS_EXECUTION_LOGS.OUTPUT, log.output())
                    .set(SYS_EXECUTION_LOGS.DURATION_MS, log.durationMs())
                    .set(SYS_EXECUTION_LOGS.ERROR_MESSAGE, log.errorMessage())
                    .set(SYS_EXECUTION_LOGS.CREATED_AT, log.createdAt())
                    .set(SYS_EXECUTION_LOGS.FINISHED_AT, log.finishedAt())
                    .where(SYS_EXECUTION_LOGS.ID.eq(log.id()))
                    .execute();
            return log;
        }
    }

    private ExecutionLog toExecutionLog(SysExecutionLogsRecord record) {
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
