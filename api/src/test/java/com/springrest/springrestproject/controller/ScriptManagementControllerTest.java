package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.SpringRestProjectApplication;
import com.springrest.springrestproject.config.SecurityConfig;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.service.implementations.ScriptManagementService;
import com.springrest.springrestproject.service.implementations.redis.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// classes = SpringRestProjectApplication.class is required here: domain's test-jar (pulled in
// for DataServiceHookIntegrationTest) also puts DomainTestApplication (itself
// @SpringBootApplication-annotated) on the test classpath, so an unqualified @WebMvcTest scan
// finds two @SpringBootConfiguration classes and fails to boot.
@WebMvcTest(ScriptManagementController.class)
@ContextConfiguration(classes = SpringRestProjectApplication.class)
@Import(SecurityConfig.class)
class ScriptManagementControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private ScriptManagementService scriptManagementService;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private CacheManager cacheManager;
    @MockitoBean
    private RateLimiterService rateLimiterService;

    private Authentication scriptEngineerAuth() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("userId", 1L).build();
        return new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_SCRIPT_ENGINEER"));
    }

    private Authentication registeredUserAuth() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("userId", 2L).build();
        return new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_REGISTERED_USER"));
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);
    }

    @Test
    void createScriptReturnsCreated() throws Exception {
        Script saved = Script.builder().id(1L).scriptType(ScriptType.DB).tableId(5L).scriptBody("function beforeSaveToDB() {}").build();
        when(scriptManagementService.createScript(ScriptType.DB, 5L, null, "function beforeSaveToDB() {}", 1L)).thenReturn(saved);

        mockMvc.perform(post("/api/system-scripts")
                        .contentType("application/json")
                        .content("{\"script_type\":\"DB\",\"table_id\":5,\"script_body\":\"function beforeSaveToDB() {}\"}")
                        .with(authentication(scriptEngineerAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tableId").value(5));
    }

    @Test
    void createScriptForbiddenWithoutEngineerRole() throws Exception {
        mockMvc.perform(post("/api/system-scripts")
                        .contentType("application/json")
                        .content("{\"script_type\":\"DB\",\"table_id\":5,\"script_body\":\"function beforeSaveToDB() {}\"}")
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createScriptReturnsConflictWhenAlreadyExists() throws Exception {
        when(scriptManagementService.createScript(ScriptType.DB, 5L, null, "function beforeSaveToDB() {}", 1L))
                .thenThrow(new ApplicationException(ErrorCode.SCRIPT_ALREADY_EXISTS_FOR_TARGET));

        mockMvc.perform(post("/api/system-scripts")
                        .contentType("application/json")
                        .content("{\"script_type\":\"DB\",\"table_id\":5,\"script_body\":\"function beforeSaveToDB() {}\"}")
                        .with(authentication(scriptEngineerAuth())))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteScriptReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/system-scripts/1")
                        .with(authentication(scriptEngineerAuth())))
                .andExpect(status().isOk());
    }
}
