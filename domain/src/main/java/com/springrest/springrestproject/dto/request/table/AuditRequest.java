package com.springrest.springrestproject.dto.request.table;

import com.springrest.springrestproject.model.table.TableMetadata;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
public record AuditRequest(
    Long recordId,
    String operationType,
    LocalDateTime executedAt,
    Long userId,
    Map<String, Object> rowData,
    TableMetadata tableMetadata
) {}
