package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.model.SystemDdlLog;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Objects;

import static jooq.generated.Tables.SYSTEM_DDL_LOG;

@Repository
@RequiredArgsConstructor
public class SystemDdlLogRepo{
    private final DSLContext dsl;

    @Transactional
    public void save(SystemDdlLog log) {
        if (log.getId() == null) {
            Long generatedId = Objects.requireNonNull(dsl.insertInto(SYSTEM_DDL_LOG)
                            .set(SYSTEM_DDL_LOG.TABLE_NAME, log.getTableName())
                            .set(SYSTEM_DDL_LOG.EXECUTED_SQL, log.getExecutedSql())
                            .set(SYSTEM_DDL_LOG.USER_ID, log.getUserId())
                            .set(SYSTEM_DDL_LOG.EXECUTED_AT, log.getExecutedAt())
                            .returning(SYSTEM_DDL_LOG.ID)
                            .fetchOne())
                            .getValue(SYSTEM_DDL_LOG.ID);
            log.setId(generatedId);
        } else {
            dsl.update(SYSTEM_DDL_LOG)
                    .set(SYSTEM_DDL_LOG.TABLE_NAME, log.getTableName())
                    .set(SYSTEM_DDL_LOG.EXECUTED_SQL, log.getExecutedSql())
                    .set(SYSTEM_DDL_LOG.USER_ID, log.getUserId())
                    .set(SYSTEM_DDL_LOG.EXECUTED_AT, log.getExecutedAt())
                    .where(SYSTEM_DDL_LOG.ID.eq(log.getId()))
                    .execute();
        }
    }
}