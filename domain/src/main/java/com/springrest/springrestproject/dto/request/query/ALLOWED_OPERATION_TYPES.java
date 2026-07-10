package com.springrest.springrestproject.dto.request.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ALLOWED_OPERATION_TYPES {
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE");

    private final String value;

    @JsonCreator
    public static ALLOWED_OPERATION_TYPES fromValue(String text) {
        if (text == null) {
            return null;
        }
        String sanitizedInput = text.trim().toUpperCase();
        for (ALLOWED_OPERATION_TYPES op : ALLOWED_OPERATION_TYPES.values()) {
            if (sanitizedInput.equals(op.name()) || text.trim().equalsIgnoreCase(op.value)) {
                return op;
            }
        }
        
        String allowedValues = java.util.Arrays.stream(ALLOWED_OPERATION_TYPES.values())
                .map(ALLOWED_OPERATION_TYPES::name)
                .collect(java.util.stream.Collectors.joining(", "));

        throw new ApplicationException(ErrorCode.INVALID_OPERATOR, text, allowedValues);
    }
}
