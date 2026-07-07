package com.ai.assistant.service;

import com.ai.assistant.config.RateLimitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitConfig rateLimitConfig;

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    public boolean isAllowed(String userId, String ip) {
        boolean userAllowed = checkUserLimit(userId);
        boolean ipAllowed = checkIpLimit(ip);
        boolean globalAllowed = checkGlobalLimit();

        return userAllowed && ipAllowed && globalAllowed;
    }

    private boolean checkUserLimit(String userId) {
        RateLimitConfig.LimitRule rule = rateLimitConfig.getUser();
        if (rule == null) return true;

        String key = RATE_LIMIT_KEY_PREFIX + "user:" + userId;
        return executeLuaScript(key, rule.getMaxRequests(), rule.getWindowSeconds());
    }

    private boolean checkIpLimit(String ip) {
        RateLimitConfig.LimitRule rule = rateLimitConfig.getIp();
        if (rule == null) return true;

        String key = RATE_LIMIT_KEY_PREFIX + "ip:" + ip;
        return executeLuaScript(key, rule.getMaxRequests(), rule.getWindowSeconds());
    }

    private boolean checkGlobalLimit() {
        RateLimitConfig.LimitRule rule = rateLimitConfig.getGlobal();
        if (rule == null) return true;

        String key = RATE_LIMIT_KEY_PREFIX + "global";
        return executeLuaScript(key, rule.getMaxRequests(), rule.getWindowSeconds());
    }

    private boolean executeLuaScript(String key, int maxRequests, int windowSeconds) {
        String luaScript = 
            "local key = KEYS[1] " +
            "local limit = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local current = tonumber(redis.call('GET', key) or '0') " +
            "if current >= limit then " +
            "    return 0 " +
            "else " +
            "    current = redis.call('INCR', key) " +
            "    if current == 1 then " +
            "        redis.call('EXPIRE', key, window) " +
            "    end " +
            "    return 1 " +
            "end";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Long.class);

        try {
            Long result = redisTemplate.execute(script,
                    Arrays.asList(key),
                    String.valueOf(maxRequests),
                    String.valueOf(windowSeconds));
            return result != null && result == 1;
        } catch (Exception e) {
            log.warn("Rate limit check failed, allowing request: {}", e.getMessage());
            return true;
        }
    }

    public long getRemainingRequests(String userId) {
        String key = RATE_LIMIT_KEY_PREFIX + "user:" + userId;
        RateLimitConfig.LimitRule rule = rateLimitConfig.getUser();
        if (rule == null) return -1;

        try {
            String value = redisTemplate.opsForValue().get(key);
            long current = value != null ? Long.parseLong(value) : 0;
            return Math.max(0, rule.getMaxRequests() - current);
        } catch (Exception e) {
            log.warn("Failed to get remaining requests", e);
            return -1;
        }
    }

    public void resetUserLimit(String userId) {
        String key = RATE_LIMIT_KEY_PREFIX + "user:" + userId;
        redisTemplate.delete(key);
        log.info("Reset rate limit for user: {}", userId);
    }
}
