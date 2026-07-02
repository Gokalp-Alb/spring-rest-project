package com.springrest.springrestproject.service.implementations.redis;

import com.springrest.springrestproject.model.column.ColumnMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationCacheService {
    private final RedisTemplate<String, Object> redisTemplate;

    public Optional<List<ColumnMetadata>> get(String tableName) {
        try {
            Object cached = redisTemplate.opsForValue().get("incoming-fks:" + tableName);
            return Optional.ofNullable((List<ColumnMetadata>) cached);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skipping cache lookup for {}", tableName);
            return Optional.empty();
        }
    }

    public void put(String tableName, List<ColumnMetadata> columns) {
        try {
            redisTemplate.opsForValue().set("incoming-fks:" + tableName, columns, Duration.ofMinutes(10));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skipping cache write for {}", tableName);
        }
    }

    public void evict(String tableName) {
        try {
            redisTemplate.delete("incoming-fks:" + tableName);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skipping cache eviction for {}", tableName);
        }
    }

}
