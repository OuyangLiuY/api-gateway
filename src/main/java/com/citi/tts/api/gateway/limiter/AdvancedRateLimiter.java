package com.citi.tts.api.gateway.limiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 增强版限流器
 * 支持突发流量处理、滑动窗口和令牌桶算法
 *
 * 单机支持最大100qps
 */
@Slf4j
@Component
public class AdvancedRateLimiter {

    private final ConcurrentHashMap<String, SlidingWindowRateLimiter> slidingWindowLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucketRateLimiter> tokenBucketLimiters = new ConcurrentHashMap<>();

    /**
     * 滑动窗口限流器
     * 支持突发流量处理
     */
    public static class SlidingWindowRateLimiter {
        private final int windowSize; // 窗口大小（秒）
        private final int maxRequests; // 最大请求数
        private final int burstSize; // 突发流量大小
        private final AtomicReference<Window> currentWindow;
        private final String name;

        public SlidingWindowRateLimiter(int windowSize, int maxRequests, int burstSize, String name) {
            this.windowSize = windowSize;
            this.maxRequests = maxRequests;
            this.burstSize = burstSize;
            this.name = name;
            this.currentWindow = new AtomicReference<>(new Window());
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            Window window = currentWindow.get();
            
            // 检查是否需要创建新窗口
            if (now - window.startTime >= windowSize * 1000) {
                window = new Window();
                currentWindow.set(window);
            }
            
            // 计算当前窗口内的请求数
            long currentCount = window.counter.get();
            
            // 检查是否超过限制
            if (currentCount >= maxRequests) {
                // 检查突发流量配额
                if (currentCount < maxRequests + burstSize) {
                    window.counter.incrementAndGet();
                    log.debug("Burst request allowed for {}: {}/{}", name, currentCount + 1, maxRequests + burstSize);
                    return true;
                }
                log.debug("Rate limit exceeded for {}: {}/{}", name, currentCount, maxRequests);
                return false;
            }
            
            window.counter.incrementAndGet();
            return true;
        }

        public synchronized long getCurrentCount() {
            long now = System.currentTimeMillis();
            Window window = currentWindow.get();
            
            if (now - window.startTime >= windowSize * 1000) {
                return 0; // 窗口已重置
            }
            
            return window.counter.get();
        }

        public synchronized double getUtilizationRate() {
            long currentCount = getCurrentCount();
            return (double) currentCount / maxRequests;
        }

        private static class Window {
            private final long startTime = System.currentTimeMillis();
            private final AtomicLong counter = new AtomicLong(0);
        }
    }

    /**
     * 增强版令牌桶限流器
     * 支持突发流量和动态调整
     */
    public static class TokenBucketRateLimiter {
        private final long capacity; // 桶容量（burst）
        private final long refillRate; // 每秒补充速率（QPS）
        private final long refillInterval; // 补充间隔（毫秒）
        private long tokens; // 当前令牌数
        private long lastRefillTime; // 上次补充时间
        private final String name;
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong rejectedRequests = new AtomicLong(0);

        public TokenBucketRateLimiter(long capacity, long refillRate, long refillInterval, String name) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.refillInterval = refillInterval;
            this.tokens = capacity; // 初始时桶是满的
            this.lastRefillTime = System.currentTimeMillis();
            this.name = name;
        }

        public synchronized boolean tryAcquire() {
            return tryAcquire(1);
        }

        public synchronized boolean tryAcquire(int count) {
            totalRequests.incrementAndGet();
            refill();
            
            if (tokens >= count) {
                tokens -= count;
                log.debug("Token acquired for {}: {} tokens consumed, {} remaining", name, count, tokens);
                return true;
            }
            
            rejectedRequests.incrementAndGet();
            log.debug("Token insufficient for {}: requested {}, available {}", name, count, tokens);
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;
            long intervals = timePassed / refillInterval;
            
            if (intervals > 0) {
                long refillTokens = intervals * refillRate * refillInterval / 1000;
                tokens = Math.min(capacity, tokens + refillTokens);
                lastRefillTime += intervals * refillInterval;
                
                if (refillTokens > 0) {
                    log.debug("Tokens refilled for {}: +{} tokens, total: {}", name, refillTokens, tokens);
                }
            }
        }

        public synchronized long getAvailableTokens() {
            refill();
            return tokens;
        }

        public synchronized double getUtilizationRate() {
            return (double) (capacity - tokens) / capacity;
        }

        public synchronized double getRejectionRate() {
            long total = totalRequests.get();
            long rejected = rejectedRequests.get();
            return total > 0 ? (double) rejected / total : 0.0;
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

    /**
     * 获取或创建滑动窗口限流器
     */
    public SlidingWindowRateLimiter getSlidingWindowLimiter(String key, int windowSize, int maxRequests, int burstSize) {
        return slidingWindowLimiters.computeIfAbsent(key, k -> 
            new SlidingWindowRateLimiter(windowSize, maxRequests, burstSize, k));
    }

    /**
     * 获取或创建令牌桶限流器
     */
    public TokenBucketRateLimiter getTokenBucketLimiter(String key, long capacity, long refillRate, long refillInterval) {
        return tokenBucketLimiters.computeIfAbsent(key, k -> 
            new TokenBucketRateLimiter(capacity, refillRate, refillInterval, k));
    }

    /**
     * 获取所有限流器状态
     */
    public java.util.Map<String, Object> getAllLimitersStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        
        // 滑动窗口限流器状态
        java.util.Map<String, Object> slidingWindowStatus = new java.util.HashMap<>();
        slidingWindowLimiters.forEach((key, limiter) -> {
            slidingWindowStatus.put(key, java.util.Map.of(
                "type", "sliding_window",
                "currentCount", limiter.getCurrentCount(),
                "utilizationRate", limiter.getUtilizationRate()
            ));
        });
        status.put("slidingWindowLimiters", slidingWindowStatus);
        
        // 令牌桶限流器状态
        java.util.Map<String, Object> tokenBucketStatus = new java.util.HashMap<>();
        tokenBucketLimiters.forEach((key, limiter) -> {
            tokenBucketStatus.put(key, java.util.Map.of(
                "type", "token_bucket",
                "availableTokens", limiter.getAvailableTokens(),
                "capacity", limiter.getCapacity(),
                "refillRate", limiter.getRefillRate(),
                "utilizationRate", limiter.getUtilizationRate(),
                "rejectionRate", limiter.getRejectionRate()
            ));
        });
        status.put("tokenBucketLimiters", tokenBucketStatus);
        
        return status;
    }

    /**
     * 清理过期的限流器
     */
    public void cleanup() {
        // 这里可以添加清理逻辑，比如清理长时间未使用的限流器
        log.debug("Cleanup completed. Active limiters: {} sliding window, {} token bucket", 
                slidingWindowLimiters.size(), tokenBucketLimiters.size());
    }
} 