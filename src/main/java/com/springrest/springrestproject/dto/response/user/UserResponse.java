package com.springrest.springrestproject.dto.response.user;

import com.springrest.springrestproject.model.user.Role;

public record UserResponse(
        Long id,
        String username,
        Role role) {}