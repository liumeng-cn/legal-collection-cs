package com.legalcs.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

    private final String baseUrl;
    private final String model;
    private final int dimension;
    private final String apiKey;
    private final Duration connectTimeout;
    private final Duration readTimeout;
}
