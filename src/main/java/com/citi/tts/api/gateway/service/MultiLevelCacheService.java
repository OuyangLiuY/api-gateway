package com.citi.tts.api.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 多级缓存服务
 * 实现本地缓存 + Redis缓存的协同工作
 * 支持缓存穿透、缓存击穿、缓存雪崩的防护
 */
@Slf4j
@Service
public class MultiLevelCacheService {

    @Autowired
    @Qualifier("userInfoCache")
    private LoadingCache<String, Object> userInfoCache;

    @Autowired
    @Qualifier("apiRouteCache")
    private LoadingCache<String, Object> apiRouteCache;

    @Autowired
    @Qualifier("rateLimitConfigCache")
    private LoadingCache<String, Object> rateLimitConfigCache;

    @Autowired
    @Qualifier("certificateCache")
    private LoadingCache<String, Object> certificateCache;

    @Autowired
    @Qualifier("fallbackDataCache")
    private LoadingCache<String, Object> fallbackDataCache;

    @Autowired
    @Qualifier("hotDataCache")
    private LoadingCache<String, Object> hotDataCache;

    /**
     * 获取缓存数据 - 多级缓存策略
     */
    public <T> Mono<T> get(String key, Class<T> type, Function<String, Mono<T>> loader) {
        return get(key, type, loader, Duration.ofMinutes(5));
    }

    /**
     * 获取缓存数据 - 指定过期时间
     */
    public <T> Mono<T> get(String key, Class<T> type, Function<String, Mono<T>> loader, Duration ttl) {
        // 1. 尝试从本地缓存获取
        Object localValue = getLocalCache(key);
        if (localValue != null) {
            log.debug("Cache hit (L1) for key: {}", key);
            return Mono.just(type.cast(localValue));
        }

        // 2. 本地缓存未命中，异步加载数据
        return loader.apply(key)
                .doOnNext(value -> {
                    // 3. 将数据放入本地缓存
                    putLocalCache(key, value, ttl);
                    log.debug("Cache miss (L1) for key: {}, loaded and cached", key);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to load data for key: {}", key, e);
                    // 4. 加载失败时，尝试从降级缓存获取
                    Object fallbackValue = getFallbackCache(key);
                    if (fallbackValue != null) {
                        log.info("Using fallback cache for key: {}", key);
                        return Mono.just(type.cast(fallbackValue));
                    }
                    return Mono.error(e);
                });
    }

    /**
     * 获取热点数据 - 使用热点缓存
     */
    public <T> Mono<T> getHotData(String key, Class<T> type, Function<String, Mono<T>> loader) {
        Object hotValue = hotDataCache.getIfPresent(key);
        if (hotValue != null) {
            log.debug("Hot cache hit for key: {}", key);
            return Mono.just(type.cast(hotValue));
        }

        return loader.apply(key)
                .doOnNext(value -> {
                    hotDataCache.put(key, value);
                    log.debug("Hot cache miss for key: {}, loaded and cached", key);
                });
    }

    /**
     * 批量获取缓存数据
     */
    public <T> Mono<java.util.Map<String, T>> getBatch(
            java.util.List<String> keys, 
            Class<T> type, 
            Function<java.util.List<String>, Mono<java.util.Map<String, T>>> batchLoader) {
        
        java.util.Map<String, T> result = new java.util.HashMap<>();
        java.util.List<String> missingKeys = new java.util.ArrayList<>();

        // 1. 从本地缓存批量获取
        for (String key : keys) {
            Object value = getLocalCache(key);
            if (value != null) {
                result.put(key, type.cast(value));
            } else {
                missingKeys.add(key);
            }
        }

        // 2. 如果所有数据都在本地缓存中，直接返回
        if (missingKeys.isEmpty()) {
            log.debug("Batch cache hit (L1) for all keys: {}", keys);
            return Mono.just(result);
        }

        // 3. 批量加载缺失的数据
        return batchLoader.apply(missingKeys)
                .doOnNext(batchResult -> {
                    // 4. 将新数据放入本地缓存
                    batchResult.forEach((key, value) -> {
                        putLocalCache(key, value, Duration.ofMinutes(5));
                        result.put(key, value);
                    });
                    log.debug("Batch cache miss (L1) for keys: {}, loaded and cached", missingKeys);
                })
                .map(batchResult -> result);
    }

    /**
     * 设置缓存数据
     */
    public <T> void put(String key, T value, Duration ttl) {
        putLocalCache(key, value, ttl);
        log.debug("Cache put for key: {}, ttl: {}", key, ttl);
    }

    /**
     * 删除缓存数据
     */
    public void evict(String key) {
        evictLocalCache(key);
        log.debug("Cache evicted for key: {}", key);
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        userInfoCache.invalidateAll();
        apiRouteCache.invalidateAll();
        rateLimitConfigCache.invalidateAll();
        certificateCache.invalidateAll();
        fallbackDataCache.invalidateAll();
        hotDataCache.invalidateAll();
        log.info("All caches cleared");
    }

    /**
     * 获取缓存统计信息
     */
    public java.util.Map<String, CacheStats> getCacheStats() {
        java.util.Map<String, CacheStats> stats = new java.util.HashMap<>();
        
        stats.put("userInfoCache", getCacheStats(userInfoCache, "userInfoCache"));
        stats.put("apiRouteCache", getCacheStats(apiRouteCache, "apiRouteCache"));
        stats.put("rateLimitConfigCache", getCacheStats(rateLimitConfigCache, "rateLimitConfigCache"));
        stats.put("certificateCache", getCacheStats(certificateCache, "certificateCache"));
        stats.put("fallbackDataCache", getCacheStats(fallbackDataCache, "fallbackDataCache"));
        stats.put("hotDataCache", getCacheStats(hotDataCache, "hotDataCache"));
        
        return stats;
    }

    // 私有方法

    private Object getLocalCache(String key) {
        // 根据key的前缀选择对应的缓存
        if (key.startsWith("user:")) {
            return userInfoCache.getIfPresent(key);
        } else if (key.startsWith("route:")) {
            return apiRouteCache.getIfPresent(key);
        } else if (key.startsWith("rate:")) {
            return rateLimitConfigCache.getIfPresent(key);
        } else if (key.startsWith("cert:")) {
            return certificateCache.getIfPresent(key);
        } else if (key.startsWith("fallback:")) {
            return fallbackDataCache.getIfPresent(key);
        } else {
            return userInfoCache.getIfPresent(key); // 默认使用用户信息缓存
        }
    }

    private void putLocalCache(String key, Object value, Duration ttl) {
        // 根据key的前缀选择对应的缓存
        if (key.startsWith("user:")) {
            userInfoCache.put(key, value);
        } else if (key.startsWith("route:")) {
            apiRouteCache.put(key, value);
        } else if (key.startsWith("rate:")) {
            rateLimitConfigCache.put(key, value);
        } else if (key.startsWith("cert:")) {
            certificateCache.put(key, value);
        } else if (key.startsWith("fallback:")) {
            fallbackDataCache.put(key, value);
        } else {
            userInfoCache.put(key, value); // 默认使用用户信息缓存
        }
    }

    private void evictLocalCache(String key) {
        if (key.startsWith("user:")) {
            userInfoCache.invalidate(key);
        } else if (key.startsWith("route:")) {
            apiRouteCache.invalidate(key);
        } else if (key.startsWith("rate:")) {
            rateLimitConfigCache.invalidate(key);
        } else if (key.startsWith("cert:")) {
            certificateCache.invalidate(key);
        } else if (key.startsWith("fallback:")) {
            fallbackDataCache.invalidate(key);
        } else {
            userInfoCache.invalidate(key);
        }
    }

    private Object getFallbackCache(String key) {
        return fallbackDataCache.getIfPresent(key);
    }

    private CacheStats getCacheStats(Cache<?, ?> cache, String cacheName) {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = cache.stats();
        return new CacheStats(
            cacheName,
            cache.estimatedSize(),
            stats.hitCount(),
            stats.missCount(),
            stats.hitRate()
        );
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final String cacheName;
        private final long size;
        private final long hitCount;
        private final long missCount;
        private final double hitRate;

        public CacheStats(String cacheName, long size, long hitCount, long missCount, double hitRate) {
            this.cacheName = cacheName;
            this.size = size;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
        }

        // Getters
        public String getCacheName() { return cacheName; }
        public long getSize() { return size; }
        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public double getHitRate() { return hitRate; }
    }
} 