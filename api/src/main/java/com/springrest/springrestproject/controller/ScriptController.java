package com.springrest.springrestproject.controller;

import com.springrest.scripting.model.ScriptCaller;
import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.springrestproject.core.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/script")
public class ScriptController {

    private final ScriptExecutionService scriptExecutionService;

    public ScriptController(ScriptExecutionService scriptExecutionService) {
        this.scriptExecutionService = scriptExecutionService;
    }

    @PostMapping
    public ApiResponse<Object> executeScript(@RequestBody Map<String, String> payload, Authentication authentication) {
        String script = payload != null ? payload.get("script") : null;

        String userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            Object userIdObj = jwt.getClaim("userId");
            if (userIdObj != null) {
                userId = String.valueOf(userIdObj);
            }
        }

        Set<String> roles = Set.of();
        if (authentication != null) {
            roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
        }

        ScriptCaller caller = new ScriptCaller(userId, roles);
        Object result = scriptExecutionService.execute(script, caller);
        return ApiResponse.success(result);
    }
}
