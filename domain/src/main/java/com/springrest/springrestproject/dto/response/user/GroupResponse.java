package com.springrest.springrestproject.dto.response.user;

import com.springrest.springrestproject.model.user.GroupName;

import java.time.LocalDateTime;

public record GroupResponse(
        Long id,
        Long userId,
        GroupName groupName,
        LocalDateTime createdDate
) {}
