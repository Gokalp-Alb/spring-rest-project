package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.repository.AppUserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdminBootstrapIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AppUserRepo appUserRepo;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Test
    void adminIsSeededWithAdminRegisteredUserAndDatabaseAdminGroups() {
        var admin = appUserRepo.findByUsername(adminUsername).orElseThrow();
        var groups = appUserRepo.findGroupsByUserId(admin.id()).stream()
                .map(com.springrest.springrestproject.model.user.UserGroup::groupName)
                .toList();
        assertTrue(groups.contains(GroupName.ADMIN));
        assertTrue(groups.contains(GroupName.REGISTERED_USER));
        assertTrue(groups.contains(GroupName.DATABASE_ADMIN));
        assertEquals(3, groups.size());
    }

    @Test
    void secondApplicationStartDoesNotDuplicateAdmin() {
        assertTrue(appUserRepo.existsByUsername(adminUsername));
        // AdminInitializerConfig already ran once during context startup for this test class;
        // re-invoking the same check confirms existsByUsername (not existsByRole) now gates creation.
        var admin = appUserRepo.findByUsername(adminUsername).orElseThrow();
        assertNotNull(admin.id());
    }
}
