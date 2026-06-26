package com.springrest.springrestproject.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int statusCode,
        String successStatus,
        String errorMessage,
        T data,
        List<?> errors
) {

    public static <T> ApiResponse<T> success(int statusCode, T data) {
        return new ApiResponse<>(statusCode, "success", null, data,null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", null, data, null);
    }

    public static ApiResponse<Void> failure(int statusCode, String errorMessage) {
        return new ApiResponse<>(statusCode, "failure", errorMessage, null,null);
    }

    public static <T> ApiResponse<T> failure(int statusCode, String errorMessage, List<?> errors) {
        return new ApiResponse<>(statusCode, "failure", errorMessage, null, errors);
    }
}