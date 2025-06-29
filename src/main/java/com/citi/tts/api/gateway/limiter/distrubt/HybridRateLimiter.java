//package com.citi.tts.api.gateway.limiter.distrubt;
//
//import com.citi.tts.api.gateway.limiter.distrubt.RedisRateLimiter;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicLong;
//import java.util.concurrent.atomic.AtomicReference;
//
///**
// * 混合限流器
// * 结合本地限流和Redis分布式限流的优势
// * 实现分层防护：本地限流(快速) + Redis限流(精确)
// */
//@Slf4j
//@Component
//public class HybridRateLimiter {
//
//    @Autowired
//    private RedisRateLimiter redisRateLimiter;
//
//    // 本地限流器缓存
//    private final ConcurrentHashMap<String, LocalRateLimiter> localLimiters = new ConcurrentHashMap<>();
//
//    /**
//     * 本地限流器 - 简单高效的计数器实现
//     */
//    public static class LocalRateLimiter {
//        private final int maxRequests;
//        private final int burstSize;
//        private final long windowSize; // 毫秒
//        private final AtomicReference<Window> currentWindow;
//        private final String name;
//
//        public LocalRateLimiter(int maxRequests, int burstSize, long windowSize, String name) {
//            this.maxRequests = maxRequests;
//            this.burstSize = burstSize;
//            this.windowSize = windowSize;
//            this.name = name;
//            this.currentWindow = new AtomicReference<>(new Window());
//        }
//
//        public synchronized boolean tryAcquire() {
//            long now = System.currentTimeMillis();
//            Window window = currentWindow.get();
//
//            // 检查是否需要创建新窗口
//            if (now - window.startTime >= windowSize) {
//                window = new Window();
//                currentWindow.set(window);
//            }
//
//            // 计算当前窗口内的请求数
//            long currentCount = window.counter.get();
//
//            // 检查是否超过限制
//            if (currentCount >= maxRequests) {
//                // 检查突发流量配额
//                if (currentCount < maxRequests + burstSize) {
//                    window.counter.incrementAndGet();
//                    log.debug("Local burst request allowed for {}: {}/{}", name, currentCount + 1, maxRequests + burstSize);
//                    return true;
//                }
//                log.debug("Local rate limit exceeded for {}: {}/{}", name, currentCount, maxRequests);
//                return false;
//            }
//
//            window.counter.incrementAndGet();
//            return true;
//        }
//
//        public synchronized long getCurrentCount() {
//            long now = System.currentTimeMillis();
//            Window window = currentWindow.get();
//
//            if (now - window.startTime >= windowSize) {
//                return 0; // 窗口已重置
//            }
//
//            return window.counter.get();
//        }
//
//        private static class Window {
//            private final long startTime = System.currentTimeMillis();
//            private final AtomicLong counter = new AtomicLong(0);
//        }
//    }
//
//    /**
//     * 混合限流检查
//     * 策略：本地限流(快速) + Redis限流(精确)
//     */
//    public Mono<Boolean> hybridRateLimit(String key, RateLimitConfig config) {
//        // 1. 本地限流检查 (快速响应)
//        LocalRateLimiter localLimiter = getLocalLimiter(key, config);
//        boolean localAllowed = localLimiter.tryAcquire();
//
//        if (!localAllowed) {
//            log.debug("Local rate limit exceeded for key: {}", key);
//            return Mono.just(false);
//        }
//
//        // 2. Redis分布式限流检查 (精确控制)
//        return redisRateLimiter.slidingWindowRateLimit(key, config.getWindowSize(), config.getMaxRequests(), config.getBurstSize())
//            .doOnSuccess(redisAllowed -> {
//                if (!redisAllowed) {
//                    log.debug("Redis rate limit exceeded for key: {}", key);
//                }
//            })
//            .onErrorResume(e -> {
//                log.warn("Redis rate limit failed for key: {}, fallback to local only", key, e);
//                // Redis故障时，仅依赖本地限流
//                return Mono.just(true);
//            });
//    }
//
//    /**
//     * 获取本地限流器
//     */
//    private LocalRateLimiter getLocalLimiter(String key, RateLimitConfig config) {
//        return localLimiters.computeIfAbsent(key, k ->
//            new LocalRateLimiter(
//                config.getMaxRequests(),
//                config.getBurstSize(),
//                config.getWindowSize() * 1000, // 转换为毫秒
//                k
//            ));
//    }
//
//    /**
//     * 仅本地限流检查 (Redis故障时的降级策略)
//     */
//    public boolean localOnlyRateLimit(String key, RateLimitConfig config) {
//        LocalRateLimiter localLimiter = getLocalLimiter(key, config);
//        return localLimiter.tryAcquire();
//    }
//
//    /**
//     * 仅Redis限流检查 (用于精确控制)
//     */
//    public Mono<Boolean> redisOnlyRateLimit(String key, RateLimitConfig config) {
//        return redisRateLimiter.slidingWindowRateLimit(key, config.getWindowSize(), config.getMaxRequests(), config.getBurstSize());
//    }
//
//    /**
//     * 获取限流器状态
//     */
//    public RateLimiterStatus getStatus(String key) {
//        LocalRateLimiter localLimiter = localLimiters.get(key);
//        if (localLimiter != null) {
//            return new RateLimiterStatus(
//                "hybrid",
//                localLimiter.getCurrentCount(),
//                0.0,
//                "local_only"
//            );
//        }
//        return new RateLimiterStatus("hybrid", 0L, 0.0, "not_found");
//    }
//
//    /**
//     * 清理过期的本地限流器
//     */
//    public void cleanup() {
//        // 这里可以添加清理逻辑，比如清理长时间未使用的本地限流器
//        log.debug("Hybrid rate limiter cleanup completed. Active local limiters: {}", localLimiters.size());
//    }
//
//    /**
//     * 限流配置
//     */
//    public static class RateLimitConfig {
//        private final int maxRequests;
//        private final int burstSize;
//        private final int windowSize; // 秒
//
//        public RateLimitConfig(int maxRequests, int burstSize, int windowSize) {
//            this.maxRequests = maxRequests;
//            this.burstSize = burstSize;
//            this.windowSize = windowSize;
//        }
//
//        public int getMaxRequests() { return maxRequests; }
//        public int getBurstSize() { return burstSize; }
//        public int getWindowSize() { return windowSize; }
//    }
//
//    /**
//     * 限流器状态
//     */
//    public static class RateLimiterStatus {
//        private final String type;
//        private final long currentCount;
//        private final double utilizationRate;
//        private final String mode;
//
//        public RateLimiterStatus(String type, long currentCount, double utilizationRate, String mode) {
//            this.type = type;
//            this.currentCount = currentCount;
//            this.utilizationRate = utilizationRate;
//            this.mode = mode;
//        }
//
//        public String getType() { return type; }
//        public long getCurrentCount() { return currentCount; }
//        public double getUtilizationRate() { return utilizationRate; }
//        public String getMode() { return mode; }
//    }
//}