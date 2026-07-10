package com.springrest.springrestproject.dto.request.relation;

import jakarta.validation.constraints.NotNull;

public record ManyToManyInsertRequest(
        @NotNull(message = "First table ID cannot be null") Long firstTableId,
        @NotNull(message = "Second table ID cannot be null") Long secondTableId
) {}
