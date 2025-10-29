package com.novel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIClientConfig {

    @Value("${ai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.default-model:gpt-4o-mini}")
    private String defaultModel;

    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public String getDefaultModel() { return defaultModel; }
}

