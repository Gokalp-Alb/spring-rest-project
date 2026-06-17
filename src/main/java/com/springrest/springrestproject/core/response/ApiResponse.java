package com.springrest.springrestproject.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int statusCode,
        String successStatus,
        String errorMessage,
        T data) {

    public static <T> ApiResponse<T> success(int statusCode, T data) {
        return new ApiResponse<>(statusCode, "success", null, data);
    }

    public static ApiResponse<Void> failure(int statusCode, String errorMessage) {
        return new ApiResponse<>(statusCode, "failure", errorMessage, null);
    }
}