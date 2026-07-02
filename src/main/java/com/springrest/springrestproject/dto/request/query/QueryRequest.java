package com.springrest.springrestproject.dto.request.query;

import java.util.List;

public record QueryRequest(
        String tableName,
        List<String> fields,
        List<Condition> conditions,
        List<Condition> audit,
        List<Sort> sorts,
        List<RelationQuery> relations
) {
    public QueryRequest(
            String tableName,
            List<String> fields,
            List<Condition> conditions,
            List<Condition> audit,
            List<Sort> sorts
    ) {
        this(tableName, fields, conditions, audit, sorts, List.of());
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

    public record RelationQuery(
            String relation
    ) {}
}
