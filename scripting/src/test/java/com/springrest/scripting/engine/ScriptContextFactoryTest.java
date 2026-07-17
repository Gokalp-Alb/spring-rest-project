package com.springrest.scripting.engine;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
