package com.citi.tts.api.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Duration;

/**
 * 多级缓存配置
 * L1: 本地缓存 (Caffeine) - 最快
 * L2: Redis缓存 - 分布式共享
 * L3: 数据库/外部服务 - 最慢
 */
@Slf4j
@Configuration
public class CacheConfig {

    @Value("${cache.local.max-size:10000}")
    private int localMaxSize;

    @Value("${cache.local.expire-after-write:300}")
    private int localExpireAfterWrite;

    @Value("${cache.redis.expire-after-write:1800}")
    private int redisExpireAfterWrite;

    @Value("${cache.redis.default-ttl:3600}")
    private int redisDefaultTtl;

    /**
     * 用户信息缓存 - 高频访问
     */
    @Bean("userInfoCache")
    public LoadingCache<String, Object> userInfoCache() {
        return Caffeine.newBuilder()
                .maximumSize(localMaxSize)
                .expireAfterWrite(Duration.ofSeconds(localExpireAfterWrite))
                .recordStats()
                .build(key -> null); // 缓存未命中时返回null
    }

    /**
     * API路由缓存 - 中频访问
     */
    @Bean("apiRouteCache")
    public LoadingCache<String, Object> apiRouteCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build(key -> null);
    }

    /**
     * 限流配置缓存 - 低频访问
     */
    @Bean("rateLimitConfigCache")
    public LoadingCache<String, Object> rateLimitConfigCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats()
                .build(key -> null);
    }

    /**
     * 证书缓存 - 长期缓存
     */
    @Bean("certificateCache")
    public LoadingCache<String, Object> certificateCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats()
                .build(key -> null);
    }

    /**
     * 降级数据缓存 - 核心服务兜底
     */
    @Bean("fallbackDataCache")
    public LoadingCache<String, Object> fallbackDataCache() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats()
                .build(key -> null);
    }

    /**
     * 热点数据缓存 - 极高频访问
     */
    @Bean("hotDataCache")
    public LoadingCache<String, Object> hotDataCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats()
                .build(key -> null);
    }

    /**
     * 分布式缓存 - Reactive RedisTemplate
     */
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        RedisSerializationContext.RedisSerializationContextBuilder<String, Object> builder =
            RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        RedisSerializationContext<String, Object> context = builder
            .value(new GenericJackson2JsonRedisSerializer())
            .build();
        return new ReactiveRedisTemplate<>(factory, context);
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