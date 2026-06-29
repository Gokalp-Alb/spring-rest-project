package com.springrest.springrestproject.model.column;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;

public enum RelationType {
    ONE_TO_ONE;

    @JsonCreator
    public static RelationType fromValue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String sanitized = text.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (RelationType type : RelationType.values()) {
            if (type.name().equals(sanitized)) {
                return type;
            }
        }
        String allowed = java.util.Arrays.stream(RelationType.values())
                .map(RelationType::name)
                .collect(java.util.stream.Collectors.joining(", "));
        throw new ApplicationException(ErrorCode.INVALID_RELATION_TYPE, text, allowed);
    }
}
