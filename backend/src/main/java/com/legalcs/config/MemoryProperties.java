package com.legalcs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.memory")
public record MemoryProperties(
        boolean enabled,
        int topK,
        double similarityThreshold,
        int retentionDays) {
}
