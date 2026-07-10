package com.springrest.springrestproject.core.aspect;

import com.springrest.springrestproject.core.annotation.PersistenceCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PersistenceCacheAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(persistenceCache)")
    public Object aroundCache(ProceedingJoinPoint pjp, PersistenceCache persistenceCache) throws Throwable {
        String key = generateKey(pjp, persistenceCache);

        try {
            Object cachedData = redisTemplate.opsForValue().get(key);
            if (cachedData != null) {
                // If the original method returns Optional, we must wrap it back into Optional
                MethodSignature signature = (MethodSignature) pjp.getSignature();
                if (Optional.class.isAssignableFrom(signature.getReturnType())) {
                    return Optional.of(cachedData);
                }
                return cachedData;
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, skipping cache read for {}", key);
        }

        Object result = pjp.proceed();

        Object dataToCache = result;
        if (result instanceof Optional<?> opt) {
            dataToCache = opt.orElse(null);
        }

        if (dataToCache != null) {
            try {
                redisTemplate.opsForValue().set(key, dataToCache, Duration.ofMinutes(30));
            } catch (RedisConnectionFailureException e) {
                log.warn("Redis unavailable, skipping cache write for {}", key);
            }
        }

        return result;
    }

    private String generateKey(ProceedingJoinPoint pjp, PersistenceCache persistenceCache) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] paramNames = nameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = pjp.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        String evaluatedTableName = parser.parseExpression(persistenceCache.tableName()).getValue(context, String.class);
        return persistenceCache.cacheName() + ":" + evaluatedTableName;
    }
}
