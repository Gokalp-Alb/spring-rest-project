package com.springrest.springrestproject.dto.request.scripting;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ScriptUpdateRequest(
        @NotBlank @JsonProperty("script_body") String scriptBody
) {}
