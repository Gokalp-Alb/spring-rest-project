package com.springrest.springrestproject.service.implementations.redis;

import com.springrest.springrestproject.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RateLimiterServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        assert redisTemplate.getConnectionFactory() != null;
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void shouldAllowRequestsUnderLimit() {
        String testIp = "rate:limit:10.0.0.1";
        int maxLimit = 5;
        int window = 60;

        for (int i = 0; i < maxLimit; i++) {
            boolean isAllowed = rateLimiterService.isAllowed(testIp, maxLimit, window);
            assertTrue(isAllowed, "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void shouldBlockRequestsOverLimit() {
        String testIp = "rate:limit:10.0.0.2";
        int maxLimit = 3;
        int window = 60;

        rateLimiterService.isAllowed(testIp, maxLimit, window);
        rateLimiterService.isAllowed(testIp, maxLimit, window);
        rateLimiterService.isAllowed(testIp, maxLimit, window);

        boolean isAllowed = rateLimiterService.isAllowed(testIp, maxLimit, window);
        assertFalse(isAllowed, "The 4th request should be blocked as it exceeds the limit");
    }

    @Test
    void shouldResetLimitAfterWindowExpires() throws InterruptedException {
        String testIp = "rate:limit:10.0.0.3";
        int maxLimit = 1;
        int window = 1;

        assertTrue(rateLimiterService.isAllowed(testIp, maxLimit, window));
        assertFalse(rateLimiterService.isAllowed(testIp, maxLimit, window));

        Thread.sleep(1100);

        assertTrue(rateLimiterService.isAllowed(testIp, maxLimit, window));
    }
}