package com.springrest.scripting.engine;

import com.springrest.scripting.model.ScriptExecutionOptions;
import com.springrest.scripting.proxy.ConsoleLogProxy;
import com.springrest.scripting.proxy.TablesProxy;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.Timer;
import java.util.TimerTask;

public class ScriptContextFactory {

    public static Context createContext(ConsoleLogProxy console, TablesProxy tables, ScriptExecutionOptions options) {
        // memoryLimitMb is intentionally unused: GraalVM's open-source Polyglot API
        // (org.graalvm.polyglot:polyglot) only exposes a statement-count ResourceLimits,
        // not a heap cap. Real per-context memory limits require GraalVM Enterprise.
        Context.Builder builder = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> false)
                .option("engine.WarnInterpreterOnly", "false");

        if (options.debugEnabled()) {
            builder.option("inspect", "4242");
        }

        Context context = builder.build();
        context.getBindings("js").putMember("console", console);
        context.getBindings("js").putMember("tables", tables);
        return context;
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
}
