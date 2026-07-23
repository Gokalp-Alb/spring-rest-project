package com.springrest.springrestproject.service.implementations.redis;

import com.springrest.springrestproject.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class ScriptCacheServiceTest extends BaseIntegrationTest {

    @Autowired
    private ScriptCacheService cacheService;

    @Test
    void setThenGetReturnsTheValue() {
        cacheService.set("script:table:1:", "counter", 42, null);
        assertEquals(42, cacheService.get("script:table:1:", "counter"));
    }

    @Test
    void differentNamespacesDoNotCollide() {
        cacheService.set("script:table:1:", "counter", "table-1-value", null);
        cacheService.set("script:table:2:", "counter", "table-2-value", null);

        assertEquals("table-1-value", cacheService.get("script:table:1:", "counter"));
        assertEquals("table-2-value", cacheService.get("script:table:2:", "counter"));
    }

    @Test
    void deleteRemovesTheKey() {
        cacheService.set("script:table:1:", "toDelete", "value", null);
        cacheService.delete("script:table:1:", "toDelete");
        assertNull(cacheService.get("script:table:1:", "toDelete"));
    }
}
