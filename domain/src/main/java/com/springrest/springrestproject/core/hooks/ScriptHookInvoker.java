package com.springrest.springrestproject.core.hooks;

import java.util.Optional;

public interface ScriptHookInvoker {
    Optional<ScriptHookSession> openDbHookSession(Long tableId, Long userId);
    Optional<ScriptHookSession> openOutboundTopicSession(Long topicId, Long userId);
    Optional<ScriptHookSession> openInboundTopicSession(Long topicId, Long userId);
}
