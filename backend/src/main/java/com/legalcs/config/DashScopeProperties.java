package com.legalcs.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.dashscope")
public record DashScopeProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        Embedding embedding,
        Rerank rerank) {

    public record Embedding(String model, int dimension) {
    }

    public record Rerank(String model) {
    }
}
