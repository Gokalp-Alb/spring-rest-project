package com.springrest.springrestproject.core.exception;

import lombok.Getter;
import java.util.List;

@Getter
public class ApplicationException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object[] args;
    private final List<FieldValidationError> errors;

    public ApplicationException(ErrorCode errorCode) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = new Object[0];
        this.errors = null;
    }

    public ApplicationException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = args;
        this.errors = null;
    }

    public ApplicationException(ErrorCode errorCode, List<FieldValidationError> errors) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = new Object[0];
        this.errors = errors;
    }

    public ApplicationException(ErrorCode errorCode, List<FieldValidationError> errors, Object... args) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = args;
        this.errors = errors;
    }

}