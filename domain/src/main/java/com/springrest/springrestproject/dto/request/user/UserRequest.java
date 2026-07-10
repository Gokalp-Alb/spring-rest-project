package com.springrest.springrestproject.dto.request.user;

import com.springrest.springrestproject.model.user.Role;

public record UserRequest(
        Long id,
        String username,
        Role role,
        String password
) {}