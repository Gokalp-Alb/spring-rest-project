package com.springrest.springrestproject.model.relation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum RelationType {
    ONE_TO_ONE,
    MANY_TO_ONE,
    MANY_TO_MANY;

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
        String allowed = Arrays.stream(RelationType.values())
                .map(RelationType::name)
                .collect(Collectors.joining(", "));
        throw new ApplicationException(ErrorCode.INVALID_RELATION_TYPE, text, allowed);
    }
}
