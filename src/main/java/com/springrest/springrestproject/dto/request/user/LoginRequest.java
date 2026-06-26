package com.springrest.springrestproject.dto.request.user;

public record LoginRequest(
        String username,
        String password
) {}