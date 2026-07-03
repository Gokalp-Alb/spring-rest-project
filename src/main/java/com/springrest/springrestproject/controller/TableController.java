package com.springrest.springrestproject.controller;

import com.springrest.springrestproject.core.mapper.TableMapper;
import com.springrest.springrestproject.core.response.ApiResponse;
import com.springrest.springrestproject.dto.request.table.TableCreateRequest;
import com.springrest.springrestproject.dto.response.table.TableResponse;
import com.springrest.springrestproject.service.interfaces.IMetadataService;
import com.springrest.springrestproject.service.interfaces.IJsonSchemaGeneratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;


@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableController {

    private final IMetadataService metadataService;
    private final TableMapper tableMapper;
    private final IJsonSchemaGeneratorService jsonSchemaGeneratorService;


    @PostMapping("/{tableName}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TableResponse> createTable(@Valid @RequestBody TableCreateRequest request,
                                                  @AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable String tableName) {
        TableResponse response = tableMapper.toResponse(metadataService.createTable(tableName, request, jwt.getClaim("userId")));
        return ApiResponse.success(HttpStatus.CREATED.value(), response);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Page<TableResponse>> getAllTables(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<TableResponse> tables = metadataService.getAllTables(pageable);
        return ApiResponse.success(HttpStatus.OK.value(), tables);
    }

    @GetMapping("/id/{tableId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<TableResponse> getById(@PathVariable Long tableId) {
        TableResponse table = metadataService.getTableById(tableId);
        return ApiResponse.success(HttpStatus.OK.value(), table);
    }

    @GetMapping("/name/{tableName}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<TableResponse> getByName(@PathVariable String tableName) {
        TableResponse table = metadataService.getTableByName(tableName);
        return ApiResponse.success(HttpStatus.OK.value(), table);
    }

    @DeleteMapping("/{tableName}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<TableResponse> deleteTable(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tableName) {
        TableResponse tr = metadataService.deleteTableByName(tableName, jwt.getClaim("userId"));
        return ApiResponse.success(HttpStatus.OK.value(), tr);
    }

    @GetMapping("/jsonSchema/{tableName}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Map<String, Object>> getJsonSchema(@PathVariable String tableName) {
        Map<String, Object> schema = jsonSchemaGeneratorService.generateSchemaForTable(tableName);
        return ApiResponse.success(HttpStatus.OK.value(), schema);
    }
}