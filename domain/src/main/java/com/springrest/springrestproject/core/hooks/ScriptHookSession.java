package com.springrest.springrestproject.core.hooks;

public interface ScriptHookSession extends AutoCloseable {
    void invokeIfDefined(String functionName);

    @Override
    void close();
}
