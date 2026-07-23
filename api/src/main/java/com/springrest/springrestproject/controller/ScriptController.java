package com.springrest.springrestproject.controller;

import com.springrest.scripting.model.CallerOrigin;
import com.springrest.scripting.model.ScriptCaller;
import com.springrest.scripting.engine.ScriptExecutionService;
import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.dto.request.scripting.ScriptExecuteRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
    public ApiResponse<Object> executeScript(@RequestBody ScriptExecuteRequest payload, Authentication authentication) {
        String script = payload != null ? payload.script() : null;
        boolean debugEnabled = payload != null && Boolean.TRUE.equals(payload.debugEnabled());

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

        ScriptCaller caller = new ScriptCaller(userId, roles, CallerOrigin.USER_SUBMITTED);
        Object result = scriptExecutionService.executeAdhoc(script, caller, debugEnabled);
        return ApiResponse.success(result);
    }
}
