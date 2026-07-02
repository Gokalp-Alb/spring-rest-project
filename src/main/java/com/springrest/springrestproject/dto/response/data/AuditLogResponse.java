package com.springrest.springrestproject.dto.response.data;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditLogResponse(
        String operationType,
        LocalDateTime executedAt,
        Long userId,
        Map<String, Object> rowData
) {}
