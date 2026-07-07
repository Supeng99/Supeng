package com.ai.assistant.service;

import com.ai.assistant.config.RateLimitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 令牌桶限流服务
 * 支持用户级、IP级、全局限流
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBucketRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitConfig rateLimitConfig;

    private static final String TOKEN_BUCKET_KEY_PREFIX = "token_bucket:";

    /**
     * 尝试获取令牌
     */
    public boolean tryAcquire(String userId, String ip) {
        boolean userAllowed = tryAcquireForUser(userId);
        boolean ipAllowed = tryAcquireForIp(ip);
        boolean globalAllowed = tryAcquireGlobal();

        return userAllowed && ipAllowed && globalAllowed;
    }

    private boolean tryAcquireForUser(String userId) {
        RateLimitConfig.LimitRule rule = rateLimitConfig.getUser();
        if (rule == null) return true;

        String key = TOKEN_BUCKET_KEY_PREFIX + "user:" + userId;
        return executeTokenBucketScript(key, rule.getMaxRequests(), rule.getWindowSeconds());
    }

    private boolean tryAcquireForIp(String ip) {
        RateLimitConfig.LimitRule rule = rateLimitConfig.getIp();
        if (rule == null) return true;

        String key = TOKEN_BUCKET_KEY_PREFIX + "ip:" + ip;
        return executeTokenBucketScript(key, rule.getMaxRequests(), rule.getWindowSeconds());
    }

    private boolean tryAcquireGlobal() {
        RateLimitConfig.LimitRule rule = rateLimitConfig.getGlobal();
        if (rule == null) return true;

        String key = TOKEN_BUCKET_KEY_PREFIX + "global";
        return executeTokenBucketScript(key, rule.getMaxRequests(), rule.getWindowSeconds());
    }

    private boolean executeTokenBucketScript(String key, int capacity, int refillSeconds) {
        String luaScript =
            "local key = KEYS[1] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local refillRate = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "local requested = tonumber(ARGV[4]) " +

            "local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill') " +
            "local tokens = tonumber(bucket[1]) " +
            "local lastRefill = tonumber(bucket[2]) " +

            "if tokens == nil then " +
            "    tokens = capacity " +
            "    lastRefill = now " +
            "end " +

            "local elapsed = (now - lastRefill) / 1000 " +
            "local refilledTokens = math.floor(elapsed * refillRate) " +

            "tokens = math.min(capacity, tokens + refilledTokens) " +

            "if refilledTokens > 0 then " +
            "    lastRefill = now " +
            "end " +

            "if tokens >= requested then " +
            "    tokens = tokens - requested " +
            "    redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', lastRefill) " +
            "    redis.call('EXPIRE', key, " + (refillSeconds * 2) + ") " +
            "    return 1 " +
            "else " +
            "    redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', lastRefill) " +
            "    redis.call('EXPIRE', key, " + (refillSeconds * 2) + ") " +
            "    return 0 " +
            "end";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Long.class);

        try {
            long now = System.currentTimeMillis();
            double refillRate = (double) capacity / refillSeconds;

            Long result = redisTemplate.execute(script,
                    Arrays.asList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(now),
                    "1");

            return result != null && result == 1;
        } catch (Exception e) {
            log.warn("Token bucket check failed, allowing request: {}", e.getMessage());
            return true;
        }
    }

    public double getRemainingTokens(String userId) {
        String key = TOKEN_BUCKET_KEY_PREFIX + "user:" + userId;

        try {
            Object tokens = redisTemplate.opsForHash().get(key, "tokens");
            if (tokens == null) {
                RateLimitConfig.LimitRule rule = rateLimitConfig.getUser();
                return rule != null ? rule.getMaxRequests() : -1;
            }
            return Double.parseDouble(String.valueOf(tokens));
        } catch (Exception e) {
            log.warn("Failed to get remaining tokens", e);
            return -1;
        }
    }

    public void resetUserLimit(String userId) {
        String key = TOKEN_BUCKET_KEY_PREFIX + "user:" + userId;
        redisTemplate.delete(key);
        log.info("Reset rate limit for user: {}", userId);
    }

    public RateLimitInfo getRateLimitInfo(String userId, String ip) {
        RateLimitConfig.LimitRule userRule = rateLimitConfig.getUser();
        RateLimitConfig.LimitRule ipRule = rateLimitConfig.getIp();

        return RateLimitInfo.builder()
                .userRemaining(getRemainingTokens(userId))
                .userLimit(userRule != null ? userRule.getMaxRequests() : -1)
                .userWindow(userRule != null ? userRule.getWindowSeconds() : -1)
                .ipRemaining(getIpRemainingTokens(ip))
                .ipLimit(ipRule != null ? ipRule.getMaxRequests() : -1)
                .ipWindow(ipRule != null ? ipRule.getWindowSeconds() : -1)
                .build();
    }

    private double getIpRemainingTokens(String ip) {
        String key = TOKEN_BUCKET_KEY_PREFIX + "ip:" + ip;

        try {
            Object tokens = redisTemplate.opsForHash().get(key, "tokens");
            if (tokens == null) {
                RateLimitConfig.LimitRule rule = rateLimitConfig.getIp();
                return rule != null ? rule.getMaxRequests() : -1;
            }
            return Double.parseDouble(String.valueOf(tokens));
        } catch (Exception e) {
            return -1;
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RateLimitInfo {
        private double userRemaining;
        private int userLimit;
        private int userWindow;
        private double ipRemaining;
        private int ipLimit;
        private int ipWindow;
    }
}
