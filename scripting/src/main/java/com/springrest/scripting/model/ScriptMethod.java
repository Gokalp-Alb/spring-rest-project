package com.springrest.scripting.model;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

public record ScriptMethod(
    String name,
    String description,
    String paramSchema,
    ProxyExecutable handler
) implements ProxyExecutable {
    @Override
    public Object execute(Value... arguments) {
        return handler.execute(arguments);
    }
}

