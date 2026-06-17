package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.dto.request.data.TableInsertRequest;
import com.springrest.springrestproject.dto.request.query.SelectQueryRequest;
import com.springrest.springrestproject.security.CustomUserDetails;
import com.springrest.springrestproject.service.interfaces.IDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DataController {

    private final IDataService dataService;

    @PostMapping("/data/insert")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> insert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TableInsertRequest request) {

        dataService.insertRow(request, userDetails.getId());

        return ApiResponse.success(HttpStatus.CREATED.value(), null);
    }

    @PostMapping("/queries/select")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<Map<String, Object>>> select(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody SelectQueryRequest request,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        List<Map<String, Object>> results = dataService.executeSelect(request, userDetails.getId(), pageable);
        return ApiResponse.success(HttpStatus.OK.value(), results);
    }

    @GetMapping("/data/{tableName}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Page<Map<String, Object>>> getTableData(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String tableName,
            @RequestParam(name = "show_sensitive", required = false) Boolean showSensitive,
            @PageableDefault Pageable pageable) {
        Page<Map<String, Object>> paginatedData = dataService.getTableData(
                tableName, showSensitive, pageable, userDetails.getId());
        return ApiResponse.success(HttpStatus.OK.value(), paginatedData);
    }
}