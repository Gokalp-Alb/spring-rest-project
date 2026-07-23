package com.springrest.scripting.proxy;

import com.springrest.scripting.model.ScriptMethod;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.service.implementations.redis.ScriptCacheService;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

public class CacheProxy implements ProxyObject {
    private final ScriptCacheService cacheService;
    private final String namespace;
    private final Map<String, ScriptMethod> methods;

    public CacheProxy(ScriptCacheService cacheService, String namespace) {
        this.cacheService = cacheService;
        this.namespace = namespace;
        this.methods = Map.of(
            "get", new ScriptMethod("get", "Reads a value from the script's namespaced cache", "any", this::get),
            "set", new ScriptMethod("set", "Writes a value to the script's namespaced cache, with an optional TTL in seconds", "any", this::set),
            "delete", new ScriptMethod("delete", "Removes a key from the script's namespaced cache", "any", this::delete)
        );
    }

    private Object get(Value... arguments) {
        if (arguments.length == 0) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "cache.get requires a key argument");
        }
        try {
            return cacheService.get(namespace, arguments[0].asString());
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException(ErrorCode.SCRIPT_CACHE_FAILED, e.getMessage());
        }
    }

    private Object set(Value... arguments) {
        if (arguments.length < 2) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "cache.set requires key and value arguments");
        }
        try {
            String key = arguments[0].asString();
            Object value = arguments[1].as(Object.class);
            Long ttlSeconds = (arguments.length > 2 && !arguments[2].isNull()) ? arguments[2].asLong() : null;
            cacheService.set(namespace, key, value, ttlSeconds);
            return null;
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException(ErrorCode.SCRIPT_CACHE_FAILED, e.getMessage());
        }
    }

    private Object delete(Value... arguments) {
        if (arguments.length == 0) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "cache.delete requires a key argument");
        }
        try {
            cacheService.delete(namespace, arguments[0].asString());
            return null;
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException(ErrorCode.SCRIPT_CACHE_FAILED, e.getMessage());
        }
    }

    @Override
    public Object getMember(String key) {
        return methods.get(key);
    }

    @Override
    public Object getMemberKeys() {
        return methods.keySet().toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return methods.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Cache properties are read-only");
    }
}
