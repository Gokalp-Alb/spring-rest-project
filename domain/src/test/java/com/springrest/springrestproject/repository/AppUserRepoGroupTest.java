package com.springrest.springrestproject.repository;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.model.user.Role;
import com.springrest.springrestproject.model.user.UserGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AppUserRepoGroupTest extends BaseIntegrationTest {

    @Autowired
    private AppUserRepo appUserRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM app_users WHERE username = 'group_repo_test_user'");
        AppUser user = AppUser.builder()
                .username("group_repo_test_user")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .active(true)
                .build();
        testUser = appUserRepo.save(user);
    }

    @Test
    void shouldSaveAndFindGroupsByUserId() {
        appUserRepo.saveGroup(testUser.id(), GroupName.SCRIPT_ENGINEER, 1L);

        List<UserGroup> groups = appUserRepo.findGroupsByUserId(testUser.id());

        assertEquals(1, groups.size());
        assertEquals(GroupName.SCRIPT_ENGINEER, groups.get(0).groupName());
        assertEquals(testUser.id(), groups.get(0).userId());
    }

    @Test
    void shouldReportExistsByUserIdAndGroupName() {
        assertFalse(appUserRepo.existsByUserIdAndGroupName(testUser.id(), GroupName.SCRIPT_ENGINEER));

        appUserRepo.saveGroup(testUser.id(), GroupName.SCRIPT_ENGINEER, 1L);

        assertTrue(appUserRepo.existsByUserIdAndGroupName(testUser.id(), GroupName.SCRIPT_ENGINEER));
    }

    @Test
    void shouldFindGroupByIdAndByUserIdAndName() {
        UserGroup saved = appUserRepo.saveGroup(testUser.id(), GroupName.SCRIPT_ENGINEER, 1L);

        Optional<UserGroup> byId = appUserRepo.findGroupById(saved.id());
        Optional<UserGroup> byUserAndName = appUserRepo.findGroupByUserIdAndName(testUser.id(), GroupName.SCRIPT_ENGINEER);

        assertTrue(byId.isPresent());
        assertTrue(byUserAndName.isPresent());
        assertEquals(saved.id(), byId.get().id());
        assertEquals(saved.id(), byUserAndName.get().id());
    }

    @Test
    void shouldDeleteGroup() {
        UserGroup saved = appUserRepo.saveGroup(testUser.id(), GroupName.SCRIPT_ENGINEER, 1L);

        appUserRepo.deleteGroup(saved, 1L);

        assertTrue(appUserRepo.findGroupById(saved.id()).isEmpty());
    }

    @Test
    void shouldInsertRegisteredUserGroup() {
        appUserRepo.insertRegisteredUserGroup(testUser.id(), 1L);

        List<UserGroup> groups = appUserRepo.findGroupsByUserId(testUser.id());

        assertEquals(1, groups.size());
        assertEquals(GroupName.REGISTERED_USER, groups.get(0).groupName());
    }

    @Test
    void shouldCascadeDeleteGroupsWhenUserIsHardDeleted() {
        appUserRepo.saveGroup(testUser.id(), GroupName.SCRIPT_ENGINEER, 1L);

        jdbcTemplate.update("DELETE FROM app_users WHERE id = ?", testUser.id());

        assertTrue(appUserRepo.findGroupsByUserId(testUser.id()).isEmpty());
    }
}
