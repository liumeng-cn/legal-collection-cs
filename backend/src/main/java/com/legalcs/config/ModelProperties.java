package com.legalcs.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.model")
public class ModelProperties {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
}
