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
    INVALID_REGEX_PATTERN_CREATION(HttpStatus.BAD_REQUEST, "error.invalid_regex_pattern_creation"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "error.duplicate_resource"),
    DUPLICATE_TABLE_NAME(HttpStatus.CONFLICT, "error.duplicate_table_name"),
    INVALID_OPERATOR(HttpStatus.BAD_REQUEST, "error.invalid_operator"),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "error.invalid_sort"),
    LOG_TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.log_table_not_found"),
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "error.invalid_date_format"),
    INVALID_RELATION_TYPE(HttpStatus.BAD_REQUEST, "error.invalid_relation_type"),
    INVALID_DELETE_POLICY(HttpStatus.BAD_REQUEST, "error.invalid_delete_policy"),
    RELATION_RESTRICT(HttpStatus.BAD_REQUEST, "error.relation_restrict"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.table_not_found"),
    ROW_NOT_FOUND(HttpStatus.NOT_FOUND, "error.row_not_found"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.user_not_found"),
    EMPTY_UPDATE_PAYLOAD(HttpStatus.BAD_REQUEST, "error.empty_update_payload"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.invalid_credentials"),
    USER_CONTEXT_INVALID(HttpStatus.BAD_REQUEST, "error.user_context_invalid"),
    SYSTEM_LOG_MUTATION_DENIED(HttpStatus.FORBIDDEN, "error.system_log_mutation_denied"),
    JUNCTION_TABLE_MUTATION_DENIED(HttpStatus.FORBIDDEN, "error.junction_table_mutation_denied"),
    RELATION_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "error.relation_already_exists"),
    JUNCTION_TABLE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "error.junction_table_already_exists"),
    INVALID_JUNCTION_TABLE(HttpStatus.BAD_REQUEST, "error.invalid_junction_table"),
    BLANK_TARGET_TABLE(HttpStatus.BAD_REQUEST, "error.blank_target_table"),
    RELATION_NOT_FOUND(HttpStatus.NOT_FOUND, "error.relation_not_found");

    private final HttpStatus httpStatus;
    private final String messageKey;

    ErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

}