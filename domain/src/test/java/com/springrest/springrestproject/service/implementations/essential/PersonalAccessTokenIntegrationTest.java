package com.springrest.springrestproject.service.implementations.essential;

import com.springrest.springrestproject.BaseIntegrationTest;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.model.user.AppUser;
import com.springrest.springrestproject.repository.AppUserRepo;
import com.springrest.springrestproject.service.interfaces.IPersonalAccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PersonalAccessTokenIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IPersonalAccessTokenService patService;

    @Autowired
    private AppUserRepo appUserRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM sys_app_users WHERE username = 'pat_test_user'");
        AppUser user = AppUser.builder()
                .username("pat_test_user")
                .password(passwordEncoder.encode("password123"))
                .active(true)
                .build();
        testUser = appUserRepo.save(user);
    }

    @Test
    void shouldCreateAndValidatePersonalAccessToken() {
        String tokenName = "My Test PAT";
        String rawToken = patService.createToken(testUser.username(), "password123", 10, tokenName);
        assertNotNull(rawToken);
        assertTrue(rawToken.startsWith("pat_"));

        Long resolvedUserId = patService.validateTokenAndGetUserId(rawToken);
        assertEquals(testUser.id(), resolvedUserId);
    }

    @Test
    void shouldInvalidateOldTokenWhenNewTokenCreated() {
        String token1 = patService.createToken(testUser.username(), "password123", 10, "Token 1");
        assertNotNull(token1);
        assertEquals(testUser.id(), patService.validateTokenAndGetUserId(token1));

        String token2 = patService.createToken(testUser.username(), "password123", 10, "Token 2");
        assertNotNull(token2);
        assertEquals(testUser.id(), patService.validateTokenAndGetUserId(token2));

        ApplicationException ex = assertThrows(ApplicationException.class, () -> 
                patService.validateTokenAndGetUserId(token1)
        );
        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
    }

    @Test
    void shouldFailValidationOnExpiredToken() {
        String rawToken = patService.createToken(testUser.username(), "password123", -5, "Expired Token");
        assertNotNull(rawToken);

        ApplicationException ex = assertThrows(ApplicationException.class, () -> 
                patService.validateTokenAndGetUserId(rawToken)
        );
        assertEquals(ErrorCode.UNAUTHORIZED_ACCESS, ex.getErrorCode());
    }

    @Test
    void shouldFailCreationOnInvalidCredentials() {
        assertThrows(ApplicationException.class, () -> 
                patService.createToken(testUser.username(), "wrong_password", 10, "Token")
        );
    }
}
