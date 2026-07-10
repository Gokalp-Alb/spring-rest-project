package com.springrest.springrestproject.model.user;

import lombok.Builder;

@Builder
public record AppUser(
    Long id,
    String username,
    String password,
    Role role,
    Boolean active
) {}