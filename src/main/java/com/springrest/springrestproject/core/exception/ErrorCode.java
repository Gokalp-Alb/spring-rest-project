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
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "error.duplicate_resource");;

    private final HttpStatus httpStatus;
    private final String messageKey;

    ErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

}