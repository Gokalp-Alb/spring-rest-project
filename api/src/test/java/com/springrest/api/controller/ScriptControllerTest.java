package com.springrest.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springrest.scripting.model.ScriptCaller;
import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.springrestproject.SpringRestProjectApplication;
import com.springrest.springrestproject.dto.response.scripting.ScriptExecutionResponse;
import com.springrest.springrestproject.controller.ScriptController;
import com.springrest.springrestproject.service.implementations.redis.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestConfiguration
class ScriptTestConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}

@WebMvcTest(ScriptController.class)
@ContextConfiguration(classes = SpringRestProjectApplication.class)
@Import(ScriptTestConfig.class)
public class ScriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScriptExecutionService scriptExecutionService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executeScript_shouldCallServiceAndReturnResult() throws Exception {
        String script = "const a = 1; a;";
        Map<String, String> requestPayload = Map.of("script", script);

        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt()))
                .thenReturn(true);

        when(scriptExecutionService.execute(eq(script), any(ScriptCaller.class)))
                .thenReturn(new ScriptExecutionResponse(1, List.of()));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("userId", 123L)
                .claim("roles", "ROLE_SCRIPT_ENGINEER")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_SCRIPT_ENGINEER"));

        mockMvc.perform(post("/api/script")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestPayload))
                .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.successStatus").value("success"))
                .andExpect(jsonPath("$.data.result").value(1))
                .andExpect(jsonPath("$.data.logs").isArray());
    }
}
