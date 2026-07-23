package com.springrest.scripting.engine;

import com.springrest.scripting.model.ScriptExecutionContext;
import com.springrest.scripting.model.ScriptExecutionOptions;
import com.springrest.scripting.proxy.CacheProxy;
import com.springrest.scripting.proxy.ConsoleLogProxy;
import com.springrest.scripting.proxy.TablesProxy;
import com.springrest.scripting.util.ValueToJsonConverter;
import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.hooks.ScriptHookInvoker;
import com.springrest.springrestproject.core.hooks.ScriptHookSession;
import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.core.model.scripting.ScriptType;
import com.springrest.springrestproject.model.Script;
import com.springrest.springrestproject.repository.ScriptRepo;
import com.springrest.springrestproject.service.implementations.ExecutionLogService;
import com.springrest.springrestproject.service.implementations.redis.ScriptCacheService;
import com.springrest.springrestproject.service.interfaces.IDataService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ScriptHookInvokerImpl implements ScriptHookInvoker {

    private final ScriptRepo scriptRepo;
    private final ExecutionLogService logService;
    private final IDataService dataService;
    private final ScriptCacheService cacheService;
    private final ScriptExecutionProperties executionProperties;

    public ScriptHookInvokerImpl(ScriptRepo scriptRepo, ExecutionLogService logService, IDataService dataService,
                                  ScriptCacheService cacheService, ScriptExecutionProperties executionProperties) {
        this.scriptRepo = scriptRepo;
        this.logService = logService;
        this.dataService = dataService;
        this.cacheService = cacheService;
        this.executionProperties = executionProperties;
    }

    @Override
    public Optional<ScriptHookSession> openDbHookSession(Long tableId, Long userId) {
        return scriptRepo.findByTableId(tableId).map(script -> openSession(script, userId));
    }

    @Override
    public Optional<ScriptHookSession> openOutboundTopicSession(Long topicId, Long userId) {
        return scriptRepo.findByTopicId(topicId).map(script -> openSession(script, userId));
    }

    @Override
    public Optional<ScriptHookSession> openInboundTopicSession(Long topicId, Long userId) {
        return scriptRepo.findByTopicId(topicId).map(script -> openSession(script, userId));
    }

    private ScriptHookSession openSession(Script script, Long userId) {
        String executionId = UUID.randomUUID().toString();
        logService.logStart(executionId, script.id(), userId == null ? null : String.valueOf(userId));

        ValueToJsonConverter converter = new ValueToJsonConverter();
        ScriptExecutionContext executionContext = new ScriptExecutionContext(executionId, logService, converter);
        ConsoleLogProxy consoleProxy = new ConsoleLogProxy(executionContext);
        TablesProxy tablesProxy = new TablesProxy(dataService, executionContext, userId);
        String namespace = script.scriptType() == ScriptType.DB
                ? "script:table:" + script.tableId() + ":"
                : "script:topic:" + script.topicId() + ":";
        CacheProxy cacheProxy = new CacheProxy(cacheService, namespace);

        ScriptExecutionOptions options = new ScriptExecutionOptions(
                executionProperties.timeoutMs(), false, executionProperties.memoryLimitMb());
        Context context = ScriptContextFactory.createHookContext(consoleProxy, tablesProxy, cacheProxy, options);
        try {
            Source source = Source.newBuilder("js", script.scriptBody(), "script-" + script.id() + ".js").build();
            ScriptContextFactory.evalWithTimeout(context, source, options.timeoutMs());
        } catch (Exception e) {
            logService.logFailure(executionId, e.getMessage(), ExecutionStatus.FAILED);
            // GraalVM 24.0.2 hazard (same as ScriptExecutionService.executeAdhoc): if
            // evalWithTimeout's watchdog already force-closed the context on a timeout,
            // this close() rethrows PolyglotException(isCancelled()==true). Swallow only
            // that specific case so the intended SCRIPT_HOOK_EXECUTION_FAILED still surfaces.
            try {
                context.close();
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
            throw new ApplicationException(ErrorCode.SCRIPT_HOOK_EXECUTION_FAILED, e.getMessage());
        }
        return new GraalScriptHookSession(executionId, context, logService);
    }
}
