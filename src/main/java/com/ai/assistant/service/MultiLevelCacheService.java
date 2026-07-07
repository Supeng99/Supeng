package com.ai.assistant.service;

import com.ai.assistant.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存服务
 * L1: Caffeine (本地) -> L2: Redis (分布式)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiLevelCacheService {

    private final CacheManager caffeineCacheManager;
    private final StringRedisTemplate redisTemplate;
    private final CacheConfig cacheConfig;

    private static final String L1_CACHE_NAME = "chatSessions";

    /**
     * 获取缓存值（两级穿透）
     */
    public <T> T get(String key, Class<T> type, Callable<T> loader) {
        T value = getFromL1(key);
        if (value != null) {
            log.debug("L1缓存命中: {}", key);
            return value;
        }

        value = getFromL2(key, type);
        if (value != null) {
            log.debug("L2缓存命中: {}", key);
            putToL1(key, value);
            return value;
        }

        try {
            value = loader.call();
            if (value != null) {
                put(key, value);
                log.debug("缓存加载并存储: {}", key);
            }
        } catch (Exception e) {
            log.error("缓存加载失败: {}", key, e);
        }

        return value;
    }

    /**
     * 获取缓存值
     */
    public <T> T get(String key, Class<T> type) {
        T value = getFromL1(key);
        if (value != null) return value;

        return getFromL2(key, type);
    }

    /**
     * 设置缓存
     */
    public void put(String key, Object value) {
        putToL1(key, value);
        putToL2(key, value);
    }

    /**
     * 删除缓存
     */
    public void evict(String key) {
        evictL1(key);
        evictL2(key);
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        clearL1();
        clearL2();
    }

    /**
     * L1缓存 - Caffeine
     */
    @SuppressWarnings("unchecked")
    private <T> T getFromL1(String key) {
        Cache cache = caffeineCacheManager.getCache(L1_CACHE_NAME);
        if (cache == null) return null;

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) return null;

        return (T) wrapper.get();
    }

    private void putToL1(String key, Object value) {
        Cache cache = caffeineCacheManager.getCache(L1_CACHE_NAME);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void evictL1(String key) {
        Cache cache = caffeineCacheManager.getCache(L1_CACHE_NAME);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private void clearL1() {
        Cache cache = caffeineCacheManager.getCache(L1_CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * L2缓存 - Redis
     */
    @SuppressWarnings("unchecked")
    private <T> T getFromL2(String key, Class<T> type) {
        try {
            String redisKey = cacheConfig.getRedis().getKeyPrefix() + key;
            Object value = redisTemplate.opsForValue().get(redisKey);
            return value != null ? (T) value : null;
        } catch (Exception e) {
            log.warn("L2缓存读取失败: {}", key, e);
            return null;
        }
    }

    private void putToL2(String key, Object value) {
        try {
            String redisKey = cacheConfig.getRedis().getKeyPrefix() + key;
            redisTemplate.opsForValue().set(
                    redisKey,
                    String.valueOf(value),
                    cacheConfig.getRedis().getExpireSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("L2缓存写入失败: {}", key, e);
        }
    }

    private void evictL2(String key) {
        try {
            String redisKey = cacheConfig.getRedis().getKeyPrefix() + key;
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("L2缓存删除失败: {}", key, e);
        }
    }

    private void clearL2() {
        try {
            Set<String> keys = redisTemplate.keys(cacheConfig.getRedis().getKeyPrefix() + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("L2缓存清空失败", e);
        }
    }

    /**
     * 获取缓存统计
     */
    public CacheStats getStats() {
        Cache cache = caffeineCacheManager.getCache(L1_CACHE_NAME);
        if (cache instanceof CaffeineCache) {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                    ((CaffeineCache) cache).getNativeCache();

            com.github.benmanes.caffeine.cache.stats.CacheStats stats = nativeCache.stats();

            return CacheStats.builder()
                    .hitCount(stats.hitCount())
                    .missCount(stats.missCount())
                    .hitRate(stats.hitRate())
                    .evictionCount(stats.evictionCount())
                    .estimatedSize(nativeCache.estimatedSize())
                    .build();
        }

        return CacheStats.builder().build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CacheStats {
        private long hitCount;
        private long missCount;
        private double hitRate;
        private long evictionCount;
        private long estimatedSize;
    }
}
