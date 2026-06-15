package com.springrest.springrestproject.core.response;

public record ApiResponse<T>(int statusCode, ResponseOperation operation, T data) {
    public static <T> ApiResponse<T> success(int statusCode, ResponseOperation operation, T data) {
        return new ApiResponse<>(statusCode, operation, data);
    }
}