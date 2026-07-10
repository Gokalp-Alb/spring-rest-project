package com.springrest.springrestproject.dto.request.query;

import java.util.List;
import java.util.Map;

public record QueryRequest(
        String tableName,
        List<String> fields,
        List<Condition> conditions,
        List<Condition> audit,
        List<Sort> sorts,
        Map<String, QueryRequest> relations,
        Integer page,
        Integer size
) {
    public QueryRequest(
            String tableName,
            List<String> fields,
            List<Condition> conditions,
            List<Condition> audit,
            List<Sort> sorts
    ) {
        this(tableName, fields, conditions, audit, sorts, Map.of(), null, null);
    }
    
    public QueryRequest(
            String tableName,
            List<String> fields,
            List<Condition> conditions,
            List<Condition> audit,
            List<Sort> sorts,
            Map<String, QueryRequest> relations
    ) {
        this(tableName, fields, conditions, audit, sorts, relations, null, null);
    }

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
