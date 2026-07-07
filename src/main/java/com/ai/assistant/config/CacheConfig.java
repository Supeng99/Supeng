package com.ai.assistant.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * 多级缓存配置
 * L1: Caffeine (本地缓存) - 热点数据
 * L2: Redis (分布式缓存) - 跨服务共享
 */
@Data
@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "cache")
public class CacheConfig {

    private CaffeineConfig caffeine = new CaffeineConfig();
    private RedisConfig redis = new RedisConfig();

    @Data
    public static class CaffeineConfig {
        private int initialCapacity = 100;
        private int maximumSize = 1000;
        private int expireAfterWriteSeconds = 300;
        private int expireAfterAccessSeconds = 600;
    }

    @Data
    public static class RedisConfig {
        private int expireSeconds = 3600;
        private String keyPrefix = "cache:";
    }

    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(caffeine.getInitialCapacity())
                .maximumSize(caffeine.getMaximumSize())
                .expireAfterWrite(caffeine.getExpireAfterWriteSeconds(), TimeUnit.SECONDS)
                .expireAfterAccess(caffeine.getExpireAfterAccessSeconds(), TimeUnit.SECONDS)
                .recordStats());
        cacheManager.setCacheNames(java.util.Arrays.asList(
                "chatSessions", "aiResponses", "documents", "userSettings"
        ));
        return cacheManager;
    }
}
