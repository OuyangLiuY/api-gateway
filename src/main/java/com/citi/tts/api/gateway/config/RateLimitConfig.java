package com.citi.tts.api.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流配置类
 * 支持多种限流策略：API权重限流、IP限流、用户限流、URL路径限流
 * 基于系统最高QPS 100优化设计
 */
@Slf4j
@Configuration
public class RateLimitConfig {

    @Bean
    @ConfigurationProperties(prefix = "gateway.rate-limit")
    public RateLimitProperties rateLimitProperties() {
        return new RateLimitProperties();
    }

    /**
     * 本地缓存限流器（用于IP限流）
     * 配置：30 QPS，50 burst
     */
    @Bean
    public LoadingCache<String, AtomicInteger> ipRateLimitCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10000)
                .recordStats() // 启用统计
                .build(key -> new AtomicInteger(0));
    }

    /**
     * 用户限流缓存
     * 配置：20 QPS，35 burst
     */
    @Bean
    public LoadingCache<String, AtomicInteger> userRateLimitCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10000)
                .recordStats() // 启用统计
                .build(key -> new AtomicInteger(0));
    }

    /**
     * URL路径限流缓存
     * 配置：40 QPS，60 burst
     */
    @Bean
    public LoadingCache<String, AtomicInteger> urlRateLimitCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(1000)
                .recordStats() // 启用统计
                .build(key -> new AtomicInteger(0));
    }

    /**
     * API权重限流缓存
     * 基于令牌桶算法实现
     */
    @Bean
    public Map<String, TokenBucket> apiWeightRateLimiters() {
        return new ConcurrentHashMap<>();
    }

    /**
     * 限流配置属性
     * 基于系统最高QPS 100优化
     */
    @Data
    @Component
    public static class RateLimitProperties {
        // 全局默认限流配置 - 作为兜底保护
        private int defaultQps = 80;
        private int defaultBurst = 120;
        
        // IP限流配置 - 防止单个IP攻击，保护系统整体
        private int ipQps = 30;
        private int ipBurst = 50;
        
        // 用户限流配置 - 防止单个用户过度使用
        private int userQps = 20;
        private int userBurst = 35;
        
        // URL路径限流配置 - 防止热点API被过度调用
        private int urlQps = 40;
        private int urlBurst = 60;
        
        // API权重限流配置 - 根据业务重要性分配资源
        private Map<String, ApiWeightConfig> apiWeights = Map.of(
            "CORE", new ApiWeightConfig(60, 80, 1.0),      // 核心API：60 QPS，60%资源
            "NORMAL", new ApiWeightConfig(25, 35, 0.4),    // 普通API：25 QPS，25%资源
            "NON_CORE", new ApiWeightConfig(10, 15, 0.2),  // 非核心API：10 QPS，10%资源
            "CRYPTO", new ApiWeightConfig(15, 20, 0.3)     // 加解密API：15 QPS，15%资源
        );
        
        // 限流开关
        private boolean enabled = true;
        private boolean ipLimitEnabled = true;
        private boolean userLimitEnabled = true;
        private boolean urlLimitEnabled = true;
        private boolean apiWeightLimitEnabled = true;

        @PostConstruct
        public void validateConfiguration() {
            log.info("=== 限流配置验证 ===");
            log.info("全局默认QPS: {}, Burst: {}", defaultQps, defaultBurst);
            log.info("IP限流: {} QPS, {} Burst, 启用: {}", ipQps, ipBurst, ipLimitEnabled);
            log.info("用户限流: {} QPS, {} Burst, 启用: {}", userQps, userBurst, userLimitEnabled);
            log.info("URL限流: {} QPS, {} Burst, 启用: {}", urlQps, urlBurst, urlLimitEnabled);
            log.info("API权重限流启用: {}", apiWeightLimitEnabled);
            
            // 验证API权重配置
            int totalWeightQps = apiWeights.values().stream()
                    .mapToInt(ApiWeightConfig::getQps)
                    .sum();
            log.info("API权重总QPS: {} (允许轻微超载)", totalWeightQps);
            
            // 验证配置合理性
            if (totalWeightQps > 120) {
                log.warn("API权重总QPS超过120，可能导致系统过载");
            }
            
            if (ipQps > defaultQps || userQps > defaultQps || urlQps > defaultQps) {
                log.warn("单层限流QPS超过全局默认QPS，可能影响限流效果");
            }
            
            log.info("=== 限流配置验证完成 ===");
        }
    }

    /**
     * API权重配置
     */
    @Data
    public static class ApiWeightConfig {
        private int qps;
        private int burst;
        private double weight;
        
        public ApiWeightConfig(int qps, int burst, double weight) {
            this.qps = qps;
            this.burst = burst;
            this.weight = weight;
        }
    }

    /**
     * 令牌桶算法实现
     * 支持突发流量处理
     */
    public static class TokenBucket {
        private final long capacity;
        private final long refillRate;
        private final long refillInterval;
        private long tokens;
        private long lastRefillTime;
        private final String name;

        public TokenBucket(long capacity, long refillRate, long refillInterval, String name) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.refillInterval = refillInterval;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
            this.name = name;
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        public synchronized boolean tryConsume(int count) {
            refill();
            if (tokens >= count) {
                tokens -= count;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;
            long intervals = timePassed / refillInterval;
            
            if (intervals > 0) {
                long refillTokens = intervals * refillRate;
                tokens = Math.min(capacity, tokens + refillTokens);
                lastRefillTime += intervals * refillInterval;
            }
        }

        public synchronized long getAvailableTokens() {
            refill();
            return tokens;
        }

        public synchronized double getUtilizationRate() {
            refill();
            return (double) (capacity - tokens) / capacity;
        }

        public String getName() {
            return name;
        }

        public long getCapacity() {
            return capacity;
        }

        public long getRefillRate() {
            return refillRate;
        }
    }
} 