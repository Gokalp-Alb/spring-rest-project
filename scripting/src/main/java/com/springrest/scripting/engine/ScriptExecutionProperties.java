package com.springrest.scripting.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// NOTE: intentionally not @Component. Constructor-bound (record) @ConfigurationProperties
// classes must be registered exclusively through @ConfigurationPropertiesScan (or
// @EnableConfigurationProperties) so Spring Boot's constructor-binding machinery constructs
// them from bound properties. Also annotating with @Component causes a second, plain
// @ComponentScan-driven bean definition that is instantiated via ordinary reflective
// constructor autowiring - which fails outright for a record with no autowirable
// (e.g. primitive `long`) constructor arguments.
@ConfigurationProperties(prefix = "script.execution")
public record ScriptExecutionProperties(
        @DefaultValue("5000") long timeoutMs,
        @DefaultValue("64") int memoryLimitMb,
        @DefaultValue("false") boolean debugEnabled
) {}
