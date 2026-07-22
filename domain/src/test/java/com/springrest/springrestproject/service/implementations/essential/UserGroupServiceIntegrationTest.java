package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.user.UserRequest;
import com.springrest.springrestproject.dto.response.user.GroupResponse;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.service.interfaces.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserGroupServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IUserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM sys_app_users WHERE username = 'group_service_test_user'");
        AppUser newUser = AppUser.builder()
                .username("group_service_test_user")
                .password("password123")
                .build();
        UserRequest created = userService.createUser(newUser, 1L);
        testUserId = created.id();
    }

    @Test
    void createUserShouldAutoAssignRegisteredUserGroup() {
        List<GroupResponse> groups = userService.getGroupsForUser(testUserId);

        assertEquals(1, groups.size());
        assertEquals(GroupName.REGISTERED_USER, groups.get(0).groupName());
    }

    @Test
    void shouldAddScriptEngineerGroupToUser() {
        GroupResponse added = userService.addGroupToUser(testUserId, GroupName.SCRIPT_ENGINEER, 1L);

        assertEquals(GroupName.SCRIPT_ENGINEER, added.groupName());
        assertEquals(2, userService.getGroupsForUser(testUserId).size());
    }

    @Test
    void shouldRejectAssigningRegisteredUserGroupManually() {
        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                userService.addGroupToUser(testUserId, GroupName.REGISTERED_USER, 1L)
        );
        assertEquals(ErrorCode.SYSTEM_MANAGED_GROUP, ex.getErrorCode());
    }

    @Test
    void shouldRejectNullGroupNameOnAdd() {
        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                userService.addGroupToUser(testUserId, null, 1L)
        );
        assertEquals(ErrorCode.INVALID_GROUP_NAME, ex.getErrorCode());
    }

    @Test
    void shouldRejectDuplicateGroupAssignment() {
        userService.addGroupToUser(testUserId, GroupName.SCRIPT_ENGINEER, 1L);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                userService.addGroupToUser(testUserId, GroupName.SCRIPT_ENGINEER, 1L)
        );
        assertEquals(ErrorCode.GROUP_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void shouldRemoveGroupById() {
        GroupResponse added = userService.addGroupToUser(testUserId, GroupName.SCRIPT_ENGINEER, 1L);

        userService.removeGroupById(testUserId, added.id(), 1L);

        assertEquals(1, userService.getGroupsForUser(testUserId).size());
    }

    @Test
    void shouldRejectRemovingRegisteredUserGroupById() {
        GroupResponse registeredUserGroup = userService.getGroupsForUser(testUserId).get(0);

        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                userService.removeGroupById(testUserId, registeredUserGroup.id(), 1L)
        );
        assertEquals(ErrorCode.SYSTEM_MANAGED_GROUP, ex.getErrorCode());
    }

    @Test
    void shouldRemoveGroupByName() {
        userService.addGroupToUser(testUserId, GroupName.SCRIPT_ENGINEER, 1L);

        userService.removeGroupByName(testUserId, "SCRIPT_ENGINEER", 1L);

        assertEquals(1, userService.getGroupsForUser(testUserId).size());
    }

    @Test
    void shouldRejectInvalidGroupNameStringOnRemove() {
        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                userService.removeGroupByName(testUserId, "not_a_real_group", 1L)
        );
        assertEquals(ErrorCode.INVALID_GROUP_NAME, ex.getErrorCode());
    }

    @Test
    void shouldThrowNotFoundForUnknownUser() {
        ApplicationException ex = assertThrows(ApplicationException.class, () ->
                userService.getGroupsForUser(999999L)
        );
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }
}
