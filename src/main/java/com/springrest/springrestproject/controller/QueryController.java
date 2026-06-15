package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.core.response.ResponseOperation;
import com.springrest.springrestproject.dto.request.query.SelectQueryRequest;
import com.springrest.springrestproject.security.CustomUserDetails;
import com.springrest.springrestproject.service.interfaces.IQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/queries")
@RequiredArgsConstructor
public class QueryController {

    private final IQueryService queryService;

    @PostMapping("/select")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<Map<String, Object>>> select(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody SelectQueryRequest request) {
        List<Map<String, Object>> results = queryService.executeSelect(request, userDetails.getId());
        return ApiResponse.success(HttpStatus.OK.value(), ResponseOperation.EXECUTE, results);
    }
}