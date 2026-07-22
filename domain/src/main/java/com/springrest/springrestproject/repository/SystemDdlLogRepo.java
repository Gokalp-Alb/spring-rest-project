package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.SystemDdlLog;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Objects;

import static jooq.generated.Tables.SYS_DDL_LOG;

@Repository
@RequiredArgsConstructor
public class SystemDdlLogRepo{
    private final DSLContext dsl;

    @Transactional
    public SystemDdlLog save(SystemDdlLog log) {
        if (log.id() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(SYS_DDL_LOG)
                            .set(SYS_DDL_LOG.TABLE_NAME, log.tableName())
                            .set(SYS_DDL_LOG.EXECUTED_SQL, log.executedSql())
                            .set(SYS_DDL_LOG.USER_ID, log.userId())
                            .set(SYS_DDL_LOG.EXECUTED_AT, log.executedAt())
                            .returning(SYS_DDL_LOG.ID)
                            .fetchOne())
                            .getValue(SYS_DDL_LOG.ID);
            return SystemDdlLog.builder()
                    .id(generatedId)
                    .tableName(log.tableName())
                    .executedSql(log.executedSql())
                    .userId(log.userId())
                    .executedAt(log.executedAt())
                    .build();
        } else {
            dsl.update(SYS_DDL_LOG)
                    .set(SYS_DDL_LOG.TABLE_NAME, log.tableName())
                    .set(SYS_DDL_LOG.EXECUTED_SQL, log.executedSql())
                    .set(SYS_DDL_LOG.USER_ID, log.userId())
                    .set(SYS_DDL_LOG.EXECUTED_AT, log.executedAt())
                    .where(SYS_DDL_LOG.ID.eq(log.id()))
                    .execute();
            return log;
        }
    }
}