package com.springrest.springrestproject.dto.request.auth;

public record PatCreationRequest(
    String username,
    String password,
    Integer expirationDays,
    String tokenName
) {}
