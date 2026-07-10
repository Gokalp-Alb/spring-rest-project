package com.springrest.springrestproject.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldValidationError(
        String field,
        String reason,
        String value
) {
    public FieldValidationError(String field, String reason) {
        this(field, reason, null);
    }
}