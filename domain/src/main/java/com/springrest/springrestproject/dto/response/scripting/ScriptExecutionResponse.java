package com.springrest.springrestproject.dto.response.scripting;

import java.util.List;

public record ScriptExecutionResponse(Object result, List<ScriptLogEntryResponse> logs) {}
