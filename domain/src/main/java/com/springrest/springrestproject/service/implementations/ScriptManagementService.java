package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.repository.ScriptRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScriptManagementService {

    private static final int MAX_SCRIPT_BODY_LENGTH = 100_000;

    private final ScriptRepo scriptRepo;
    private final AppUserRepo appUserRepo;

    @Transactional
    public Script createScript(ScriptType scriptType, Long tableId, Long topicId, String scriptBody, Long callerId) {
        assertCallerAuthorized(scriptType, callerId);
        assertValidAssociation(scriptType, tableId, topicId);
        assertValidScriptBody(scriptBody);

        try {
            return scriptRepo.save(Script.builder()
                    .scriptType(scriptType).tableId(tableId).topicId(topicId).scriptBody(scriptBody)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ApplicationException(ErrorCode.SCRIPT_ALREADY_EXISTS_FOR_TARGET);
        }
    }

    @Transactional
    public Script updateScript(Long id, String scriptBody, Long callerId) {
        Script existing = scriptRepo.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "id: " + id));
        assertCallerAuthorized(existing.scriptType(), callerId);
        assertValidScriptBody(scriptBody);

        Script updated = Script.builder()
                .id(existing.id()).scriptType(existing.scriptType())
                .tableId(existing.tableId()).topicId(existing.topicId())
                .scriptBody(scriptBody)
                .build();
        return scriptRepo.update(updated);
    }

    @Transactional
    public void deleteScript(Long id, Long callerId) {
        Script existing = scriptRepo.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "id: " + id));
        assertCallerAuthorized(existing.scriptType(), callerId);
        scriptRepo.delete(id);
    }

    public Script getScript(Long id) {
        return scriptRepo.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "id: " + id));
    }

    public List<Script> listScripts() {
        return scriptRepo.findAll();
    }

    private void assertValidScriptBody(String scriptBody) {
        if (scriptBody != null && scriptBody.length() > MAX_SCRIPT_BODY_LENGTH) {
            throw new ApplicationException(ErrorCode.SCRIPT_INVALID_PAYLOAD, "Script size exceeds 100,000 characters");
        }
    }

    private void assertValidAssociation(ScriptType scriptType, Long tableId, Long topicId) {
        boolean valid = switch (scriptType) {
            case DB -> tableId != null && topicId == null;
            case KAFKA -> topicId != null && tableId == null;
        };
        if (!valid) {
            throw new ApplicationException(ErrorCode.SCRIPT_INVALID_ASSOCIATION);
        }
    }

    private void assertCallerAuthorized(ScriptType scriptType, Long callerId) {
        GroupName requiredGroup = scriptType == ScriptType.DB ? GroupName.SCRIPT_ENGINEER : GroupName.KAFKA_ENGINEER;
        boolean authorized = callerId != null && appUserRepo.findGroupsByUserId(callerId).stream()
                .anyMatch(group -> group.groupName() == requiredGroup);
        if (!authorized) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS,
                    "Caller does not have " + requiredGroup + " role");
        }
    }
}
