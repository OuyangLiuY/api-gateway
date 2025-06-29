package com.citi.tts.api.gateway.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于LRU缓存的QPS统计组件
 * 自动清理最少使用的数据，无需定时任务
 */
@Slf4j
@Component
public class LRUQPSMetrics {

    // 全局QPS统计
    private final AtomicReference<QPSWindow> globalQPS = new AtomicReference<>(new QPSWindow());
    
    // 基于LRU的API路径QPS统计
    private final LRUCache<String, AtomicReference<QPSWindow>> apiQPS;
    
    // 基于LRU的IP QPS统计
    private final LRUCache<String, AtomicReference<QPSWindow>> ipQPS;
    
    // 基于LRU的用户QPS统计
    private final LRUCache<String, AtomicReference<QPSWindow>> userQPS;
    
    // 基于LRU的优先级QPS统计
    private final LRUCache<String, AtomicReference<QPSWindow>> priorityQPS;

    public LRUQPSMetrics() {
        // 初始化LRU缓存，设置最大容量
        this.apiQPS = new LRUCache<>(1000); // 最多1000个API路径
        this.ipQPS = new LRUCache<>(5000);  // 最多5000个IP
        this.userQPS = new LRUCache<>(10000); // 最多10000个用户
        this.priorityQPS = new LRUCache<>(10); // 最多10个优先级
    }

    /**
     * 记录请求
     */
    public void recordRequest(String path, String ip, String userId, String priority) {
        long now = System.currentTimeMillis();
        
        // 全局QPS统计
        recordToWindow(globalQPS, now);
        
        // API路径QPS统计
        if (path != null) {
            AtomicReference<QPSWindow> windowRef = apiQPS.get(path);
            if (windowRef == null) {
                windowRef = new AtomicReference<>(new QPSWindow());
                apiQPS.put(path, windowRef);
            }
            recordToWindow(windowRef, now);
        }
        
        // IP QPS统计
        if (ip != null) {
            AtomicReference<QPSWindow> windowRef = ipQPS.get(ip);
            if (windowRef == null) {
                windowRef = new AtomicReference<>(new QPSWindow());
                ipQPS.put(ip, windowRef);
            }
            recordToWindow(windowRef, now);
        }
        
        // 用户QPS统计
        if (userId != null) {
            AtomicReference<QPSWindow> windowRef = userQPS.get(userId);
            if (windowRef == null) {
                windowRef = new AtomicReference<>(new QPSWindow());
                userQPS.put(userId, windowRef);
            }
            recordToWindow(windowRef, now);
        }
        
        // 优先级QPS统计
        if (priority != null) {
            AtomicReference<QPSWindow> windowRef = priorityQPS.get(priority);
            if (windowRef == null) {
                windowRef = new AtomicReference<>(new QPSWindow());
                priorityQPS.put(priority, windowRef);
            }
            recordToWindow(windowRef, now);
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
     * 获取全局QPS
     */
    public long getGlobalQPS() {
        return getWindowQPS(globalQPS);
    }

    /**
     * 获取API路径QPS
     */
    public Map<String, Long> getApiQPS() {
        return getQPSFromLRU(apiQPS, "API");
    }

    /**
     * 获取IP QPS
     */
    public Map<String, Long> getIpQPS() {
        return getQPSFromLRU(ipQPS, "IP");
    }

    /**
     * 获取用户QPS
     */
    public Map<String, Long> getUserQPS() {
        return getQPSFromLRU(userQPS, "User");
    }

    /**
     * 获取优先级QPS
     */
    public Map<String, Long> getPriorityQPS() {
        return getQPSFromLRU(priorityQPS, "Priority");
    }

    /**
     * 从LRU缓存获取QPS数据
     */
    private Map<String, Long> getQPSFromLRU(LRUCache<String, AtomicReference<QPSWindow>> lruCache, String type) {
        Map<String, Long> result = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        
        // 遍历LRU缓存，清理过期数据
        lruCache.entrySet().removeIf(entry -> {
            QPSWindow window = entry.getValue().get();
            if (now - window.startTime >= 60000) { // 1分钟过期
                log.debug("Removing expired {} QPS entry: {}", type, entry.getKey());
                return true;
            } else {
                result.put(entry.getKey(), window.counter.get());
                return false;
            }
        });
        
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
     * 获取完整的QPS统计信息
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
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("apiQPSSize", apiQPS.size());
        stats.put("ipQPSSize", ipQPS.size());
        stats.put("userQPSSize", userQPS.size());
        stats.put("priorityQPSSize", priorityQPS.size());
        stats.put("apiQPSMaxSize", apiQPS.getMaxSize());
        stats.put("ipQPSMaxSize", ipQPS.getMaxSize());
        stats.put("userQPSMaxSize", userQPS.getMaxSize());
        stats.put("priorityQPSMaxSize", priorityQPS.getMaxSize());
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
     * LRU缓存实现
     */
    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        public LRUCache(int maxSize) {
            super(maxSize, 0.75f, true); // accessOrder=true 表示按访问顺序排序
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            boolean shouldRemove = size() > maxSize;
            if (shouldRemove) {
                log.debug("LRU cache full, removing eldest entry: {}", eldest.getKey());
            }
            return shouldRemove;
        }

        public int getMaxSize() {
            return maxSize;
        }
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