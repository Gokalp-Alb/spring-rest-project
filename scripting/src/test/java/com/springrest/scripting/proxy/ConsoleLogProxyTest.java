package com.springrest.scripting.proxy;

import com.springrest.scripting.model.ScriptExecutionContext;
import com.springrest.scripting.util.ValueToJsonConverter;
import com.springrest.springrestproject.core.model.scripting.LogLevel;
import com.springrest.springrestproject.service.implementations.ExecutionLogService;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConsoleLogProxyTest {

    @Test
    void info_appendsInfoLevelEntryWithConvertedMessage() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ScriptExecutionContext ctx = new ScriptExecutionContext("exec-1", logService, new ValueToJsonConverter());
        ConsoleLogProxy proxy = new ConsoleLogProxy(ctx);

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("console", proxy);
            context.eval("js", "console.info('hello')");
        }

        verify(logService).append("exec-1", LogLevel.INFO, "hello", null);
    }

    @Test
    void log_isTreatedAsInfoAlias() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ScriptExecutionContext ctx = new ScriptExecutionContext("exec-1", logService, new ValueToJsonConverter());
        ConsoleLogProxy proxy = new ConsoleLogProxy(ctx);

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("console", proxy);
            context.eval("js", "console.log('hi')");
        }

        verify(logService).append("exec-1", LogLevel.INFO, "hi", null);
    }

    @Test
    void warn_appendsWarnLevelEntryWithSerializedObject() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ScriptExecutionContext ctx = new ScriptExecutionContext("exec-1", logService, new ValueToJsonConverter());
        ConsoleLogProxy proxy = new ConsoleLogProxy(ctx);

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("console", proxy);
            context.eval("js", "console.warn({ code: 42 })");
        }

        verify(logService).append("exec-1", LogLevel.WARN, "{\"code\":42}", null);
    }

    @Test
    void error_appendsErrorLevelEntry() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ScriptExecutionContext ctx = new ScriptExecutionContext("exec-1", logService, new ValueToJsonConverter());
        ConsoleLogProxy proxy = new ConsoleLogProxy(ctx);

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("console", proxy);
            context.eval("js", "console.error('bad')");
        }

        verify(logService).append("exec-1", LogLevel.ERROR, "bad", null);
    }

    @Test
    void debug_appendsDebugLevelEntry() {
        ExecutionLogService logService = mock(ExecutionLogService.class);
        ScriptExecutionContext ctx = new ScriptExecutionContext("exec-1", logService, new ValueToJsonConverter());
        ConsoleLogProxy proxy = new ConsoleLogProxy(ctx);

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("console", proxy);
            context.eval("js", "console.debug('trace')");
        }

        verify(logService).append("exec-1", LogLevel.DEBUG, "trace", null);
    }

    @Test
    void putMember_throwsUnsupportedOperationException() {
        ScriptExecutionContext ctx = new ScriptExecutionContext("exec-1", mock(ExecutionLogService.class), new ValueToJsonConverter());
        ConsoleLogProxy proxy = new ConsoleLogProxy(ctx);

        assertThrows(UnsupportedOperationException.class, () -> proxy.putMember("info", null));
    }
}
