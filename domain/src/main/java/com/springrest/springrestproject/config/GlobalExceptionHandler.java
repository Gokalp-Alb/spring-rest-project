package com.springrest.springrestproject.config;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.exception.FieldValidationError;
import com.springrest.springrestproject.core.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final MessageSource messageSource;
    private static final Pattern DETAIL_PATTERN = Pattern.compile("Detail: Key \\((.*?)\\)=\\((.*?)\\) already exists\\.");

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(ApplicationException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = errorCode.getHttpStatus();
        String finalMessage = messageSource.getMessage(
                errorCode.getMessageKey(),
                ex.getArgs(),
                ex.getMessage(),
                LocaleContextHolder.getLocale()
        );
        
        String rawMessage = messageSource.getMessage(
                errorCode.getMessageKey(),
                null,
                ex.getMessage(),
                LocaleContextHolder.getLocale()
        );
        if (ex.getArgs() != null && ex.getArgs().length > 0 && (rawMessage == null || !rawMessage.contains("{0}"))) {
            assert finalMessage != null;
            StringBuilder sb = new StringBuilder(finalMessage.trim());
            for (Object arg : ex.getArgs()) {
                if (arg != null) {
                    sb.append(" ").append(arg);
                }
            }
            finalMessage = sb.toString();
        }
        
        ApiResponse<Void> apiResponse = ApiResponse.failure(status.value(), finalMessage, ex.getErrors());
        return new ResponseEntity<>(apiResponse, status);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof ValueInstantiationException valueInstantiationException) {
            if (valueInstantiationException.getCause() instanceof ApplicationException applicationException) {
                return handleApplicationException(applicationException);
            }
        }
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String localizedMessage = messageSource.getMessage(
                ErrorCode.BAD_REQUEST.getMessageKey(),
                null,
                LocaleContextHolder.getLocale()
        );
        ApiResponse<Void> apiResponse = ApiResponse.failure(status.value(), localizedMessage);
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
        String rawMessage = getDetailMessage(ex);
        List<FieldValidationError> errorsList = extractDuplicateErrors(rawMessage);

        ApiResponse<Void> apiResponse = ApiResponse.failure(
                status.value(),
                localizedMessage,
                errorsList
        );

        return new ResponseEntity<>(apiResponse, status);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKeyException(DuplicateKeyException ex) {
        HttpStatus status = ErrorCode.DUPLICATE_RESOURCE.getHttpStatus();

        String rawMessage = getDetailMessage(ex);
        String messageKey;
        if (rawMessage != null && (rawMessage.contains("table_metadata") || rawMessage.contains("uk9d25vd5jfwnaunlgdgw9e0qph"))) {
            messageKey = ErrorCode.DUPLICATE_TABLE_NAME.getMessageKey();
        } else {
            messageKey = ErrorCode.DUPLICATE_RESOURCE.getMessageKey();
        }

        String baseMessage = messageSource.getMessage(
                messageKey,
                null,
                LocaleContextHolder.getLocale()
        );
        List<FieldValidationError> errorsList = extractDuplicateErrors(rawMessage);

        ApiResponse<Void> apiResponse = ApiResponse.failure(status.value(), baseMessage, errorsList);
        return new ResponseEntity<>(apiResponse, status);
    }

    private String getDetailMessage(Throwable ex) {
        if (ex == null) {
            return null;
        }
        if (ex.getMessage() != null && ex.getMessage().contains("Detail:")) {
            return ex.getMessage();
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("Detail:")) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return ex.getMessage();
    }

    private List<FieldValidationError> extractDuplicateErrors(String rawMessage) {
        if (rawMessage != null) {
            Matcher matcher = DETAIL_PATTERN.matcher(rawMessage);
            if (matcher.find()) {
                String fieldName = matcher.group(1);
                String fieldValue = matcher.group(2);
                return List.of(new FieldValidationError(fieldName, null, fieldValue));
            }
        }
        return null;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED_ACCESS;
        HttpStatus status = errorCode.getHttpStatus();
        String localizedMessage = messageSource.getMessage(
                errorCode.getMessageKey(),
                null,
                LocaleContextHolder.getLocale()
        );
        ApiResponse<Void> apiResponse = ApiResponse.failure(status.value(), localizedMessage);
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