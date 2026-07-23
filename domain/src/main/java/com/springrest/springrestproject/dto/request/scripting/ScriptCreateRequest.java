package com.springrest.springrestproject.dto.request.scripting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScriptCreateRequest(
        @NotNull @JsonProperty("script_type") ScriptType scriptType,
        @JsonProperty("table_id") Long tableId,
        @JsonProperty("topic_id") Long topicId,
        @NotBlank @JsonProperty("script_body") String scriptBody
) {}
