package com.springrest.springrestproject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springrest.springrestproject.config.SecurityConfig;
import com.springrest.springrestproject.dto.request.relation.DirectRelationRequest;
import com.springrest.springrestproject.dto.request.relation.ManyToManyRelationRequest;
import com.springrest.springrestproject.dto.response.relation.RelationResponse;
import com.springrest.springrestproject.model.relation.DeletePolicy;
import com.springrest.springrestproject.model.relation.RelationType;
import com.springrest.springrestproject.service.implementations.redis.RateLimiterService;
import com.springrest.springrestproject.service.interfaces.IRelationService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration
class RelationControllerTestConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}

@WebMvcTest(RelationController.class)
@Import({RelationControllerTestConfig.class, SecurityConfig.class})
class RelationControllerDatabaseAdminTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private IRelationService relationService;

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
    void createOneToOneRelationSucceedsForDatabaseAdmin() throws Exception {
        DirectRelationRequest request = new DirectRelationRequest("orders", "customers", DeletePolicy.CASCADE);
        RelationResponse response = new RelationResponse("orders", "customer_id", "customers", "id",
                RelationType.ONE_TO_ONE, DeletePolicy.CASCADE, DeletePolicy.CASCADE);
        when(relationService.createOneToOneRelation(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/relations/one-to-one")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(databaseAdminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceTable").value("orders"));
    }

    @Test
    void createOneToOneRelationForbiddenWithoutDatabaseAdminRole() throws Exception {
        DirectRelationRequest request = new DirectRelationRequest("orders", "customers", DeletePolicy.CASCADE);

        mockMvc.perform(post("/api/relations/one-to-one")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createManyToOneRelationForbiddenWithoutDatabaseAdminRole() throws Exception {
        DirectRelationRequest request = new DirectRelationRequest("orders", "customers", DeletePolicy.CASCADE);

        mockMvc.perform(post("/api/relations/many-to-one")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createManyToManyRelationSucceedsForDatabaseAdmin() throws Exception {
        ManyToManyRelationRequest request = new ManyToManyRelationRequest("orders", "products", DeletePolicy.CASCADE, DeletePolicy.CASCADE);
        RelationResponse response = new RelationResponse("orders", null, "products", null,
                RelationType.MANY_TO_MANY, DeletePolicy.CASCADE, DeletePolicy.CASCADE, "orders_products_jt");
        when(relationService.createManyToManyRelation(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/relations/many-to-many")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(databaseAdminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.junctionTableName").value("orders_products_jt"));
    }

    @Test
    void createManyToManyRelationForbiddenWithoutDatabaseAdminRole() throws Exception {
        ManyToManyRelationRequest request = new ManyToManyRelationRequest("orders", "products", DeletePolicy.CASCADE, DeletePolicy.CASCADE);

        mockMvc.perform(post("/api/relations/many-to-many")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllRelationsRemainsAccessibleWithoutDatabaseAdminRole() throws Exception {
        when(relationService.getAllRelations()).thenReturn(List.of());

        mockMvc.perform(get("/api/relations")
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isOk());
    }
}
