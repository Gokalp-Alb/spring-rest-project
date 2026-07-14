package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.service.interfaces.IDatabaseManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final IDatabaseManagementService databaseManagementService;

    @PostMapping("/reset-database")
    public ApiResponse<String> resetDatabase(
            @RequestParam String confirm,
            @AuthenticationPrincipal Jwt jwt) {
        
        Long userId = jwt != null && jwt.hasClaim("userId") 
                ? ((Number) Objects.requireNonNull(jwt.getClaim("userId"))).longValue() 
                : null;
                
        String result = databaseManagementService.resetDatabaseToDefault(confirm, userId);
        return ApiResponse.success(HttpStatus.OK.value(), result);
    }
}
