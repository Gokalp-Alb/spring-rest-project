package com.springrest.springrestproject.service.implementations.redis;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ScriptCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public Object get(String namespace, String key) {
        try {
            return redisTemplate.opsForValue().get(namespace + key);
        } catch (RedisConnectionFailureException e) {
            throw new ApplicationException(ErrorCode.SCRIPT_CACHE_FAILED, e.getMessage());
        }
    }

    public void set(String namespace, String key, Object value, Long ttlSeconds) {
        try {
            String fullKey = namespace + key;
            redisTemplate.opsForValue().set(fullKey, value);
            if (ttlSeconds != null) {
                redisTemplate.expire(fullKey, Duration.ofSeconds(ttlSeconds));
            }
        } catch (RedisConnectionFailureException e) {
            throw new ApplicationException(ErrorCode.SCRIPT_CACHE_FAILED, e.getMessage());
        }
    }

    public void delete(String namespace, String key) {
        try {
            redisTemplate.delete(namespace + key);
        } catch (RedisConnectionFailureException e) {
            throw new ApplicationException(ErrorCode.SCRIPT_CACHE_FAILED, e.getMessage());
        }
    }
}
