package com.springrest.springrestproject.dto.response.data;

import java.util.List;
import java.util.Map;

public record QueryResponse(
        List<Map<String, Object>> data,
        List<AuditLogResponse> auditData
) {}
