package com.springrest.scripting.model;

public record ScriptExecutionOptions(long timeoutMs, boolean debugEnabled, int memoryLimitMb) {}
