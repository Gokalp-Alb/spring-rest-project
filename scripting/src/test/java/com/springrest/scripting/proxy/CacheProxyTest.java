package com.springrest.scripting.proxy;

import com.springrest.springrestproject.service.implementations.redis.ScriptCacheService;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CacheProxyTest {

    @Test
    void getSetDeleteDelegateWithNamespacePrefix() {
        ScriptCacheService cacheService = mock(ScriptCacheService.class);
        CacheProxy proxy = new CacheProxy(cacheService, "script:table:1:");

        try (Context context = Context.newBuilder("js").allowHostAccess(org.graalvm.polyglot.HostAccess.ALL).build()) {
            context.getBindings("js").putMember("cache", proxy);

            when(cacheService.get("script:table:1:", "counter")).thenReturn(42);
            Object result = context.eval("js", "cache.get('counter')").as(Object.class);
            assertEquals(42, result);

            context.eval("js", "cache.set('counter', 43)");
            verify(cacheService).set(eq("script:table:1:"), eq("counter"), eq(43), isNull());

            context.eval("js", "cache.set('counter', 43, 60)");
            verify(cacheService).set(eq("script:table:1:"), eq("counter"), eq(43), eq(60L));

            context.eval("js", "cache.delete('counter')");
            verify(cacheService).delete("script:table:1:", "counter");
        }
    }
}
