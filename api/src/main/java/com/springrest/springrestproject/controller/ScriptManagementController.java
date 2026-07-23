package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.dto.request.scripting.ScriptCreateRequest;
import com.springrest.springrestproject.dto.request.scripting.ScriptUpdateRequest;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.service.implementations.ScriptManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system-scripts")
@RequiredArgsConstructor
public class ScriptManagementController {

    private final ScriptManagementService scriptManagementService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Script> createScript(@Valid @RequestBody ScriptCreateRequest request, Authentication authentication) {
        Long callerId = extractUserId(authentication);
        Script saved = scriptManagementService.createScript(
                request.scriptType(), request.tableId(), request.topicId(), request.scriptBody(), callerId);
        return ApiResponse.success(HttpStatus.CREATED.value(), saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<Script> updateScript(@PathVariable Long id, @Valid @RequestBody ScriptUpdateRequest request, Authentication authentication) {
        Long callerId = extractUserId(authentication);
        return ApiResponse.success(scriptManagementService.updateScript(id, request.scriptBody(), callerId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteScript(@PathVariable Long id, Authentication authentication) {
        Long callerId = extractUserId(authentication);
        scriptManagementService.deleteScript(id, callerId);
        return ApiResponse.success(HttpStatus.OK.value(), null);
    }

    @GetMapping("/{id}")
    public ApiResponse<Script> getScript(@PathVariable Long id) {
        return ApiResponse.success(scriptManagementService.getScript(id));
    }

    @GetMapping
    public ApiResponse<List<Script>> listScripts() {
        return ApiResponse.success(scriptManagementService.listScripts());
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            Object userIdObj = jwt.getClaim("userId");
            if (userIdObj != null) {
                return Long.valueOf(String.valueOf(userIdObj));
            }
        }
        return null;
    }
}
