package com.springrest.springrestproject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springrest.springrestproject.config.SecurityConfig;
import com.springrest.springrestproject.dto.response.user.GroupResponse;
import com.springrest.springrestproject.model.user.GroupName;
import com.springrest.springrestproject.service.implementations.redis.RateLimiterService;
import com.springrest.springrestproject.service.interfaces.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.boot.test.context.TestConfiguration;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration
class UserGroupTestConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}

@WebMvcTest(UserController.class)
@Import({UserGroupTestConfig.class, SecurityConfig.class})
class UserGroupControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private IUserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private CacheManager cacheManager;

    private Authentication adminAuth() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("userId", 1L).build();
        return new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_ADMIN"));
    }

    private Authentication userAuth() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("userId", 2L).build();
        return new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_USER"));
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);
    }

    @Test
    void addGroupToUser_shouldReturnCreated() throws Exception {
        GroupResponse response = new GroupResponse(10L, 5L, GroupName.SCRIPT_ENGINEER, LocalDateTime.now());
        when(userService.addGroupToUser(eq(5L), eq(GroupName.SCRIPT_ENGINEER), anyLong()))
                .thenReturn(response);

        mockMvc.perform(post("/api/users/5/groups")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("groupName", "SCRIPT_ENGINEER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.groupName").value("SCRIPT_ENGINEER"));
    }

    @Test
    void getGroupsForUser_shouldReturnList() throws Exception {
        GroupResponse response = new GroupResponse(10L, 5L, GroupName.REGISTERED_USER, LocalDateTime.now());
        when(userService.getGroupsForUser(5L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/users/5/groups")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].groupName").value("REGISTERED_USER"));
    }

    @Test
    void removeGroupById_shouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/users/5/groups/id/10")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void removeGroupByName_shouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/users/5/groups/name/SCRIPT_ENGINEER")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void getGroupsForUser_nonAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/users/5/groups")
                        .with(authentication(userAuth())))
                .andExpect(status().isForbidden());
    }
}
