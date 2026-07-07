package com.ai.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    private String defaultModel;
    private ModelConfig deepseek;
    private ModelConfig qwen;

    @Data
    public static class ModelConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}
