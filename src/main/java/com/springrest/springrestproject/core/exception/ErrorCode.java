package com.springrest.springrestproject.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.resource_not_found"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "error.bad_request"),
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "error.unauthorized_access"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal_server_error"),
    ALREADY_DELETED(HttpStatus.BAD_REQUEST, "error.already_deleted"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "error.validation_failed"),
    INVALID_REGEX_PATTERN(HttpStatus.BAD_REQUEST, "error.invalid_regex_pattern"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "error.duplicate_resource"),
    DUPLICATE_TABLE_NAME(HttpStatus.CONFLICT, "error.duplicate_table_name"),
    INVALID_OPERATOR(HttpStatus.BAD_REQUEST, "error.invalid_operator"),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "error.invalid_sort"),
    LOG_TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.log_table_not_found"),
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "error.invalid_date_format");

    private final HttpStatus httpStatus;
    private final String messageKey;

    ErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

}