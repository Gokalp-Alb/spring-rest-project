package com.springrest.springrestproject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springrest.springrestproject.config.SecurityConfig;
import com.springrest.springrestproject.core.mapper.TableMapper;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.model.column.ColumnMetadata;
import com.springrest.springrestproject.service.implementations.redis.RateLimiterService;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration
class TableControllerTestConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}

@WebMvcTest(TableController.class)
@Import({TableControllerTestConfig.class, SecurityConfig.class})
class TableControllerDatabaseAdminTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private IMetadataService metadataService;

    @MockitoBean
    private TableMapper tableMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private CacheManager cacheManager;

    private Authentication databaseAdminAuth() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("userId", 1L).build();
        return new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_REGISTERED_USER", () -> "ROLE_DATABASE_ADMIN"));
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
    void createTableSucceedsForDatabaseAdmin() throws Exception {
        TableCreateRequest request = new TableCreateRequest(List.of(new ColumnMetadata(null, "id", "BIGINT", null, null)), false);
        TableResponse response = new TableResponse(1L, "orders", request.columns(), null);
        when(metadataService.createTable(anyString(), any(), any())).thenReturn(null);
        when(tableMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/tables/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(databaseAdminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tableName").value("orders"));
    }

    @Test
    void createTableForbiddenWithoutDatabaseAdminRole() throws Exception {
        TableCreateRequest request = new TableCreateRequest(List.of(new ColumnMetadata(null, "id", "BIGINT", null, null)), false);

        mockMvc.perform(post("/api/tables/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTableSucceedsForDatabaseAdmin() throws Exception {
        TableResponse response = new TableResponse(1L, "orders", List.of(), null);
        when(metadataService.deleteTableByName(anyString(), any())).thenReturn(response);

        mockMvc.perform(delete("/api/tables/orders")
                        .with(authentication(databaseAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tableName").value("orders"));
    }

    @Test
    void deleteTableForbiddenWithoutDatabaseAdminRole() throws Exception {
        mockMvc.perform(delete("/api/tables/orders")
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllTablesRemainsAccessibleWithoutDatabaseAdminRole() throws Exception {
        when(metadataService.getAllTables(any())).thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/tables")
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isOk());
    }
}
