package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.model.scripting.ExecutionLogEntry;
import com.springrest.springrestproject.core.model.scripting.LogLevel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static jooq.generated.Tables.SYS_EXECUTION_LOG_ENTRIES;

@Repository
@RequiredArgsConstructor
public class ExecutionLogEntryRepo {
    private final DSLContext dsl;
    // NOTE: If update/delete methods are added in the future, inject SystemGovernanceGuard and call assertRowMutable(...) first, matching other system-table repos.

    @Transactional(readOnly = true)
    public int countByExecutionId(String executionId) {
        return dsl.fetchCount(dsl.selectFrom(SYS_EXECUTION_LOG_ENTRIES)
                .where(SYS_EXECUTION_LOG_ENTRIES.EXECUTION_ID.eq(executionId)));
    }

    @Transactional
    public void insert(ExecutionLogEntry entry) {
        dsl.insertInto(SYS_EXECUTION_LOG_ENTRIES)
                .set(SYS_EXECUTION_LOG_ENTRIES.EXECUTION_ID, entry.executionId())
                .set(SYS_EXECUTION_LOG_ENTRIES.LEVEL, entry.level().name())
                .set(SYS_EXECUTION_LOG_ENTRIES.MESSAGE, entry.message())
                .set(SYS_EXECUTION_LOG_ENTRIES.SEQUENCE_NUMBER, entry.sequenceNumber())
                .set(SYS_EXECUTION_LOG_ENTRIES.STACK_TRACE, entry.stackTrace())
                .execute();
    }

    @Transactional(readOnly = true)
    public List<ExecutionLogEntry> findByExecutionIdOrderBySequence(String executionId) {
        return dsl.selectFrom(SYS_EXECUTION_LOG_ENTRIES)
                .where(SYS_EXECUTION_LOG_ENTRIES.EXECUTION_ID.eq(executionId))
                .orderBy(SYS_EXECUTION_LOG_ENTRIES.SEQUENCE_NUMBER.asc())
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
