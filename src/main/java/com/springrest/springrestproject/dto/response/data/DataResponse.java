package com.springrest.springrestproject.dto.response.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataResponse(
        Long id,
        String tableName,
        String operation,
        Map<String, Object> rowData
) {}