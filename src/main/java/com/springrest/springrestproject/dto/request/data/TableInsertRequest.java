package com.springrest.springrestproject.dto.request.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record TableInsertRequest(
        @NotBlank String tableName,
        @NotEmpty Map<String, Object> rowData
) {}