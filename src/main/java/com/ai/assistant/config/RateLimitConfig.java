package com.ai.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {

    private LimitRule user;
    private LimitRule ip;
    private LimitRule global;

    @Data
    public static class LimitRule {
        private int maxRequests;
        private int windowSeconds;
    }
}
