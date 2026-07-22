package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.service.interfaces.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class McpAgentLockdownTest extends BaseIntegrationTest {

    @Autowired
    private IUserService userService;
    @Autowired
    private AppUserRepo appUserRepo;

    @Test
    void cannotAddGroupToMcpAgent() {
        Long mcpAgentId = appUserRepo.findByUsername("mcp_agent").orElseThrow().id();
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> userService.addGroupToUser(mcpAgentId, GroupName.KAFKA_ENGINEER, 0L));
        assertEquals(ErrorCode.SYSTEM_ACCOUNT_GROUPS_LOCKED, ex.getErrorCode());
    }

    @Test
    void cannotRemoveMcpAgentGroupFromMcpAgent() {
        Long mcpAgentId = appUserRepo.findByUsername("mcp_agent").orElseThrow().id();
        Long junctionId = appUserRepo.findGroupByUserIdAndName(mcpAgentId, GroupName.MCP_AGENT).orElseThrow().id();
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> userService.removeGroupById(mcpAgentId, junctionId, 0L));
        assertEquals(ErrorCode.SYSTEM_ACCOUNT_GROUPS_LOCKED, ex.getErrorCode());
    }
}
