package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.repository.AppUserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ScriptManagementServiceTest extends BaseIntegrationTest {

    @Autowired
    private ScriptManagementService service;
    @Autowired
    private AppUserRepo appUserRepo;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long seedTable(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO sys_table_metadata (table_name, creator_id, created_date, last_updater_id, last_changed_date, is_audit_enabled, is_restricted) " +
                        "VALUES (?, 0, now(), 0, now(), false, false) RETURNING id",
                Long.class, name);
    }

    private Long seedUserInGroup(String username, GroupName group) {
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO sys_app_users (username, password, active, creator_id, created_date, last_updater_id, last_changed_date, is_restricted) " +
                        "VALUES (?, 'x', true, 0, now(), 0, now(), false) RETURNING id",
                Long.class, username);
        appUserRepo.saveGroup(userId, group, 0L);
        return userId;
    }

    @Test
    void scriptEngineerCanCreateDbScript() {
        Long callerId = seedUserInGroup("script_mgmt_engineer_1", GroupName.SCRIPT_ENGINEER);
        Long tableId = seedTable("script_mgmt_test_table_1");

        Script saved = service.createScript(ScriptType.DB, tableId, null, "function beforeSaveToDB() {}", callerId);

        assertEquals(ScriptType.DB, saved.scriptType());
        assertEquals(tableId, saved.tableId());
    }

    @Test
    void nonScriptEngineerCannotCreateDbScript() {
        Long callerId = seedUserInGroup("script_mgmt_registered_1", GroupName.REGISTERED_USER);
        Long tableId = seedTable("script_mgmt_test_table_2");

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                service.createScript(ScriptType.DB, tableId, null, "function beforeSaveToDB() {}", callerId));
        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
    }

    @Test
    void bothTargetsSetIsRejected() {
        Long callerId = seedUserInGroup("script_mgmt_engineer_2", GroupName.SCRIPT_ENGINEER);
        Long tableId = seedTable("script_mgmt_test_table_3");

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                service.createScript(ScriptType.DB, tableId, 999L, "function beforeSaveToDB() {}", callerId));
        assertEquals(ErrorCode.SCRIPT_INVALID_ASSOCIATION, ex.getErrorCode());
    }

    @Test
    void secondScriptForSameTableIsRejectedWithFriendlyError() {
        Long callerId = seedUserInGroup("script_mgmt_engineer_3", GroupName.SCRIPT_ENGINEER);
        Long tableId = seedTable("script_mgmt_test_table_4");
        service.createScript(ScriptType.DB, tableId, null, "function beforeSaveToDB() {}", callerId);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                service.createScript(ScriptType.DB, tableId, null, "function afterSaveToDB() {}", callerId));
        assertEquals(ErrorCode.SCRIPT_ALREADY_EXISTS_FOR_TARGET, ex.getErrorCode());
    }

    @Test
    void deleteFreesTheSlot() {
        Long callerId = seedUserInGroup("script_mgmt_engineer_4", GroupName.SCRIPT_ENGINEER);
        Long tableId = seedTable("script_mgmt_test_table_5");
        Script saved = service.createScript(ScriptType.DB, tableId, null, "function beforeSaveToDB() {}", callerId);

        service.deleteScript(saved.id(), callerId);

        Script replacement = service.createScript(ScriptType.DB, tableId, null, "function afterSaveToDB() {}", callerId);
        assertNotEquals(saved.id(), replacement.id());
    }

    @Test
    void updatePreservesIdAndDoesNotOrphanExecutionLogHistory() {
        Long callerId = seedUserInGroup("script_mgmt_engineer_5", GroupName.SCRIPT_ENGINEER);
        Long tableId = seedTable("script_mgmt_test_table_6");
        Script saved = service.createScript(ScriptType.DB, tableId, null, "function beforeSaveToDB() {}", callerId);

        jdbcTemplate.update(
                "INSERT INTO sys_execution_logs (execution_id, script_id, caller, status, created_at) " +
                        "VALUES (?, ?, ?, 'SUCCESS', now())",
                "exec-" + saved.id(), saved.id(), String.valueOf(callerId));

        Script updated = service.updateScript(saved.id(), "function beforeSaveToDB() { console.log('changed'); }", callerId);

        assertEquals(saved.id(), updated.id());
        assertEquals("function beforeSaveToDB() { console.log('changed'); }", updated.scriptBody());

        Integer linkedLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_execution_logs WHERE script_id = ?", Integer.class, saved.id());
        assertEquals(1, linkedLogCount);
    }
}
