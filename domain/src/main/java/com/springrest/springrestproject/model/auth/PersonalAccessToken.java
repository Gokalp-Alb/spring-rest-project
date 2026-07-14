package com.springrest.springrestproject.model.auth;

import java.time.LocalDateTime;

public record PersonalAccessToken(
    Long id,
    String tokenHash,
    String name,
    Long userId,
    LocalDateTime expiresAt,
    LocalDateTime createdAt,
    LocalDateTime lastUsedAt
) {}
