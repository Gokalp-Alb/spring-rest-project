package com.springrest.scripting.engine;

import com.springrest.scripting.model.ScriptCaller;
import com.springrest.scripting.model.ScriptExecutionContext;
import com.springrest.scripting.model.ScriptExecutionOptions;
import com.springrest.scripting.proxy.ConsoleLogProxy;
import com.springrest.scripting.proxy.TablesProxy;
import com.springrest.scripting.util.ValueToJsonConverter;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.model.scripting.ExecutionLogEntry;
import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.dto.response.scripting.ScriptExecutionResponse;
import com.springrest.springrestproject.dto.response.scripting.ScriptLogEntryResponse;
import com.springrest.springrestproject.service.implementations.ExecutionLogService;
import com.springrest.springrestproject.service.interfaces.IDataService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScriptExecutionService {
    private final ExecutionLogService logService;
    private final IDataService dataService;
    private final ScriptExecutionProperties executionProperties;

    public ScriptExecutionService(ExecutionLogService logService, IDataService dataService, ScriptExecutionProperties executionProperties) {
        this.logService = logService;
        this.dataService = dataService;
        this.executionProperties = executionProperties;
    }

    public ScriptExecutionResponse execute(String script, ScriptCaller caller) {
        if (caller == null || caller.roles() == null) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Caller roles are required");
        }

        boolean isAuthorized = true; //TODO write a role based system to check authorization
        if (!isAuthorized) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS, "Caller does not have SCRIPT_ENGINEER role");
        }

        if (script == null) {
            throw new ApplicationException(ErrorCode.SCRIPT_INVALID_PAYLOAD, "Script payload is required");
        }
        if (script.length() > 100000) {
            throw new ApplicationException(ErrorCode.SCRIPT_INVALID_PAYLOAD, "Script size exceeds 100,000 characters");
        }

        String executionId = UUID.randomUUID().toString();
        String callerId = caller.userId() != null ? caller.userId() : "anonymous";
        logService.logStart(executionId, script, callerId);

        ValueToJsonConverter converter = new ValueToJsonConverter();
        ScriptExecutionContext executionContext = new ScriptExecutionContext(executionId, logService, converter);
        ConsoleLogProxy consoleProxy = new ConsoleLogProxy(executionContext);

        Long userId = null;
        if (caller.userId() != null) {
            try {
                userId = Long.valueOf(caller.userId());
            } catch (NumberFormatException ignored) {}
        }
        TablesProxy tablesProxy = new TablesProxy(dataService, executionContext, userId);

        ScriptExecutionOptions options = new ScriptExecutionOptions(
                executionProperties.timeoutMs(),
                executionProperties.debugEnabled(),
                executionProperties.memoryLimitMb()
        );

        // NOTE: deliberately not a try-with-resources. ScriptContextFactory.evalWithTimeout's
        // watchdog can call context.close(true) on a background thread when a script exceeds
        // options.timeoutMs(). GraalVM 24.0.2 then rethrows PolyglotException(isCancelled()==true)
        // from every subsequent close() call on that context. Closing the context exactly once,
        // ourselves, in this finally block (and swallowing that specific rethrow) avoids a second,
        // redundant close attempt racing/duplicating the watchdog's close.
        Context context = ScriptContextFactory.createContext(consoleProxy, tablesProxy, options);
        try {
            Source source = Source.newBuilder("js", script, "script.js").build();
            Value result = ScriptContextFactory.evalWithTimeout(context, source, options.timeoutMs());
            Object hostObj = converter.convertToHostObject(result);
            String output = converter.convert(result);
            logService.logSuccess(executionId, output);
            List<ScriptLogEntryResponse> logs = toLogResponses(logService.getEntries(executionId));
            return new ScriptExecutionResponse(hostObj, logs);
        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof PolyglotException pe && pe.isHostException()) {
                cause = pe.asHostException();
            }
            ExecutionStatus failureStatus = (e instanceof PolyglotException pe && pe.isCancelled())
                    ? ExecutionStatus.TIMEOUT
                    : ExecutionStatus.FAILED;
            if (cause instanceof ApplicationException ae) {
                logService.logFailure(executionId, describeFailure(ae), failureStatus);
                throw ae;
            }
            logService.logFailure(executionId, e.getMessage(), failureStatus);
            throw new ApplicationException(ErrorCode.SCRIPT_QUERY_FAILED, e.getMessage());
        } finally {
            try {
                context.close();
            } catch (PolyglotException pe) {
                // Expected when the watchdog already force-closed the context after a timeout
                // (isCancelled() == true). Safe to ignore: the context is already closed.
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
        }
    }

    private String describeFailure(ApplicationException ae) {
        String base = ae.getErrorCode().name();
        Object[] args = ae.getArgs();
        if (args != null && args.length > 0) {
            return base + ": " + java.util.Arrays.toString(args);
        }
        return base;
    }

    private List<ScriptLogEntryResponse> toLogResponses(List<ExecutionLogEntry> entries) {
        return entries.stream()
                .map(entry -> new ScriptLogEntryResponse(entry.level(), entry.message(), entry.loggedAt()))
                .collect(Collectors.toList());
    }
}
