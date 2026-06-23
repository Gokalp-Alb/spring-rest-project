package com.springrest.springrestproject.service.implementations.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript = createScript();

    private static DefaultRedisScript<Long> createScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local key = KEYS[1] " +
                        "local limit = tonumber(ARGV[1]) " +
                        "local window = tonumber(ARGV[2]) " +
                        "local count = redis.call('INCR', key) " +
                        "if count == 1 then " +
                        "  redis.call('EXPIRE', key, window) " +
                        "end " +
                        "return count"
        );
        script.setResultType(Long.class);
        return script;
    }

    public boolean isAllowed(String key, int maxLimit, int windowInSeconds) {
        try {
            Long currentCount = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(maxLimit),
                    String.valueOf(windowInSeconds)
            );
            return currentCount != null && currentCount <= maxLimit;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failed. Bypassing rate limiter for key: {}. Error: {}", key, e.getMessage());
            return true;
        } catch (Exception e) {
            log.error("Unexpected error executing rate limit script. Bypassing. Error: {}", e.getMessage());
            return true;
        }
    }
}