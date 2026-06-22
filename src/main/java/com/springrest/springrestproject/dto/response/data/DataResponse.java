package com.springrest.springrestproject.dto.response.data;

import java.util.Map;

public record DataResponse(
        Long id,
        String tableName,
        String operation,
        Map<String, Object> rowData
) {}