package com.springrest.scripting.engine;

import com.springrest.springrestproject.core.exception.ApplicationException;
import com.springrest.springrestproject.core.exception.ErrorCode;
import com.springrest.springrestproject.core.hooks.ScriptHookSession;
import com.springrest.springrestproject.core.model.scripting.ExecutionStatus;
import com.springrest.springrestproject.service.implementations.ExecutionLogService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

final class GraalScriptHookSession implements ScriptHookSession {
    private final String executionId;
    private final Context context;
    private final ExecutionLogService logService;
    private boolean failed = false;

    GraalScriptHookSession(String executionId, Context context, ExecutionLogService logService) {
        this.executionId = executionId;
        this.context = context;
        this.logService = logService;
    }

    @Override
    public void invokeIfDefined(String functionName) {
        Value fn = context.getBindings("js").getMember(functionName);
        if (fn == null || !fn.canExecute()) {
            return;
        }
        try {
            fn.execute();
        } catch (Exception e) {
            failed = true;
            Throwable cause = (e instanceof PolyglotException pe && pe.isHostException()) ? pe.asHostException() : e;
            String message = cause.getMessage();
            logService.logFailure(executionId, message, ExecutionStatus.FAILED);
            throw new ApplicationException(ErrorCode.SCRIPT_HOOK_EXECUTION_FAILED, message);
        }
    }

    @Override
    public void close() {
        try {
            if (!failed) {
                logService.logSuccess(executionId, null);
            }
        } finally {
            context.close();
        }
    }
}
