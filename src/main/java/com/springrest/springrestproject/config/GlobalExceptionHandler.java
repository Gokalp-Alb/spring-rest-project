package com.springrest.springrestproject.config;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(ApplicationException ex) {
        var errorCode = ex.getErrorCode();
        var status = errorCode.getHttpStatus();
        String localizedMessage = messageSource.getMessage(
                errorCode.getMessageKey(),
                ex.getArgs(),
                LocaleContextHolder.getLocale()
        );
        ApiResponse<Void> apiResponse = ApiResponse.failure(
                status.value(),
                localizedMessage
        );
        return new ResponseEntity<>(apiResponse, status);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String messageKey = ErrorCode.BAD_REQUEST.getMessageKey();

        Throwable rootCause = ex.getRootCause();
        if (rootCause instanceof SQLException sqlException) {
            String sqlState = sqlException.getSQLState();
            if ("23505".equals(sqlState)) {
                messageKey = "error.duplicate_entry";
            }
        }
        String localizedMessage = messageSource.getMessage(
                messageKey,
                null,
                LocaleContextHolder.getLocale()
        );
        ApiResponse<Void> apiResponse = ApiResponse.failure(
                status.value(),
                localizedMessage
        );

        return new ResponseEntity<>(apiResponse, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String localizedMessage = messageSource.getMessage(
                ErrorCode.INTERNAL_SERVER_ERROR.getMessageKey(),
                null,
                LocaleContextHolder.getLocale()
        );
        String finalMessage = localizedMessage + " (" + ex.getMessage() + ")";
        ApiResponse<Void> apiResponse = ApiResponse.failure(status.value(), finalMessage);
        return new ResponseEntity<>(apiResponse, status);
    }
}