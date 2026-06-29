package com.springrest.springrestproject.dto.request.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ALLOWED_OPERATORS {
    EQUALS("=", false),
    NOT_EQUALS("!=", false),
    GREATER_THAN(">", false),
    LESS_THAN("<", false),
    GREATER_THAN_OR_EQUAL(">=", false),
    LESS_THAN_OR_EQUAL("<=", false),
    LIKE("LIKE", false),
    NOT_LIKE("NOT LIKE", false),
    BETWEEN("BETWEEN", true),
    BEFORE("BEFORE", true),
    AFTER("AFTER", true);

    private final String value;
    private final boolean logQueryOperator;

    @JsonCreator
    public static ALLOWED_OPERATORS fromValue(String text) {
        if (text == null) {
            return null;
        }
        String sanitizedInput = text.trim().toUpperCase().replace("_", "");
        for (ALLOWED_OPERATORS op : ALLOWED_OPERATORS.values()) {
            String exactEnumName = op.name().replace("_", "");
            String shortEnumName = op.name().replace("_THAN", "").replace("_", "");

            if (sanitizedInput.equals(exactEnumName) ||
                sanitizedInput.equals(shortEnumName) ||
                text.trim().equals(op.value)) {
                return op;
            }
        }
        String allowedValues = java.util.Arrays.stream(ALLOWED_OPERATORS.values())
                .map(ALLOWED_OPERATORS::name)
                .collect(java.util.stream.Collectors.joining(", "));

        throw new ApplicationException(ErrorCode.INVALID_OPERATOR, text, allowedValues);
    }
}
