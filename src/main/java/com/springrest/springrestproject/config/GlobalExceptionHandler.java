package com.springrest.springrestproject.config;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(ApplicationException ex) {
        var errorCode = ex.getErrorCode();
        var status = errorCode.getHttpStatus();

        ApiResponse<Void> apiResponse = ApiResponse.failure(
                status.value(),
                ex.getMessage()
        );

        return new ResponseEntity<>(apiResponse, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        ApiResponse<Void> apiResponse = ApiResponse.failure(
                status.value(),
                "An unexpected server error occurred: " + ex.getMessage()
        );

        return new ResponseEntity<>(apiResponse, status);
    }
}