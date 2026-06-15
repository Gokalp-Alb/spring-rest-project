package com.springrest.springrestproject.dto.request.query;

import java.util.List;

public record SelectQueryRequest(
        String tableName,
        List<String> fields,
        String whereColumn,
        String whereValue
) {}
