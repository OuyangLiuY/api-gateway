package com.citi.tts.api.gateway.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Iterator;

/**
 * 高级LRU缓存QPS统计组件
 * 支持基于时间和访问频率的多维度淘汰策略
 */
@Slf4j
@Component
public class AdvancedLRUQPSMetrics {

    // 全局QPS统计
    private final AtomicReference<QPSWindow> globalQPS = new AtomicReference<>(new QPSWindow());
    
    // 基于高级LRU的API路径QPS统计
    private final AdvancedLRUCache<String, AtomicReference<QPSWindow>> apiQPS;
    
    // 基于高级LRU的IP QPS统计
    private final AdvancedLRUCache<String, AtomicReference<QPSWindow>> ipQPS;
    
    // 基于高级LRU的用户QPS统计
    private final AdvancedLRUCache<String, AtomicReference<QPSWindow>> userQPS;
    
    // 基于高级LRU的优先级QPS统计
    private final AdvancedLRUCache<String, AtomicReference<QPSWindow>> priorityQPS;

    public AdvancedLRUQPSMetrics() {
        // 初始化高级LRU缓存，设置最大容量和过期时间
        this.apiQPS = new AdvancedLRUCache<>(1000, 60000); // 1000个API，60秒过期
        this.ipQPS = new AdvancedLRUCache<>(5000, 60000);  // 5000个IP，60秒过期
        this.userQPS = new AdvancedLRUCache<>(10000, 60000); // 10000个用户，60秒过期
        this.priorityQPS = new AdvancedLRUCache<>(10, 60000); // 10个优先级，60秒过期
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
            AtomicReference<QPSWindow> windowRef = apiQPS.getValue(path);
            if (windowRef == null) {
                windowRef = new AtomicReference<>(new QPSWindow());
                apiQPS.put(path, windowRef, now);
            }
            recordToWindow(windowRef, now);
        }
        
        // IP QPS统计
        if (ip != null) {
            AtomicReference<QPSWindow> windowRef = ipQPS.getValue(ip);
            if (windowRef == null) {
                windowRef = new AtomicReference<>(new QPSWindow());
                ipQPS.put(ip, windowRef, now);
            }
            recordToWindow(windowRef, now);
        }
        
        // 用户QPS统计
        if (userId != null) {
            AtomicReference<QPSWindow> windowRef = userQPS.getValue(userId);
            if (windowRef == null) {
                windowRef = new AtomicReference<>(new QPSWindow());
                userQPS.put(userId, windowRef, now);
            }
            recordToWindow(windowRef, now);
        }
        
        // 优先级QPS统计
        if (priority != null) {
            AtomicReference<QPSWindow> windowRef = priorityQPS.getValue(priority);
            if (windowRef == null) {
                windowRef = new AtomicReference<>(new QPSWindow());
                priorityQPS.put(priority, windowRef, now);
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
        return getQPSFromAdvancedLRU(apiQPS, "API");
    }

    /**
     * 获取IP QPS
     */
    public Map<String, Long> getIpQPS() {
        return getQPSFromAdvancedLRU(ipQPS, "IP");
    }

    /**
     * 获取用户QPS
     */
    public Map<String, Long> getUserQPS() {
        return getQPSFromAdvancedLRU(userQPS, "User");
    }

    /**
     * 获取优先级QPS
     */
    public Map<String, Long> getPriorityQPS() {
        return getQPSFromAdvancedLRU(priorityQPS, "Priority");
    }

    /**
     * 从高级LRU缓存获取QPS数据
     */
    private Map<String, Long> getQPSFromAdvancedLRU(AdvancedLRUCache<String, AtomicReference<QPSWindow>> lruCache, String type) {
        Map<String, Long> result = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        
        // 清理过期数据并获取清理统计
        int cleanedCount = lruCache.cleanupExpired(now);
        if (cleanedCount > 0) {
            log.debug("{} QPS cache: cleaned {} expired entries", type, cleanedCount);
        }
        
        // 获取所有有效数据
        for (Map.Entry<String, CacheEntry<AtomicReference<QPSWindow>>> entry : lruCache.entrySet()) {
            QPSWindow window = entry.getValue().value.get();
            if (now - window.startTime < 1000) { // 1秒窗口内的数据
                result.put(entry.getKey(), window.counter.get());
            }
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
        long now = System.currentTimeMillis();
        
        // 基础统计
        stats.put("apiQPSSize", apiQPS.size());
        stats.put("ipQPSSize", ipQPS.size());
        stats.put("userQPSSize", userQPS.size());
        stats.put("priorityQPSSize", priorityQPS.size());
        stats.put("apiQPSMaxSize", apiQPS.getMaxSize());
        stats.put("ipQPSMaxSize", ipQPS.getMaxSize());
        stats.put("userQPSMaxSize", userQPS.getMaxSize());
        stats.put("priorityQPSMaxSize", priorityQPS.getMaxSize());
        
        // 过期统计
        stats.put("apiQPSExpiredCount", apiQPS.getExpiredCount(now));
        stats.put("ipQPSExpiredCount", ipQPS.getExpiredCount(now));
        stats.put("userQPSExpiredCount", userQPS.getExpiredCount(now));
        stats.put("priorityQPSExpiredCount", priorityQPS.getExpiredCount(now));
        
        // 详细使用统计
        stats.put("apiQPSUsage", apiQPS.getUsageStats(now));
        stats.put("ipQPSUsage", ipQPS.getUsageStats(now));
        stats.put("userQPSUsage", userQPS.getUsageStats(now));
        stats.put("priorityQPSUsage", priorityQPS.getUsageStats(now));
        
        stats.put("timestamp", now);
        
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
     * 缓存条目
     */
    private static class CacheEntry<V> {
        private final V value;
        private final long accessTime;
        private final long expireTime;

        public CacheEntry(V value, long accessTime, long expireTime) {
            this.value = value;
            this.accessTime = accessTime;
            this.expireTime = expireTime;
        }

        public V getValue() { return value; }
        public long getAccessTime() { return accessTime; }
        public long getExpireTime() { return expireTime; }
        public boolean isExpired(long now) { return now > expireTime; }
    }

    /**
     * 高级LRU缓存实现
     */
    private static class AdvancedLRUCache<K, V> extends LinkedHashMap<K, CacheEntry<V>> {
        private final int maxSize;
        private final long expireTimeMs;

        public AdvancedLRUCache(int maxSize, long expireTimeMs) {
            super(maxSize, 0.75f, true); // accessOrder=true
            this.maxSize = maxSize;
            this.expireTimeMs = expireTimeMs;
        }

        /**
         * 基于size和时间的双重淘汰策略
         */
        @Override
        protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
            long now = System.currentTimeMillis();
            CacheEntry<V> eldestEntry = eldest.getValue();
            
            // 策略1：容量淘汰 - 当缓存大小达到上限时
            boolean sizeLimitReached = size() >= maxSize;
            
            // 策略2：时间淘汰 - 当最老的条目已过期时
            boolean timeExpired = eldestEntry.isExpired(now);
            
            // 如果容量达到上限，先批量清理过期数据
            if (sizeLimitReached) {
                int cleanedCount = cleanupExpiredBatch(now);
                if (cleanedCount > 0) {
                    log.debug("LRU cache: cleaned {} expired entries before size limit check", cleanedCount);
                    // 清理后重新检查容量
                    sizeLimitReached = size() >= maxSize;
                }
            }
            
            // 任一条件满足就淘汰
            boolean shouldRemove = sizeLimitReached || timeExpired;
            
            if (shouldRemove) {
                if (sizeLimitReached && timeExpired) {
                    log.debug("LRU cache: removing eldest entry due to both size limit (size: {}) and time expiration (key: {}, expired: {}ms ago)", 
                            size(), eldest.getKey(), now - eldestEntry.getExpireTime());
                } else if (sizeLimitReached) {
                    log.debug("LRU cache: removing eldest entry due to size limit (size: {}, key: {})", 
                            size(), eldest.getKey());
                } else if (timeExpired) {
                    log.debug("LRU cache: removing eldest entry due to time expiration (key: {}, expired: {}ms ago)", 
                            eldest.getKey(), now - eldestEntry.getExpireTime());
                }
            }
            
            return shouldRemove;
        }

        /**
         * 批量清理过期数据
         * @return 清理的条目数量
         */
        private int cleanupExpiredBatch(long currentTime) {
            int cleanedCount = 0;
            Iterator<Map.Entry<K, CacheEntry<V>>> iterator = entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<K, CacheEntry<V>> entry = iterator.next();
                if (entry.getValue().isExpired(currentTime)) {
                    iterator.remove();
                    cleanedCount++;
                    log.trace("LRU cache: cleaned expired entry: {}", entry.getKey());
                }
            }
            
            return cleanedCount;
        }

        /**
         * 获取缓存条目，更新访问时间
         */
        public V getValue(K key) {
            CacheEntry<V> entry = super.get(key);
            if (entry != null) {
                // 更新访问时间
                long now = System.currentTimeMillis();
                if (!entry.isExpired(now)) {
                    // 重新插入以更新访问顺序
                    remove(key);
                    put(key, new CacheEntry<>(entry.getValue(), now, entry.getExpireTime()));
                    return entry.getValue();
                } else {
                    // 已过期，移除
                    remove(key);
                    log.debug("Removing expired entry: {}", key);
                }
            }
            return null;
        }

        /**
         * 放入缓存条目
         */
        public void put(K key, V value, long currentTime) {
            long expireTime = currentTime + expireTimeMs;
            put(key, new CacheEntry<>(value, currentTime, expireTime));
        }

        /**
         * 清理过期数据
         * @return 清理的条目数量
         */
        public int cleanupExpired(long currentTime) {
            int cleanedCount = cleanupExpiredBatch(currentTime);
            if (cleanedCount > 0) {
                log.debug("LRU cache: cleaned {} expired entries", cleanedCount);
            }
            return cleanedCount;
        }

        /**
         * 获取过期数据数量
         */
        public int getExpiredCount(long currentTime) {
            return (int) entrySet().stream()
                    .filter(entry -> entry.getValue().isExpired(currentTime))
                    .count();
        }

        public int getMaxSize() {
            return maxSize;
        }

        /**
         * 获取缓存使用统计信息
         */
        public Map<String, Object> getUsageStats(long currentTime) {
            Map<String, Object> stats = new ConcurrentHashMap<>();
            stats.put("size", size());
            stats.put("maxSize", maxSize);
            stats.put("usagePercentage", (double) size() / maxSize * 100);
            stats.put("expiredCount", getExpiredCount(currentTime));
            stats.put("validCount", size() - getExpiredCount(currentTime));
            stats.put("timestamp", currentTime);
            return stats;
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