//package com.citi.tts.api.gateway.limiter;
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
// * 本地优先限流器
// * 设计理念：本地限流优先，分布式限流兜底
// *
// * 优势：
// * 1. 本地限流响应快（微秒级）
// * 2. 减少网络开销和Redis压力
// * 3. 分布式限流作为兜底，保证全局一致性
// * 4. 故障容错能力强
// */
//@Slf4j
//@Component
//public class LocalFirstRateLimiter {
//
//    @Autowired
//    private RedisRateLimiter redisRateLimiter;
//
//    // 本地限流器缓存
//    private final ConcurrentHashMap<String, LocalRateLimiter> localLimiters = new ConcurrentHashMap<>();
//
//    // 分布式限流配置
//    private final ConcurrentHashMap<String, DistributedRateLimitConfig> distributedConfigs = new ConcurrentHashMap<>();
//
//    /**
//     * 本地限流器 - 高性能滑动窗口实现
//     */
//    public static class LocalRateLimiter {
//        private final int maxRequests;
//        private final int burstSize;
//        private final long windowSize; // 毫秒
//        private final AtomicReference<Window> currentWindow;
//        private final String name;
//        private final AtomicLong totalRequests = new AtomicLong(0);
//        private final AtomicLong rejectedRequests = new AtomicLong(0);
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
//            totalRequests.incrementAndGet();
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
//                rejectedRequests.incrementAndGet();
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
//        public synchronized double getUtilizationRate() {
//            return (double) getCurrentCount() / maxRequests;
//        }
//
//        public synchronized double getRejectionRate() {
//            long total = totalRequests.get();
//            long rejected = rejectedRequests.get();
//            return total > 0 ? (double) rejected / total : 0.0;
//        }
//
//        private static class Window {
//            private final long startTime = System.currentTimeMillis();
//            private final AtomicLong counter = new AtomicLong(0);
//        }
//    }
//
//    /**
//     * 分布式限流配置
//     */
//    public static class DistributedRateLimitConfig {
//        private final int maxRequests;
//        private final int burstSize;
//        private final int windowSize;
//        private final double localRatio; // 本地限流比例，如0.8表示80%的流量由本地处理
//
//        public DistributedRateLimitConfig(int maxRequests, int burstSize, int windowSize, double localRatio) {
//            this.maxRequests = maxRequests;
//            this.burstSize = burstSize;
//            this.windowSize = windowSize;
//            this.localRatio = localRatio;
//        }
//
//        public int getMaxRequests() { return maxRequests; }
//        public int getBurstSize() { return burstSize; }
//        public int getWindowSize() { return windowSize; }
//        public double getLocalRatio() { return localRatio; }
//    }
//
//    /**
//     * 本地优先限流检查
//     * 策略：
//     * 1. 本地限流优先处理大部分流量（如80%）
//     * 2. 分布式限流作为兜底，处理剩余流量和异常情况
//     * 3. Redis故障时自动降级为纯本地限流
//     */
//    public Mono<Boolean> localFirstRateLimit(String key, RateLimitConfig config) {
//        // 1. 本地限流检查（快速响应）
//        LocalRateLimiter localLimiter = getLocalLimiter(key, config);
//        boolean localAllowed = localLimiter.tryAcquire();
//
//        if (!localAllowed) {
//            log.debug("Local rate limit exceeded for key: {}", key);
//            return Mono.just(false);
//        }
//
//        // 2. 判断是否需要分布式限流检查
//        DistributedRateLimitConfig distributedConfig = getDistributedConfig(key, config);
//        double localUtilization = localLimiter.getUtilizationRate();
//
//        // 如果本地利用率低于阈值，直接放行（减少Redis压力）
//        if (localUtilization < distributedConfig.getLocalRatio()) {
//            log.debug("Local utilization rate {} < {}, skip distributed check for key: {}",
//                    localUtilization, distributedConfig.getLocalRatio(), key);
//            return Mono.just(true);
//        }
//
//        // 3. 分布式限流检查（兜底）
//        return redisRateLimiter.slidingWindowRateLimit(key, config.getWindowSize(), config.getMaxRequests(), config.getBurstSize())
//            .doOnSuccess(redisAllowed -> {
//                if (!redisAllowed) {
//                    log.debug("Distributed rate limit exceeded for key: {}", key);
//                } else {
//                    log.debug("Distributed rate limit check passed for key: {}", key);
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
//     * 获取分布式限流配置
//     */
//    private DistributedRateLimitConfig getDistributedConfig(String key, RateLimitConfig config) {
//        return distributedConfigs.computeIfAbsent(key, k ->
//            new DistributedRateLimitConfig(
//                config.getMaxRequests(),
//                config.getBurstSize(),
//                config.getWindowSize(),
//                0.8 // 默认80%流量由本地处理
//            ));
//    }
//
//    /**
//     * 仅本地限流检查（Redis故障时的降级策略）
//     */
//    public boolean localOnlyRateLimit(String key, RateLimitConfig config) {
//        LocalRateLimiter localLimiter = getLocalLimiter(key, config);
//        return localLimiter.tryAcquire();
//    }
//
//    /**
//     * 仅分布式限流检查（用于精确控制）
//     */
//    public Mono<Boolean> distributedOnlyRateLimit(String key, RateLimitConfig config) {
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
//                "local_first",
//                localLimiter.getCurrentCount(),
//                localLimiter.getUtilizationRate(),
//                localLimiter.getRejectionRate(),
//                "active"
//            );
//        }
//        return new RateLimiterStatus("local_first", 0L, 0.0, 0.0, "not_found");
//    }
//
//    /**
//     * 清理过期的本地限流器
//     */
//    public void cleanup() {
//        // 这里可以添加清理逻辑，比如清理长时间未使用的本地限流器
//        log.debug("Local first rate limiter cleanup completed. Active local limiters: {}", localLimiters.size());
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
//        private final double rejectionRate;
//        private final String mode;
//
//        public RateLimiterStatus(String type, long currentCount, double utilizationRate, double rejectionRate, String mode) {
//            this.type = type;
//            this.currentCount = currentCount;
//            this.utilizationRate = utilizationRate;
//            this.rejectionRate = rejectionRate;
//            this.mode = mode;
//        }
//
//        public String getType() { return type; }
//        public long getCurrentCount() { return currentCount; }
//        public double getUtilizationRate() { return utilizationRate; }
//        public double getRejectionRate() { return rejectionRate; }
//        public String getMode() { return mode; }
//    }
//}