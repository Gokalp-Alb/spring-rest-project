package com.springrest.springrestproject.dto.request.query;

import java.util.List;

public record QueryRequest(
        String tableName,
        List<String> fields,
        List<Condition> conditions,
        List<Sort> sorts
) {
    public record Condition(
            String column,
            ALLOWED_OPERATORS operator,
            Object value
    ) {}

    public record Sort(
            String column,
            ALLOWED_DIRECTIONS direction
    ) {}
}
