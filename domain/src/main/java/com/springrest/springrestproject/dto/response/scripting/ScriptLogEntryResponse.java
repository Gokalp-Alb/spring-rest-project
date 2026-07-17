package com.springrest.springrestproject.dto.response.scripting;

import com.springrest.springrestproject.core.model.scripting.LogLevel;

import java.time.LocalDateTime;

public record ScriptLogEntryResponse(LogLevel level, String message, LocalDateTime loggedAt) {}
