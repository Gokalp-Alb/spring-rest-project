package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.service.implementations.redis.RateLimiterService;
import com.springrest.springrestproject.service.interfaces.IDatabaseManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestConfiguration
class TestConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}

@WebMvcTest(SystemController.class)
@Import(TestConfig.class)
public class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IDatabaseManagementService databaseManagementService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void resetDatabase_shouldCallService() throws Exception {
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt()))
                .thenReturn(true);
        when(databaseManagementService.resetDatabaseToDefault(eq("yes-reset-sandbox"), anyLong()))
                .thenReturn("Success");

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("userId", 123L).build();
        Authentication auth = new JwtAuthenticationToken(jwt);

        mockMvc.perform(post("/api/system/reset-database")
                .param("confirm", "yes-reset-sandbox")
                .with(authentication(auth)))
                .andExpect(status().isOk());
    }
}
