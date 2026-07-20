package com.springrest.scripting.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import org.graalvm.polyglot.Value;

import java.util.*;

public class ValueToJsonConverter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String convert(Value value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        try {
            Set<Value> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            Object hostObject = toHostObject(value, visited);
            if (hostObject instanceof Number || hostObject instanceof Boolean) {
                return String.valueOf(hostObject);
            }
            return objectMapper.writeValueAsString(hostObject);
        } catch (Exception e) {
            throw new ApplicationException(ErrorCode.SCRIPT_QUERY_FAILED);
        }
    }

    public Object convertToHostObject(Value value) {
        Set<Value> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return toHostObject(value,visited);
    }

    private Object toHostObject(Value value, Set<Value> visited) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.as(Number.class);
        }
        if (value.isDate()) {
            if (value.isTime()) {
                return value.asInstant();
            }
            return value.asDate();
        }
        if (value.isTime()) {
            return value.asTime();
        }


        if (!visited.add(value)) {
            return null;
        }
        try {
            if (value.hasArrayElements()) {
                long size = value.getArraySize();
                List<Object> list = new ArrayList<>((int) size);
                for (long i = 0; i < size; i++) {
                    list.add(toHostObject(value.getArrayElement(i), visited));
                }
                return list;
            }
            if (value.hasMembers()) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (String key : value.getMemberKeys()) {
                    map.put(key, toHostObject(value.getMember(key), visited));
                }
                return map;
            }
            return value.toString();
        } finally {
            visited.remove(value);
        }
    }
}
