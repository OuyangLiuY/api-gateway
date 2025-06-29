//package com.citi.tts.api.gateway.limiter.distrubt;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.ReactiveRedisTemplate;
//import org.springframework.data.redis.core.script.RedisScript;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//
//import java.time.Duration;
//import java.util.Arrays;
//import java.util.List;
//
///**
// * Redis分布式限流器
// * 支持滑动窗口和令牌桶算法
// * 实现全局一致的限流控制
// */
//@Slf4j
//@Component
//public class RedisRateLimiter {
//
//    @Autowired
//    private ReactiveRedisTemplate<String, Object> redisTemplate;
//
//    /**
//     * 滑动窗口限流 - Redis实现
//     * 使用Redis Sorted Set实现滑动窗口
//     */
//    public Mono<Boolean> slidingWindowRateLimit(String key, int windowSize, int maxRequests, int burstSize) {
//        long now = System.currentTimeMillis();
//        long windowStart = now - (windowSize * 1000);
//
//        String script = """
//            local key = KEYS[1]
//            local now = tonumber(ARGV[1])
//            local window_start = tonumber(ARGV[2])
//            local max_requests = tonumber(ARGV[3])
//            local burst_size = tonumber(ARGV[4])
//
//            -- 移除窗口外的数据
//            redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
//
//            -- 获取当前窗口内的请求数
//            local current_count = redis.call('ZCARD', key)
//
//            -- 检查是否超过限制
//            if current_count >= max_requests then
//                -- 检查突发流量配额
//                if current_count < max_requests + burst_size then
//                    -- 允许突发请求
//                    redis.call('ZADD', key, now, now .. '-' .. math.random())
//                    redis.call('EXPIRE', key, window_size)
//                    return 1
//                else
//                    return 0
//                end
//            else
//                -- 正常请求
//                redis.call('ZADD', key, now, now .. '-' .. math.random())
//                redis.call('EXPIRE', key, window_size)
//                return 1
//            end
//            """;
//
//        RedisScript<Long> redisScript = RedisScript.of(script, Long.class);
//
//        return redisTemplate.execute(redisScript,
//            Arrays.asList(key),
//            String.valueOf(now),
//            String.valueOf(windowStart),
//            String.valueOf(maxRequests),
//            String.valueOf(burstSize),
//            String.valueOf(windowSize))
//            .map(result -> result != null && result == 1)
//            .doOnSuccess(allowed -> {
//                if (!allowed) {
//                    log.debug("Redis sliding window rate limit exceeded for key: {}, window: {}s, max: {} + burst: {}",
//                            key, windowSize, maxRequests, burstSize);
//                }
//            })
//            .onErrorResume(e -> {
//                log.error("Redis sliding window rate limit error for key: {}", key, e);
//                // Redis故障时降级为本地限流或直接放行
//                return Mono.just(true);
//            });
//    }
//
//    /**
//     * 令牌桶限流 - Redis实现
//     * 使用Redis Hash存储令牌桶状态
//     */
//    public Mono<Boolean> tokenBucketRateLimit(String key, long capacity, long refillRate, long refillInterval) {
//        String script = """
//            local key = KEYS[1]
//            local capacity = tonumber(ARGV[1])
//            local refill_rate = tonumber(ARGV[2])
//            local refill_interval = tonumber(ARGV[3])
//            local now = tonumber(ARGV[4])
//
//            -- 获取当前令牌桶状态
//            local bucket_data = redis.call('HMGET', key, 'tokens', 'last_refill_time')
//            local current_tokens = tonumber(bucket_data[1]) or capacity
//            local last_refill_time = tonumber(bucket_data[2]) or now
//
//            -- 计算需要补充的令牌数
//            local time_passed = now - last_refill_time
//            local intervals = math.floor(time_passed / refill_interval)
//            local refill_tokens = intervals * refill_rate * refill_interval / 1000
//
//            -- 更新令牌数
//            current_tokens = math.min(capacity, current_tokens + refill_tokens)
//            last_refill_time = last_refill_time + intervals * refill_interval
//
//            -- 检查是否有足够的令牌
//            if current_tokens >= 1 then
//                current_tokens = current_tokens - 1
//                redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill_time', last_refill_time)
//                redis.call('EXPIRE', key, 3600) -- 1小时过期
//                return 1
//            else
//                redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill_time', last_refill_time)
//                redis.call('EXPIRE', key, 3600)
//                return 0
//            end
//            """;
//
//        RedisScript<Long> redisScript = RedisScript.of(script, Long.class);
//
//        return redisTemplate.execute(redisScript,
//            Arrays.asList(key),
//            String.valueOf(capacity),
//            String.valueOf(refillRate),
//            String.valueOf(refillInterval),
//            String.valueOf(System.currentTimeMillis()))
//            .map(result -> result != null && result == 1)
//            .doOnSuccess(allowed -> {
//                if (!allowed) {
//                    log.debug("Redis token bucket rate limit exceeded for key: {}, capacity: {}, refill_rate: {}",
//                            key, capacity, refillRate);
//                }
//            })
//            .onErrorResume(e -> {
//                log.error("Redis token bucket rate limit error for key: {}", key, e);
//                // Redis故障时降级为本地限流或直接放行
//                return Mono.just(true);
//            });
//    }
//
//    /**
//     * 固定窗口限流 - Redis实现
//     * 使用Redis INCR + EXPIRE实现
//     */
//    public Mono<Boolean> fixedWindowRateLimit(String key, int maxRequests, Duration window) {
//        String script = """
//            local key = KEYS[1]
//            local max_requests = tonumber(ARGV[1])
//            local window_seconds = tonumber(ARGV[2])
//
//            local current_count = redis.call('INCR', key)
//
//            if current_count == 1 then
//                redis.call('EXPIRE', key, window_seconds)
//            end
//
//            if current_count <= max_requests then
//                return 1
//            else
//                return 0
//            end
//            """;
//
//        RedisScript<Long> redisScript = RedisScript.of(script, Long.class);
//
//        return redisTemplate.execute(redisScript,
//            Arrays.asList(key),
//            String.valueOf(maxRequests),
//            String.valueOf(window.getSeconds()))
//            .map(result -> result != null && result == 1)
//            .doOnSuccess(allowed -> {
//                if (!allowed) {
//                    log.debug("Redis fixed window rate limit exceeded for key: {}, max: {}, window: {}s",
//                            key, maxRequests, window.getSeconds());
//                }
//            })
//            .onErrorResume(e -> {
//                log.error("Redis fixed window rate limit error for key: {}", key, e);
//                return Mono.just(true);
//            });
//    }
//
//    /**
//     * 获取限流器状态
//     */
//    public Mono<RateLimiterStatus> getRateLimiterStatus(String key, String type) {
//        if ("sliding_window".equals(type)) {
//            // 获取滑动窗口状态
//            return redisTemplate.opsForZSet().zCard(key)
//                .map(count -> new RateLimiterStatus(type, count != null ? count : 0L, 0.0))
//                .onErrorResume(e -> {
//                    log.error("Failed to get sliding window status for key: {}", key, e);
//                    return Mono.just(new RateLimiterStatus(type, 0L, 0.0));
//                });
//        } else if ("token_bucket".equals(type)) {
//            // 获取令牌桶状态
//            return redisTemplate.opsForHash().multiGet(key, Arrays.asList("tokens", "last_refill_time"))
//                .map(bucketData -> {
//                    Long tokens = bucketData.get(0) != null ? Long.valueOf(bucketData.get(0).toString()) : 0L;
//                    return new RateLimiterStatus(type, tokens, 0.0);
//                })
//                .onErrorResume(e -> {
//                    log.error("Failed to get token bucket status for key: {}", key, e);
//                    return Mono.just(new RateLimiterStatus(type, 0L, 0.0));
//                });
//        }
//        return Mono.just(new RateLimiterStatus(type, 0L, 0.0));
//    }
//
//    /**
//     * 清理过期的限流器数据
//     */
//    public Mono<Void> cleanup() {
//        return Mono.fromRunnable(() -> {
//            try {
//                // 这里可以添加清理逻辑，比如清理过期的限流器数据
//                log.debug("Redis rate limiter cleanup completed");
//            } catch (Exception e) {
//                log.error("Redis rate limiter cleanup error", e);
//            }
//        });
//    }
//
//    /**
//     * 限流器状态
//     */
//    public static class RateLimiterStatus {
//        private final String type;
//        private final long currentCount;
//        private final double utilizationRate;
//
//        public RateLimiterStatus(String type, long currentCount, double utilizationRate) {
//            this.type = type;
//            this.currentCount = currentCount;
//            this.utilizationRate = utilizationRate;
//        }
//
//        public String getType() { return type; }
//        public long getCurrentCount() { return currentCount; }
//        public double getUtilizationRate() { return utilizationRate; }
//    }
//}