package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.model.scripting.ExecutionLogEntry;
import com.springrest.springrestproject.core.model.scripting.LogLevel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static jooq.generated.Tables.EXECUTION_LOG_ENTRIES;

@Repository
@RequiredArgsConstructor
public class ExecutionLogEntryRepo {
    private final DSLContext dsl;

    @Transactional(readOnly = true)
    public int countByExecutionId(String executionId) {
        return dsl.fetchCount(dsl.selectFrom(EXECUTION_LOG_ENTRIES)
                .where(EXECUTION_LOG_ENTRIES.EXECUTION_ID.eq(executionId)));
    }

    @Transactional
    public void insert(ExecutionLogEntry entry) {
        dsl.insertInto(EXECUTION_LOG_ENTRIES)
                .set(EXECUTION_LOG_ENTRIES.EXECUTION_ID, entry.executionId())
                .set(EXECUTION_LOG_ENTRIES.LEVEL, entry.level().name())
                .set(EXECUTION_LOG_ENTRIES.MESSAGE, entry.message())
                .set(EXECUTION_LOG_ENTRIES.SEQUENCE_NUMBER, entry.sequenceNumber())
                .set(EXECUTION_LOG_ENTRIES.STACK_TRACE, entry.stackTrace())
                .execute();
    }

    @Transactional(readOnly = true)
    public List<ExecutionLogEntry> findByExecutionIdOrderBySequence(String executionId) {
        return dsl.selectFrom(EXECUTION_LOG_ENTRIES)
                .where(EXECUTION_LOG_ENTRIES.EXECUTION_ID.eq(executionId))
                .orderBy(EXECUTION_LOG_ENTRIES.SEQUENCE_NUMBER.asc())
                .fetch()
                .map(record -> new ExecutionLogEntry(
                        record.getLogId(),
                        record.getExecutionId(),
                        LogLevel.valueOf(record.getLevel()),
                        record.getMessage(),
                        record.getLoggedAt(),
                        record.getSequenceNumber(),
                        record.getStackTrace()
                ));
    }
}
