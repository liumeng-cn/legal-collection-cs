package com.legalcs.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.rerank")
public class RerankProperties {

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
}
