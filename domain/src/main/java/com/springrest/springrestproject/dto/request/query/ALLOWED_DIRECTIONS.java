package com.springrest.springrestproject.dto.request.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ALLOWED_DIRECTIONS {
    ASC("ASC"),
    DESC("DESC");

    private final String value;

    @JsonCreator
    public static ALLOWED_DIRECTIONS fromValue(String text) {
        if (text == null) {
            return null;
        }
        String sanitizedInput = text.trim().toUpperCase();
        if (sanitizedInput.startsWith("ASC")) {
            return ASC;
        } else if (sanitizedInput.startsWith("DESC")) {
            return DESC;
        }
        String allowedValues = java.util.Arrays.stream(ALLOWED_DIRECTIONS.values())
                .map(ALLOWED_DIRECTIONS::name)
                .collect(java.util.stream.Collectors.joining(", "));

        throw new ApplicationException(ErrorCode.INVALID_SORT, text, allowedValues);
    }
}