package com.springrest.springrestproject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springrest.springrestproject.SpringRestProjectApplication;
import com.springrest.springrestproject.config.SecurityConfig;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.model.KafkaTableMapping;
import com.springrest.springrestproject.service.implementations.Kafka.KafkaMappingService;
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
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration
class KafkaMappingTestConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}

// classes = SpringRestProjectApplication.class is required here: domain's test-jar (pulled in
// for DataServiceHookIntegrationTest) also puts DomainTestApplication (itself
// @SpringBootApplication-annotated) on the test classpath, so an unqualified @WebMvcTest scan
// finds two @SpringBootConfiguration classes and fails to boot.
@WebMvcTest(KafkaMappingController.class)
@ContextConfiguration(classes = SpringRestProjectApplication.class)
@Import({KafkaMappingTestConfig.class, SecurityConfig.class})
class KafkaMappingControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private KafkaMappingService mappingService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    private Authentication kafkaEngineerAuth() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("userId", 1L).build();
        return new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_KAFKA_ENGINEER"));
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
    void listMappingsReturnsOk() throws Exception {
        KafkaTableMapping mapping = KafkaTableMapping.builder()
                .id(1L).tableName("orders").topicId(10L).direction("OUTBOUND").active(true).build();
        when(mappingService.listMappings()).thenReturn(List.of(mapping));

        mockMvc.perform(get("/api/kafka-mappings")
                        .with(authentication(kafkaEngineerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tableName").value("orders"))
                .andExpect(jsonPath("$.data[0].topicId").value(10));
    }

    @Test
    void listMappingsForbiddenWithoutKafkaEngineerRole() throws Exception {
        mockMvc.perform(get("/api/kafka-mappings")
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMappingByIdReturnsOk() throws Exception {
        KafkaTableMapping mapping = KafkaTableMapping.builder()
                .id(1L).tableName("orders").topicId(10L).direction("OUTBOUND").active(true).build();
        when(mappingService.getMapping(1L)).thenReturn(mapping);

        mockMvc.perform(get("/api/kafka-mappings/1")
                        .with(authentication(kafkaEngineerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tableName").value("orders"));
    }

    @Test
    void getMappingByIdReturns404WhenNotFound() throws Exception {
        when(mappingService.getMapping(999L)).thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "id: 999"));

        mockMvc.perform(get("/api/kafka-mappings/999")
                        .with(authentication(kafkaEngineerAuth())))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMappingReturnsOk() throws Exception {
        KafkaTableMapping updated = KafkaTableMapping.builder()
                .id(1L).tableName("orders").topicId(20L).direction("OUTBOUND").active(true).build();
        when(mappingService.updateMapping(1L, "orders", "orders-topic-v2", "OUTBOUND", true)).thenReturn(updated);

        mockMvc.perform(put("/api/kafka-mappings/1")
                        .param("tableName", "orders")
                        .param("topicName", "orders-topic-v2")
                        .param("direction", "OUTBOUND")
                        .param("active", "true")
                        .with(authentication(kafkaEngineerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicId").value(20));
    }

    @Test
    void updateMappingForbiddenWithoutKafkaEngineerRole() throws Exception {
        mockMvc.perform(put("/api/kafka-mappings/1")
                        .param("tableName", "orders")
                        .param("topicName", "orders-topic")
                        .param("direction", "OUTBOUND")
                        .param("active", "true")
                        .with(authentication(registeredUserAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMappingReturns404WhenNotFound() throws Exception {
        when(mappingService.updateMapping(999L, "orders", "orders-topic", "OUTBOUND", true))
                .thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, "id: 999"));

        mockMvc.perform(put("/api/kafka-mappings/999")
                        .param("tableName", "orders")
                        .param("topicName", "orders-topic")
                        .param("direction", "OUTBOUND")
                        .param("active", "true")
                        .with(authentication(kafkaEngineerAuth())))
                .andExpect(status().isNotFound());
    }
}
