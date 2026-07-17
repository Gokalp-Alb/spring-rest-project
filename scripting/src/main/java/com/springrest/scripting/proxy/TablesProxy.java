package com.springrest.scripting.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springrest.scripting.model.ScriptExecutionContext;
import com.springrest.scripting.model.ScriptMethod;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.dto.request.query.QueryRequest;
import com.springrest.springrestproject.service.interfaces.IDataService;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public class TablesProxy implements ProxyObject {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IDataService dataService;
    private final ScriptExecutionContext context;
    private final Long userId;
    private final Map<String, ScriptMethod> methods;

    public TablesProxy(IDataService dataService, ScriptExecutionContext context, Long userId) {
        this.dataService = dataService;
        this.context = context;
        this.userId = userId;
        this.methods = Map.of(
            "select", new ScriptMethod("select", "Executes a select query on tables", "object", arguments -> {
                if (arguments.length == 0) {
                    throw new ApplicationException(ErrorCode.BAD_REQUEST, "select method requires a query argument");
                }
                try {
                    Object hostObj = context.converter().convertToHostObject(arguments[0]);
                    QueryRequest queryRequest = objectMapper.convertValue(hostObj, QueryRequest.class);
                    int page = queryRequest.page() != null ? queryRequest.page() : 0;
                    int size = queryRequest.size() != null ? queryRequest.size() : 10;
                    Pageable pageable = PageRequest.of(page, size);
                    return dataService.executeSelect(queryRequest, userId, pageable);
                } catch (ApplicationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ApplicationException(ErrorCode.BAD_REQUEST, "Failed to parse query: " + e.getMessage());
                }
            })
        );
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
        throw new UnsupportedOperationException("Tables properties are read-only");
    }
}
