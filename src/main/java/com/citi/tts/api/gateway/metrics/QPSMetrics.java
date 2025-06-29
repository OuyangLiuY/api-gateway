package com.citi.tts.api.gateway.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QPS统计组件
 * 支持实时QPS计算和多维度统计，带自动清理机制
 */
@Slf4j
@Component
public class QPSMetrics {

    // 全局QPS统计
    private final AtomicReference<QPSWindow> globalQPS = new AtomicReference<>(new QPSWindow());
    
    // 按API路径统计QPS
    private final ConcurrentHashMap<String, AtomicReference<QPSWindow>> apiQPS = new ConcurrentHashMap<>();
    
    // 按IP统计QPS
    private final ConcurrentHashMap<String, AtomicReference<QPSWindow>> ipQPS = new ConcurrentHashMap<>();
    
    // 按用户统计QPS
    private final ConcurrentHashMap<String, AtomicReference<QPSWindow>> userQPS = new ConcurrentHashMap<>();
    
    // 按API优先级统计QPS
    private final ConcurrentHashMap<String, AtomicReference<QPSWindow>> priorityQPS = new ConcurrentHashMap<>();

    // 清理配置
    private static final long CLEANUP_THRESHOLD = 60000; // 1分钟过期
    private static final int CLEANUP_BATCH_SIZE = 100; // 批量清理大小

    /**
     * 记录请求
     */
    public void recordRequest(String path, String ip, String userId, String priority) {
        long now = System.currentTimeMillis();
        
        // 全局QPS统计
        recordToWindow(globalQPS, now);
        
        // API路径QPS统计
        if (path != null) {
            apiQPS.computeIfAbsent(path, k -> new AtomicReference<>(new QPSWindow()));
            recordToWindow(apiQPS.get(path), now);
        }
        
        // IP QPS统计
        if (ip != null) {
            ipQPS.computeIfAbsent(ip, k -> new AtomicReference<>(new QPSWindow()));
            recordToWindow(ipQPS.get(ip), now);
        }
        
        // 用户QPS统计
        if (userId != null) {
            userQPS.computeIfAbsent(userId, k -> new AtomicReference<>(new QPSWindow()));
            recordToWindow(userQPS.get(userId), now);
        }
        
        // 优先级QPS统计
        if (priority != null) {
            priorityQPS.computeIfAbsent(priority, k -> new AtomicReference<>(new QPSWindow()));
            recordToWindow(priorityQPS.get(priority), now);
        }
    }

    /**
     * 记录到滑动窗口
     */
    private void recordToWindow(AtomicReference<QPSWindow> windowRef, long now) {
        QPSWindow window = windowRef.get();
        
        // 检查是否需要创建新窗口
        if (now - window.startTime >= 1000) { // 1秒窗口
            window = new QPSWindow();
            windowRef.set(window);
        }
        
        window.counter.incrementAndGet();
    }

    /**
     * 获取全局QPS（带自动清理）
     */
    public long getGlobalQPS() {
        return getWindowQPS(globalQPS);
    }

    /**
     * 获取API路径QPS（带自动清理）
     */
    public Map<String, Long> getApiQPS() {
        return getQPSWithCleanup(apiQPS, "API");
    }

    /**
     * 获取IP QPS（带自动清理）
     */
    public Map<String, Long> getIpQPS() {
        return getQPSWithCleanup(ipQPS, "IP");
    }

    /**
     * 获取用户QPS（带自动清理）
     */
    public Map<String, Long> getUserQPS() {
        return getQPSWithCleanup(userQPS, "User");
    }

    /**
     * 获取优先级QPS（带自动清理）
     */
    public Map<String, Long> getPriorityQPS() {
        return getQPSWithCleanup(priorityQPS, "Priority");
    }

    /**
     * 带自动清理的QPS获取方法
     */
    private Map<String, Long> getQPSWithCleanup(ConcurrentHashMap<String, AtomicReference<QPSWindow>> dataMap, String type) {
        Map<String, Long> result = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        final AtomicInteger cleanedCount = new AtomicInteger(0);
        
        // 遍历并清理过期数据
        dataMap.entrySet().removeIf(entry -> {
            QPSWindow window = entry.getValue().get();
            if (now - window.startTime >= CLEANUP_THRESHOLD) {
                cleanedCount.incrementAndGet();
                return true; // 移除过期数据
            } else {
                result.put(entry.getKey(), window.counter.get());
                return false; // 保留有效数据
            }
        });
        
        // 记录清理日志
        if (cleanedCount.get() > 0) {
            log.debug("Auto cleaned {} expired {} QPS entries, remaining: {}", 
                    cleanedCount.get(), type, dataMap.size());
        }
        
        return result;
    }

    /**
     * 获取窗口QPS
     */
    private long getWindowQPS(AtomicReference<QPSWindow> windowRef) {
        QPSWindow window = windowRef.get();
        long now = System.currentTimeMillis();
        
        // 如果窗口已过期，返回0
        if (now - window.startTime >= 1000) {
            return 0;
        }
        
        return window.counter.get();
    }

    /**
     * 获取完整的QPS统计信息（带自动清理）
     */
    public QPSStatistics getQPSStatistics() {
        return QPSStatistics.builder()
                .globalQPS(getGlobalQPS())
                .apiQPS(getApiQPS())
                .ipQPS(getIpQPS())
                .userQPS(getUserQPS())
                .priorityQPS(getPriorityQPS())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 手动清理（保留作为备用方案）
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        int totalCleaned = 0;
        
        // 清理过期的API QPS数据
        int apiCleaned = cleanupMap(apiQPS, now, "API");
        totalCleaned += apiCleaned;
        
        // 清理过期的IP QPS数据
        int ipCleaned = cleanupMap(ipQPS, now, "IP");
        totalCleaned += ipCleaned;
        
        // 清理过期的用户QPS数据
        int userCleaned = cleanupMap(userQPS, now, "User");
        totalCleaned += userCleaned;
        
        // 清理过期的优先级QPS数据
        int priorityCleaned = cleanupMap(priorityQPS, now, "Priority");
        totalCleaned += priorityCleaned;
        
        log.info("Manual cleanup completed. Total cleaned: {}, Active entries: API={}, IP={}, User={}, Priority={}", 
                totalCleaned, apiQPS.size(), ipQPS.size(), userQPS.size(), priorityQPS.size());
    }

    /**
     * 清理指定Map的过期数据
     */
    private int cleanupMap(ConcurrentHashMap<String, AtomicReference<QPSWindow>> dataMap, long now, String type) {
        final AtomicInteger cleanedCount = new AtomicInteger(0);
        dataMap.entrySet().removeIf(entry -> {
            if (now - entry.getValue().get().startTime >= CLEANUP_THRESHOLD) {
                cleanedCount.incrementAndGet();
                return true;
            }
            return false;
        });
        
        if (cleanedCount.get() > 0) {
            log.debug("Cleaned {} expired {} QPS entries", cleanedCount.get(), type);
        }
        
        return cleanedCount.get();
    }

    /**
     * 获取内存使用统计
     */
    public Map<String, Object> getMemoryStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("apiQPSSize", apiQPS.size());
        stats.put("ipQPSSize", ipQPS.size());
        stats.put("userQPSSize", userQPS.size());
        stats.put("priorityQPSSize", priorityQPS.size());
        stats.put("totalEntries", apiQPS.size() + ipQPS.size() + userQPS.size() + priorityQPS.size());
        stats.put("cleanupThreshold", CLEANUP_THRESHOLD);
        stats.put("timestamp", System.currentTimeMillis());
        
        return stats;
    }

    /**
     * QPS滑动窗口
     */
    private static class QPSWindow {
        private final long startTime = System.currentTimeMillis();
        private final AtomicLong counter = new AtomicLong(0);
    }

    /**
     * QPS统计信息
     */
    public static class QPSStatistics {
        private final long globalQPS;
        private final Map<String, Long> apiQPS;
        private final Map<String, Long> ipQPS;
        private final Map<String, Long> userQPS;
        private final Map<String, Long> priorityQPS;
        private final long timestamp;

        private QPSStatistics(Builder builder) {
            this.globalQPS = builder.globalQPS;
            this.apiQPS = builder.apiQPS;
            this.ipQPS = builder.ipQPS;
            this.userQPS = builder.userQPS;
            this.priorityQPS = builder.priorityQPS;
            this.timestamp = builder.timestamp;
        }

        public static Builder builder() {
            return new Builder();
        }

        public long getGlobalQPS() { return globalQPS; }
        public Map<String, Long> getApiQPS() { return apiQPS; }
        public Map<String, Long> getIpQPS() { return ipQPS; }
        public Map<String, Long> getUserQPS() { return userQPS; }
        public Map<String, Long> getPriorityQPS() { return priorityQPS; }
        public long getTimestamp() { return timestamp; }

        public static class Builder {
            private long globalQPS;
            private Map<String, Long> apiQPS;
            private Map<String, Long> ipQPS;
            private Map<String, Long> userQPS;
            private Map<String, Long> priorityQPS;
            private long timestamp;

            public Builder globalQPS(long globalQPS) {
                this.globalQPS = globalQPS;
                return this;
            }

            public Builder apiQPS(Map<String, Long> apiQPS) {
                this.apiQPS = apiQPS;
                return this;
            }

            public Builder ipQPS(Map<String, Long> ipQPS) {
                this.ipQPS = ipQPS;
                return this;
            }

            public Builder userQPS(Map<String, Long> userQPS) {
                this.userQPS = userQPS;
                return this;
            }

            public Builder priorityQPS(Map<String, Long> priorityQPS) {
                this.priorityQPS = priorityQPS;
                return this;
            }

            public Builder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public QPSStatistics build() {
                return new QPSStatistics(this);
            }
        }
    }
} 