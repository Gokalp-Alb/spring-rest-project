package com.springrest.springrestproject.model.column;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ValidRegexPatterns {
    EMAIL("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"),
    PHONE("^\\+?[0-9]+$");

    private final String pattern;

    @JsonCreator
    public static ValidRegexPatterns fromValue(String text) {
        if (text == null) {
            return null;
        }
        String sanitizedInput = text.trim().toUpperCase().replace("_", "");
        for (ValidRegexPatterns p : ValidRegexPatterns.values()) {
            String exactEnumName = p.name().replace("_", "");
            if (sanitizedInput.equals(exactEnumName) || text.trim().equals(p.pattern)) {
                return p;
            }
        }

        String allowedValues = java.util.Arrays.stream(ValidRegexPatterns.values())
                .map(ValidRegexPatterns::name)
                .collect(java.util.stream.Collectors.joining(", "));

        throw new ApplicationException(ErrorCode.INVALID_REGEX_PATTERN_CREATION, text, allowedValues);
    }
}
