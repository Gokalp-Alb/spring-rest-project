package com.springrest.scripting.engine;

import com.springrest.scripting.model.ScriptExecutionOptions;
import com.springrest.scripting.proxy.ConsoleLogProxy;
import com.springrest.scripting.proxy.TablesProxy;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScriptContextFactory {

    private static final int DEBUG_PORT = 4242;

    // GraalVM's inspect-community tool does not throw when a second Context binds the same
    // inspector port while a first session already holds it - the second build()/eval() call
    // silently succeeds with a non-functional listener (verified by direct reproduction against
    // inspect-community 24.0.2). Enforcing "one fixed port, one debug session at a time"
    // therefore has to happen at this application level, not by catching a GraalVM exception
    // that is never thrown. This guard is per-JVM-process only (a static field): the API app,
    // mcp-server, and sandbox-mcp are separate processes, each with their own copy of it. A raw
    // TCP bind probe (below) additionally catches the cross-process case - another process (or
    // an orphaned leftover listener) already holding DEBUG_PORT at the OS level - which this
    // in-memory flag alone cannot see.
    private static final AtomicBoolean DEBUG_SESSION_ACTIVE = new AtomicBoolean(false);

    public static Context createContext(ConsoleLogProxy console, TablesProxy tables, ScriptExecutionOptions options) {
        // memoryLimitMb is intentionally unused: GraalVM's open-source Polyglot API
        // (org.graalvm.polyglot:polyglot) only exposes a statement-count ResourceLimits,
        // not a heap cap. Real per-context memory limits require GraalVM Enterprise.
        Context.Builder builder = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> false)
                .option("engine.WarnInterpreterOnly", "false");

        if (options.debugEnabled()) {
            if (!tryAcquireDebugSession()) {
                throw new ApplicationException(ErrorCode.SCRIPT_DEBUG_SESSION_ALREADY_ACTIVE,
                        "A debug session is already active on port " + DEBUG_PORT);
            }
            if (!isPortAvailable(DEBUG_PORT)) {
                releaseDebugSession(options);
                throw new ApplicationException(ErrorCode.SCRIPT_DEBUG_SESSION_ALREADY_ACTIVE,
                        "Port " + DEBUG_PORT + " is already in use by another process");
            }
            builder.option("inspect", String.valueOf(DEBUG_PORT));
        }

        Context context;
        try {
            context = builder.build();
        } catch (RuntimeException e) {
            if (options.debugEnabled()) {
                DEBUG_SESSION_ACTIVE.set(false);
            }
            throw e;
        }
        context.getBindings("js").putMember("console", console);
        context.getBindings("js").putMember("tables", tables);
        return context;
    }

    // Package-private so ScriptContextFactoryTest can verify the guard's acquire/reject/release
    // semantics directly, without going through a real GraalVM Context. A real debug-enabled
    // Context starts an actual Chrome Inspector listener thread that Context.close() does not
    // appear to tear down, which hangs the JVM at shutdown (observed directly: a single real
    // debug Context created and closed in a test left the forked test JVM alive, near-zero CPU,
    // for 10+ minutes) - so tests must never create one.
    static boolean tryAcquireDebugSession() {
        return DEBUG_SESSION_ACTIVE.compareAndSet(false, true);
    }

    // Package-private for the same reason as tryAcquireDebugSession: lets the test bind the
    // real port itself to simulate another process holding it, without ever engaging GraalVM's
    // inspector.
    static boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Must be called exactly once per createContext() call made with debugEnabled=true, after
    // the context is done being used (whether it succeeded, failed, or timed out), so the next
    // debug session can acquire the slot. Safe/no-op to call when debugEnabled=false.
    public static void releaseDebugSession(ScriptExecutionOptions options) {
        if (options.debugEnabled()) {
            DEBUG_SESSION_ACTIVE.set(false);
        }
    }

    public static Value evalWithTimeout(Context context, Source source, long timeoutMs) {
        Timer watchdog = new Timer(true);
        watchdog.schedule(new TimerTask() {
            @Override
            public void run() {
                context.close(true);
            }
        }, timeoutMs);
        try {
            return context.eval(source);
        } finally {
            watchdog.cancel();
        }
    }

    public static Value evalWithoutTimeout(Context context, Source source) {
        return context.eval(source);
    }
}
