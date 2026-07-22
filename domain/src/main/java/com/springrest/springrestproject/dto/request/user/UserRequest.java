package com.springrest.springrestproject.dto.request.user;

import com.springrest.springrestproject.model.user.GroupName;

import java.util.List;

public record UserRequest(
        Long id,
        String username,
        List<GroupName> groups,
        String password
) {}
