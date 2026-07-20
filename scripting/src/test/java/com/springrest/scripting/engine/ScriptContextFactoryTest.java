package com.springrest.scripting.engine;

import com.springrest.scripting.model.ScriptExecutionOptions;
import com.springrest.scripting.proxy.ConsoleLogProxy;
import com.springrest.scripting.proxy.TablesProxy;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptContextFactoryTest {

    @Test
    void evalWithTimeout_returnsResultWhenScriptFinishesInTime() {
        try (Context context = Context.newBuilder("js").build()) {
            Value result = ScriptContextFactory.evalWithTimeout(context, Source.create("js", "1 + 1"), 5000);
            assertEquals(2, result.asInt());
        }
    }

    @Test
    void evalWithTimeout_cancelsScriptThatExceedsTimeout() {
        Context context = Context.newBuilder("js").build();
        try {
            PolyglotException ex = assertThrows(PolyglotException.class, () ->
                    ScriptContextFactory.evalWithTimeout(context, Source.create("js", "while(true){}"), 200)
            );
            assertTrue(ex.isCancelled());
        } finally {
            // The watchdog already force-closed the context via close(true). GraalVM 24.0.2
            // deterministically rethrows PolyglotException(isCancelled()==true) from every
            // subsequent close() call on a context that was cancelled this way, so a plain
            // try-with-resources here would fail the test on its own implicit close().
            try {
                context.close();
            } catch (PolyglotException ignored) {
                // expected: context was already cancelled/closed by the watchdog
            }
        }
    }

    @Test
    void evalWithoutTimeout_doesNotCancelALongRunningScript() {
        try (Context context = Context.newBuilder("js").build()) {
            // A script that would be well past any short timeout if the watchdog ran;
            // evalWithoutTimeout must let it finish rather than cancelling it.
            Value result = ScriptContextFactory.evalWithoutTimeout(context, Source.create("js", "let sum = 0; for (let i = 0; i < 5000000; i++) { sum += i; } sum;"));
            assertTrue(result.fitsInLong());
        }
    }

    @Test
    void debugSession_rejectsASecondConcurrentAcquireUntilReleased() {
        // Deliberately does not go through createContext()/a real GraalVM Context: a real
        // debug-enabled Context starts an actual Chrome Inspector listener thread that
        // Context.close() does not appear to tear down, which hangs the JVM at shutdown
        // (observed directly: a single real debug Context created and closed in a test left
        // the forked test JVM alive, near-zero CPU, for 10+ minutes). Testing the atomic guard
        // in isolation proves the same acquire/reject/release semantics deterministically and
        // safely, without ever touching port 4242.
        ScriptExecutionOptions debugOptions = new ScriptExecutionOptions(5000L, true, 64);

        assertTrue(ScriptContextFactory.tryAcquireDebugSession());
        try {
            assertFalse(ScriptContextFactory.tryAcquireDebugSession());
        } finally {
            ScriptContextFactory.releaseDebugSession(debugOptions);
        }

        // After releasing, the slot must be acquirable again.
        assertTrue(ScriptContextFactory.tryAcquireDebugSession());
        ScriptContextFactory.releaseDebugSession(debugOptions);
    }

    @Test
    void isPortAvailable_returnsFalseWhenPortIsBoundByAnotherSocket() throws IOException {
        try (ServerSocket blocker = new ServerSocket(0)) {
            int port = blocker.getLocalPort();
            assertFalse(ScriptContextFactory.isPortAvailable(port));
        }
    }

    @Test
    void isPortAvailable_returnsTrueWhenPortIsFree() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        assertTrue(ScriptContextFactory.isPortAvailable(freePort));
    }

    @Test
    void createContext_rejectsDebugSessionWhenPortIsHeldByAnotherProcess() throws IOException {
        // Simulates cross-process contention: a raw socket (standing in for another JVM process,
        // or an orphaned leftover listener) holding port 4242 at the OS level, which the
        // in-process AtomicBoolean guard alone cannot see. createContext must reject this before
        // ever configuring GraalVM's "inspect" option or calling builder.build() - so this never
        // creates a real debug Context and cannot hang the test JVM.
        ScriptExecutionOptions debugOptions = new ScriptExecutionOptions(5000L, true, 64);
        ConsoleLogProxy console = new ConsoleLogProxy(null);
        TablesProxy tables = new TablesProxy(null, null, null);

        try (ServerSocket blocker = new ServerSocket(4242)) {
            ApplicationException ex = assertThrows(ApplicationException.class, () ->
                    ScriptContextFactory.createContext(console, tables, debugOptions)
            );
            assertEquals(ErrorCode.SCRIPT_DEBUG_SESSION_ALREADY_ACTIVE, ex.getErrorCode());
        }

        // The rejection must not leak the in-process guard: once the port is free again, a new
        // debug session must still be acquirable.
        assertTrue(ScriptContextFactory.tryAcquireDebugSession());
        ScriptContextFactory.releaseDebugSession(debugOptions);
    }
}
