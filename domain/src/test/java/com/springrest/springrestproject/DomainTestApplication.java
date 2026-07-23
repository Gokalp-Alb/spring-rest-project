package com.springrest.springrestproject;

import com.springrest.springrestproject.core.hooks.ScriptHookInvoker;
import com.springrest.springrestproject.core.hooks.ScriptHookSession;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

@SpringBootApplication(scanBasePackages = "com.springrest")
@ConfigurationPropertiesScan(basePackages = "com.springrest")
public class DomainTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(DomainTestApplication.class, args);
    }

    // domain cannot depend on scripting (scripting already depends on domain, so the reverse
    // would be circular), so the real ScriptHookInvokerImpl is never on domain's own test
    // classpath. This no-op stand-in lets DataService (which now requires a ScriptHookInvoker)
    // still wire up in domain's @SpringBootTest suite. @ConditionalOnMissingBean backs off when
    // a real implementation IS present, e.g. when scripting's own tests reuse this same
    // DomainTestApplication as their @SpringBootTest configuration class. Note this relies on
    // component-scan ordering registering ScriptHookInvokerImpl's bean definition before this
    // one is evaluated, rather than a deferred auto-configuration-style guarantee (this class
    // isn't processed as Spring Boot auto-configuration) - verified empirically in both
    // directions (ScriptHookInvokerImplTest gets the real bean, domain's own suite gets this
    // no-op). If that ever proves flaky, prefer @Primary on the real bean plus an
    // always-registered no-op instead of relying on @ConditionalOnMissingBean here.
    @Bean
    @ConditionalOnMissingBean(ScriptHookInvoker.class)
    public ScriptHookInvoker noOpScriptHookInvoker() {
        return new ScriptHookInvoker() {
            @Override
            public Optional<ScriptHookSession> openDbHookSession(Long tableId, Long userId) {
                return Optional.empty();
            }

            @Override
            public Optional<ScriptHookSession> openOutboundTopicSession(Long topicId, Long userId) {
                return Optional.empty();
            }

            @Override
            public Optional<ScriptHookSession> openInboundTopicSession(Long topicId, Long userId) {
                return Optional.empty();
            }
        };
    }
}
