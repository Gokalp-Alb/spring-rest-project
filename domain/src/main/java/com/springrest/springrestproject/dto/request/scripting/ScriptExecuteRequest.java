package com.springrest.springrestproject.dto.request.scripting;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScriptExecuteRequest(
        String script,
        @JsonProperty("debug_enabled") Boolean debugEnabled
) {}
