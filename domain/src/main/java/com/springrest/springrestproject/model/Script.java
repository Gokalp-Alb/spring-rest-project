package com.springrest.springrestproject.model;

import com.springrest.springrestproject.core.model.scripting.ScriptType;
import lombok.Builder;

@Builder
public record Script(
    Long id,
    ScriptType scriptType,
    Long tableId,
    Long topicId,
    String scriptBody
) {}
