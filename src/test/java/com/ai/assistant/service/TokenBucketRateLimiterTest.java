package com.ai.assistant.service;

import com.ai.assistant.config.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TokenBucketRateLimiter 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TokenBucketRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RateLimitConfig rateLimitConfig;

    @InjectMocks
    private TokenBucketRateLimiter rateLimiter;

    private RateLimitConfig.LimitRule userRule;

    @BeforeEach
    void setUp() {
        userRule = new RateLimitConfig.LimitRule();
        userRule.setMaxRequests(10);
        userRule.setWindowSeconds(60);
    }

    @Test
    @DisplayName("测试未配置限流规则时允许请求")
    void testAllowWhenNoRuleConfigured() {
        when(rateLimitConfig.getUser()).thenReturn(null);
        when(rateLimitConfig.getIp()).thenReturn(null);
        when(rateLimitConfig.getGlobal()).thenReturn(null);

        boolean allowed = rateLimiter.tryAcquire("user1", "127.0.0.1");

        assertTrue(allowed);
    }

    @Test
    @DisplayName("测试限流服务初始化")
    void testRateLimiterInitialization() {
        assertNotNull(rateLimiter);
    }

    @Test
    @DisplayName("测试获取剩余令牌数")
    void testGetRemainingTokens() {
        when(rateLimitConfig.getUser()).thenReturn(userRule);
        when(redisTemplate.opsForHash()).thenReturn(mock(org.springframework.data.redis.core.HashOperations.class));

        double remaining = rateLimiter.getRemainingTokens("user1");

        assertTrue(remaining >= 0 || remaining == -1);
    }

    @Test
    @DisplayName("测试重置用户限流")
    void testResetUserLimit() {
        when(redisTemplate.delete(anyString())).thenReturn(true);

        assertDoesNotThrow(() -> rateLimiter.resetUserLimit("user1"));

        verify(redisTemplate).delete(contains("user:user1"));
    }

    @Test
    @DisplayName("测试限流信息获取")
    void testGetRateLimitInfo() {
        when(rateLimitConfig.getUser()).thenReturn(userRule);
        when(redisTemplate.opsForHash()).thenReturn(mock(org.springframework.data.redis.core.HashOperations.class));

        RateLimitConfig.LimitRule ipRule = new RateLimitConfig.LimitRule();
        ipRule.setMaxRequests(100);
        ipRule.setWindowSeconds(60);
        when(rateLimitConfig.getIp()).thenReturn(ipRule);

        TokenBucketRateLimiter.RateLimitInfo info = rateLimiter.getRateLimitInfo("user1", "127.0.0.1");

        assertNotNull(info);
        assertEquals(userRule.getMaxRequests(), info.getUserLimit());
        assertEquals(ipRule.getMaxRequests(), info.getIpLimit());
    }
}
