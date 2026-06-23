package com.springrest.springrestproject.dto.request.query;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ALLOWED_OPERATORS {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    LESS_THAN("<"),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN_OR_EQUAL("<="),
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE");

    private final String value;
}
