package com.springrest.springrestproject.model.user;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UserGroup(
        Long id,
        Long userId,
        GroupName groupName,
        Long creatorId,
        LocalDateTime createdDate,
        Long lastUpdaterId,
        LocalDateTime lastChangedDate
) {}
