package com.legalcs.config;

import io.agentscope.core.tracing.OtelTracingMiddleware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    @Bean
    public OtelTracingMiddleware otelTracingMiddleware() {
        return new OtelTracingMiddleware();
    }
}
