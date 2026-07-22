package com.springrest.springrestproject.service.implementations.redis;

import com.springrest.springrestproject.model.table.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableMetadataCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public Optional<TableMetadata> get(String tableName) {
        try {
            Object cached = redisTemplate.opsForValue().get("table-metadata:" + tableName);
            return Optional.ofNullable((TableMetadata) cached);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skipping cache lookup for {}", tableName);
            return Optional.empty();
        }
    }

    public void put(TableMetadata metadata) {
        try {
            redisTemplate.opsForValue().set(
                    "table-metadata:" + metadata.tableName(),
                    metadata,
                    Duration.ofMinutes(30)
            );
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skipping cache write for {}", metadata.tableName());
        }
    }

    public void evict(String tableName) {
        try {
            redisTemplate.delete("table-metadata:" + tableName);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skipping cache eviction for {}", tableName);
        }
    }

    public void evictAll() {
        try {
            var keys = redisTemplate.keys("table-metadata:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skipping full cache eviction");
        }
    }
}