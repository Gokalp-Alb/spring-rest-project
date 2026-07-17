package com.springrest.scripting.proxy;

import com.springrest.scripting.model.ScriptExecutionContext;
import com.springrest.scripting.model.ScriptMethod;
import com.springrest.springrestproject.core.model.scripting.LogLevel;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

public class ConsoleLogProxy implements ProxyObject {
    private final ScriptExecutionContext ctx;
    private final Map<String, ScriptMethod> methods;

    public ConsoleLogProxy(ScriptExecutionContext ctx) {
        this.ctx = ctx;
        this.methods = Map.of(
            "info", new ScriptMethod("info", "Logs an informational message for this execution", "any", args -> log(LogLevel.INFO, args)),
            "log", new ScriptMethod("log", "Alias for console.info", "any", args -> log(LogLevel.INFO, args)),
            "warn", new ScriptMethod("warn", "Logs a warning message for this execution", "any", args -> log(LogLevel.WARN, args)),
            "error", new ScriptMethod("error", "Logs an error message for this execution", "any", args -> log(LogLevel.ERROR, args)),
            "debug", new ScriptMethod("debug", "Logs a debug message for this execution", "any", args -> log(LogLevel.DEBUG, args))
        );
    }

    private Object log(LogLevel level, Value... arguments) {
        String message = arguments.length > 0 ? ctx.converter().convert(arguments[0]) : "";
        ctx.logService().append(ctx.executionId(), level, message, null);
        return null;
    }

    @Override
    public Object getMember(String key) {
        return methods.get(key);
    }

    @Override
    public Object getMemberKeys() {
        return methods.keySet().toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return methods.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Console properties are read-only");
    }
}
