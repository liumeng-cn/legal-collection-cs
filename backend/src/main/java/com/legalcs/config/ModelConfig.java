package com.legalcs.config;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ModelConfig {

    private static final String MODEL_PROVIDER_PREFIX = "openai:";

    private final ModelProperties modelProperties;

    @Bean
    public Model model() {
        String modelId = MODEL_PROVIDER_PREFIX + modelProperties.getName();
        ModelCreationContext context = ModelCreationContext.builder()
                .apiKey(modelProperties.getApiKey())
                .baseUrl(modelProperties.getBaseUrl())
                .stream(true)
                .build();
        return ModelRegistry.resolve(modelId, context);
    }
}
