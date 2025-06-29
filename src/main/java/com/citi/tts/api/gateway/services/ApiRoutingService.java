package com.citi.tts.api.gateway.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API路由服务
 * 根据API优先级选择对应的线程池
 */
@Slf4j
@Service
public class ApiRoutingService {

    @Autowired
    @Qualifier("coreApiThreadPool")
    private ThreadPoolExecutor coreApiThreadPool;

    @Autowired
    @Qualifier("normalApiThreadPool")
    private ThreadPoolExecutor normalApiThreadPool;

    @Autowired
    @Qualifier("defaultThreadPool")
    private ThreadPoolExecutor defaultThreadPool;

    @Autowired
    @Qualifier("cryptoExecutor")
    private ThreadPoolExecutor cryptoExecutorPool;


    // API路径到优先级的映射
    private final ConcurrentHashMap<String, ApiPriority> apiPriorityMap = new ConcurrentHashMap<>();
    
    // 请求计数器
    private final AtomicLong coreApiCounter = new AtomicLong(0);
    private final AtomicLong normalApiCounter = new AtomicLong(0);
    private final AtomicLong nonCoreApiCounter = new AtomicLong(0);
    private final AtomicLong cryptoCounter = new AtomicLong(0);

    /**
     * API优先级枚举
     */
    public enum ApiPriority {
        CORE,       // 核心API：支付、转账等
        NORMAL,     // 普通API：一般业务
        NON_CORE,   // 非核心API：查询、统计等
        CRYPTO      // 加解密API
    }

    /**
     * 初始化API优先级映射
     */
    @PostConstruct
    public void initApiPriorityMap() {
        // 核心API
        apiPriorityMap.put("/api/payment/process", ApiPriority.CORE);
        apiPriorityMap.put("/api/transfer", ApiPriority.CORE);
        apiPriorityMap.put("/api/withdraw", ApiPriority.CORE);
        apiPriorityMap.put("/api/deposit", ApiPriority.CORE);
        apiPriorityMap.put("/api/account/create", ApiPriority.CORE);
        apiPriorityMap.put("/api/account/close", ApiPriority.CORE);

        // 普通API
        apiPriorityMap.put("/api/account/balance", ApiPriority.NORMAL);
        apiPriorityMap.put("/api/transaction/history", ApiPriority.NORMAL);
        apiPriorityMap.put("/api/user/profile", ApiPriority.NORMAL);
        apiPriorityMap.put("/api/notification", ApiPriority.NORMAL);

        // 非核心API
        apiPriorityMap.put("/api/statistics", ApiPriority.NON_CORE);
        apiPriorityMap.put("/api/reports", ApiPriority.NON_CORE);
        apiPriorityMap.put("/api/analytics", ApiPriority.NON_CORE);
        apiPriorityMap.put("/api/logs", ApiPriority.NON_CORE);
        apiPriorityMap.put("/api/statement", ApiPriority.NON_CORE);

        log.info("API priority map initialized with {} entries", apiPriorityMap.size());
    }

    public Map<String,ApiPriority> getApiPriorityMap(){
        return apiPriorityMap;
    }

    /**
     * 根据API路径获取优先级
     */
    public ApiPriority getApiPriority(String apiPath) {
        // 精确匹配
        ApiPriority priority = apiPriorityMap.get(apiPath);
        if (priority != null) {
            return priority;
        }

        // 前缀匹配
        for (String pattern : apiPriorityMap.keySet()) {
            if (apiPath.startsWith(pattern)) {
                return apiPriorityMap.get(pattern);
            }
        }

        // 默认返回普通优先级
        return ApiPriority.NON_CORE;
    }

    /**
     * 根据API优先级选择线程池
     */
    public ThreadPoolExecutor getThreadPool(ApiPriority priority) {
        return switch (priority) {
            case CORE -> coreApiThreadPool;
            case NORMAL -> normalApiThreadPool;
            default -> defaultThreadPool;
        };
    }

    /**
     * 异步执行API任务
     */
    public <T> CompletableFuture<T> executeAsync(String apiPath, ApiTask<T> task) {
        ApiPriority priority = getApiPriority(apiPath);
        ThreadPoolExecutor threadPool = getThreadPool(priority);
        
        // 更新计数器
        updateCounter(priority);
        
        log.debug("Executing API: {} with priority: {} in thread pool: {}", 
                apiPath, priority, threadPool.getPoolSize());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.execute();
            } catch (Exception e) {
                log.error("API execution failed: {}", apiPath, e);
                throw new RuntimeException("API execution failed", e);
            }
        }, threadPool);
    }

    /**
     * 同步执行API任务
     */
    public <T> T executeSync(String apiPath, ApiTask<T> task) {
        ApiPriority priority = getApiPriority(apiPath);
        ThreadPoolExecutor threadPool = getThreadPool(priority);
        
        // 更新计数器
        updateCounter(priority);
        
        log.debug("Executing API: {} with priority: {} in thread pool: {}", 
                apiPath, priority, threadPool.getPoolSize());
        
        try {
            return task.execute();
        } catch (Exception e) {
            log.error("API execution failed: {}", apiPath, e);
            throw new RuntimeException("API execution failed", e);
        }
    }

    /**
     * 更新请求计数器
     */
    private void updateCounter(ApiPriority priority) {
        switch (priority) {
            case CORE -> coreApiCounter.incrementAndGet();
            case NORMAL -> normalApiCounter.incrementAndGet();
            case NON_CORE -> nonCoreApiCounter.incrementAndGet();
            case CRYPTO -> cryptoCounter.incrementAndGet();
        }
    }

    /**
     * 获取API统计信息
     */
    public ApiStatistics getApiStatistics() {
        return ApiStatistics.builder()
                .coreApiCount(coreApiCounter.get())
                .normalApiCount(normalApiCounter.get())
                .nonCoreApiCount(nonCoreApiCounter.get())
                .cryptoCount(cryptoCounter.get())
                .totalCount(coreApiCounter.get() + normalApiCounter.get() + 
                           nonCoreApiCounter.get() + cryptoCounter.get())
                .build();
    }

    /**
     * 获取线程池状态
     */
    public ThreadPoolStatus getThreadPoolStatus() {
        return ThreadPoolStatus.builder()
                .coreApiStatus(getPoolStatus(coreApiThreadPool, "Core API"))
                .normalApiStatus(getPoolStatus(normalApiThreadPool, "Normal API"))
                .nonCoreApiStatus(getPoolStatus(defaultThreadPool, "Non-Core API"))
                .cryptoStatus(getPoolStatus(cryptoExecutorPool, "Crypto"))
                .build();
    }

    /**
     * 获取单个线程池状态
     */
    private PoolStatus getPoolStatus(ThreadPoolExecutor pool, String name) {
        if (pool == null) {
            return PoolStatus.builder()
                    .name(name)
                    .status("Not Available")
                    .build();
        }

        return PoolStatus.builder()
                .name(name)
                .activeCount(pool.getActiveCount())
                .poolSize(pool.getPoolSize())
                .maximumPoolSize(pool.getMaximumPoolSize())
                .queueSize(pool.getQueue().size())
                .completedTasks(pool.getCompletedTaskCount())
                .utilization((double) pool.getActiveCount() / pool.getMaximumPoolSize())
                .status("Running")
                .build();
    }

    /**
     * API任务接口
     */
    @FunctionalInterface
    public interface ApiTask<T> {
        T execute() throws Exception;
    }

    /**
     * API统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class ApiStatistics {
        private long coreApiCount;
        private long normalApiCount;
        private long nonCoreApiCount;
        private long cryptoCount;
        private long totalCount;
    }

    /**
     * 线程池状态
     */
    @lombok.Builder
    @lombok.Data
    public static class ThreadPoolStatus {
        private PoolStatus coreApiStatus;
        private PoolStatus normalApiStatus;
        private PoolStatus nonCoreApiStatus;
        private PoolStatus cryptoStatus;
    }

    /**
     * 单个线程池状态
     */
    @lombok.Builder
    @lombok.Data
    public static class PoolStatus {
        private String name;
        private int activeCount;
        private int poolSize;
        private int maximumPoolSize;
        private int queueSize;
        private long completedTasks;
        private double utilization;
        private String status;
    }
} 