package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jooq.generated.Tables.SYS_SCRIPTS;
import static jooq.generated.Tables.SYS_SCRIPTS_LOG;

@Repository
@RequiredArgsConstructor
public class ScriptRepo {
    private final DSLContext dsl;

    public Optional<Script> findById(Long id) {
        return dsl.selectFrom(SYS_SCRIPTS)
                .where(SYS_SCRIPTS.ID.eq(id))
                .fetchOptional(this::toScript);
    }

    public Optional<Script> findByTableId(Long tableId) {
        return dsl.selectFrom(SYS_SCRIPTS)
                .where(SYS_SCRIPTS.TABLE_ID.eq(tableId))
                .and(SYS_SCRIPTS.SCRIPT_TYPE.eq(ScriptType.DB.name()))
                .fetchOptional(this::toScript);
    }

    public Optional<Script> findByTopicId(Long topicId) {
        return dsl.selectFrom(SYS_SCRIPTS)
                .where(SYS_SCRIPTS.TOPIC_ID.eq(topicId))
                .and(SYS_SCRIPTS.SCRIPT_TYPE.eq(ScriptType.KAFKA.name()))
                .fetchOptional(this::toScript);
    }

    public List<Script> findAll() {
        return dsl.selectFrom(SYS_SCRIPTS).fetch(this::toScript);
    }

    private Script toScript(Record record) {
        return Script.builder()
                .id(record.get(SYS_SCRIPTS.ID))
                .scriptType(ScriptType.valueOf(record.get(SYS_SCRIPTS.SCRIPT_TYPE)))
                .tableId(record.get(SYS_SCRIPTS.TABLE_ID))
                .topicId(record.get(SYS_SCRIPTS.TOPIC_ID))
                .scriptBody(record.get(SYS_SCRIPTS.SCRIPT_BODY))
                .build();
    }

    public Script save(Script script) {
        Long generatedId = Objects.requireNonNull(dsl.insertInto(SYS_SCRIPTS)
                        .set(SYS_SCRIPTS.SCRIPT_TYPE, script.scriptType().name())
                        .set(SYS_SCRIPTS.TABLE_ID, script.tableId())
                        .set(SYS_SCRIPTS.TOPIC_ID, script.topicId())
                        .set(SYS_SCRIPTS.SCRIPT_BODY, script.scriptBody())
                        .set(SYS_SCRIPTS.CREATOR_ID, currentUserOrSystem())
                        .set(SYS_SCRIPTS.CREATED_DATE, LocalDateTime.now())
                        .set(SYS_SCRIPTS.LAST_UPDATER_ID, currentUserOrSystem())
                        .set(SYS_SCRIPTS.LAST_CHANGED_DATE, LocalDateTime.now())
                        .set(SYS_SCRIPTS.IS_RESTRICTED, true)
                        .returning(SYS_SCRIPTS.ID)
                        .fetchOne())
                .getValue(SYS_SCRIPTS.ID);

        Script saved = Script.builder()
                .id(generatedId)
                .scriptType(script.scriptType())
                .tableId(script.tableId())
                .topicId(script.topicId())
                .scriptBody(script.scriptBody())
                .build();
        logScriptMutation(saved, "POST");
        return saved;
    }

    public Script update(Script script) {
        dsl.update(SYS_SCRIPTS)
                .set(SYS_SCRIPTS.SCRIPT_BODY, script.scriptBody())
                .set(SYS_SCRIPTS.LAST_UPDATER_ID, currentUserOrSystem())
                .set(SYS_SCRIPTS.LAST_CHANGED_DATE, LocalDateTime.now())
                .where(SYS_SCRIPTS.ID.eq(script.id()))
                .execute();
        logScriptMutation(script, "PUT");
        return script;
    }

    public void delete(Long id) {
        Script existing = findById(id)
                .orElseThrow(() -> new com.springrest.springrestproject.core.exception.ApplicationException(
                        com.springrest.springrestproject.core.exception.ErrorCode.RESOURCE_NOT_FOUND, "id: " + id));
        dsl.deleteFrom(SYS_SCRIPTS).where(SYS_SCRIPTS.ID.eq(id)).execute();
        logScriptMutation(existing, "DELETE");
    }

    private void logScriptMutation(Script script, String operation) {
        Long executorId = currentUserOrSystem();
        dsl.insertInto(SYS_SCRIPTS_LOG)
                .set(SYS_SCRIPTS_LOG.ID, script.id())
                .set(SYS_SCRIPTS_LOG.SCRIPT_TYPE, script.scriptType().name())
                .set(SYS_SCRIPTS_LOG.TABLE_ID, script.tableId())
                .set(SYS_SCRIPTS_LOG.TOPIC_ID, script.topicId())
                .set(SYS_SCRIPTS_LOG.SCRIPT_BODY, script.scriptBody())
                .set(SYS_SCRIPTS_LOG.OPERATION_TYPE, operation)
                .set(SYS_SCRIPTS_LOG.EXECUTED_AT, LocalDateTime.now())
                .set(SYS_SCRIPTS_LOG.USER_ID, executorId)
                .execute();
    }

    private Long currentUserOrSystem() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userId == null ? 0L : userId;
    }
}
