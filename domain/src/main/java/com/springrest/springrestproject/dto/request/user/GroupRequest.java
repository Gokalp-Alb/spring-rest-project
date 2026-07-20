package com.springrest.springrestproject.dto.request.user;

import com.springrest.springrestproject.model.user.GroupName;

public record GroupRequest(
        GroupName groupName
) {}
