package com.springrest.scripting.model;

import java.util.Set;

public record ScriptCaller(String userId, Set<String> roles, CallerOrigin origin) {}
