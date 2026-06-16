package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.core.response.ResponseOperation;
import com.springrest.springrestproject.core.mapper.TableMapper;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.security.CustomUserDetails;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableController {

    private final IMetadataService metadataService;
    private final TableMapper tableMapper;


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TableResponse> createTable(@Valid @RequestBody TableCreateRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        TableResponse response = tableMapper.toResponse(metadataService.createTable(request, userDetails.getId()));
        return ApiResponse.success(HttpStatus.CREATED.value(), ResponseOperation.CREATE, response);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Page<TableResponse>> getAllTables(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<TableResponse> tables = metadataService.getAllTables(pageable);
        return ApiResponse.success(HttpStatus.OK.value(), ResponseOperation.READ, tables);
    }

    @DeleteMapping("/{tableName}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> deleteTable(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String tableName) {
        metadataService.deleteTableByName(tableName, userDetails.getId());
        return ApiResponse.success(HttpStatus.OK.value(), ResponseOperation.DELETE, null);
    }
}