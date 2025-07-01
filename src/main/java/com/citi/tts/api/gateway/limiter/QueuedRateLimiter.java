package com.citi.tts.api.gateway.limiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 排队限流器
 * 当限流超出时，将请求放入队列等待处理，而不是直接丢弃
 * 支持降级服务兜底机制
 */
@Slf4j
@Component
public class QueuedRateLimiter {

    /**
     * 排队请求包装器
     */
    public static class QueuedRequest<T> {
        private final String key;
        private final Supplier<Mono<T>> requestSupplier;
        private final long queueTime;
        private final CompletableFuture<T> future;
        private final int priority; // 优先级：0-最高，9-最低

        public QueuedRequest(String key, Supplier<Mono<T>> requestSupplier, int priority) {
            this.key = key;
            this.requestSupplier = requestSupplier;
            this.queueTime = System.currentTimeMillis();
            this.future = new CompletableFuture<>();
            this.priority = priority;
        }

        public String getKey() { return key; }
        public Supplier<Mono<T>> getRequestSupplier() { return requestSupplier; }
        public long getQueueTime() { return queueTime; }
        public CompletableFuture<T> getFuture() { return future; }
        public int getPriority() { return priority; }
        public long getWaitTime() { return System.currentTimeMillis() - queueTime; }
    }

    /**
     * 排队限流器配置
     */
    public static class QueueConfig {
        private final int maxQueueSize;
        private final Duration maxWaitTime;
        private final int maxConcurrency;
        private final boolean enablePriority;
        private final boolean enableFallback; // 是否启用降级服务
        private final Duration fallbackTimeout; // 降级服务超时时间

        public QueueConfig(int maxQueueSize, Duration maxWaitTime, int maxConcurrency, boolean enablePriority) {
            this(maxQueueSize, maxWaitTime, maxConcurrency, enablePriority, true, Duration.ofSeconds(5));
        }

        public QueueConfig(int maxQueueSize, Duration maxWaitTime, int maxConcurrency, boolean enablePriority, 
                          boolean enableFallback, Duration fallbackTimeout) {
            this.maxQueueSize = maxQueueSize;
            this.maxWaitTime = maxWaitTime;
            this.maxConcurrency = maxConcurrency;
            this.enablePriority = enablePriority;
            this.enableFallback = enableFallback;
            this.fallbackTimeout = fallbackTimeout;
        }

        public int getMaxQueueSize() { return maxQueueSize; }
        public Duration getMaxWaitTime() { return maxWaitTime; }
        public int getMaxConcurrency() { return maxConcurrency; }
        public boolean isEnablePriority() { return enablePriority; }
        public boolean isEnableFallback() { return enableFallback; }
        public Duration getFallbackTimeout() { return fallbackTimeout; }
    }

    // 按限流类型分组的队列
    private final ConcurrentHashMap<String, BlockingQueue<QueuedRequest<?>>> queues = new ConcurrentHashMap<>();
    
    // 队列处理器
    private final ConcurrentHashMap<String, ScheduledExecutorService> queueProcessors = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicLong totalQueuedRequests = new AtomicLong(0);
    private final AtomicLong totalProcessedRequests = new AtomicLong(0);
    private final AtomicLong totalRejectedRequests = new AtomicLong(0);
    private final AtomicLong totalTimeoutRequests = new AtomicLong(0);
    private final AtomicLong totalFallbackRequests = new AtomicLong(0);

    /**
     * 带排队的限流检查
     */
    public <T> Mono<T> rateLimitWithQueue(String key, Supplier<Mono<T>> requestSupplier, 
                                         Supplier<Boolean> rateLimitCheck, QueueConfig config) {
        return rateLimitWithQueue(key, requestSupplier, rateLimitCheck, config, 5, null); // 默认优先级5，无降级服务
    }

    /**
     * 带排队的限流检查（指定优先级）
     */
    public <T> Mono<T> rateLimitWithQueue(String key, Supplier<Mono<T>> requestSupplier, 
                                         Supplier<Boolean> rateLimitCheck, QueueConfig config, int priority) {
        return rateLimitWithQueue(key, requestSupplier, rateLimitCheck, config, priority, null);
    }

    /**
     * 带排队的限流检查（支持降级服务）
     */
    public <T> Mono<T> rateLimitWithQueue(String key, Supplier<Mono<T>> requestSupplier, 
                                         Supplier<Boolean> rateLimitCheck, QueueConfig config, int priority,
                                         Function<String, Mono<T>> fallbackService) {
        // 首先尝试直接通过限流检查
        if (rateLimitCheck.get()) {
            return requestSupplier.get();
        }

        // 限流超出，尝试排队
        return queueRequest(key, requestSupplier, config, priority, fallbackService);
    }

    /**
     * 排队请求
     */
    private <T> Mono<T> queueRequest(String key, Supplier<Mono<T>> requestSupplier, 
                                    QueueConfig config, int priority,
                                    Function<String, Mono<T>> fallbackService) {
        BlockingQueue<QueuedRequest<?>> queue = getOrCreateQueue(key, config);
        
        // 检查队列是否已满
        if (queue.size() >= config.getMaxQueueSize()) {
            totalRejectedRequests.incrementAndGet();
            log.warn("Queue is full for key: {}, queue size: {}", key, queue.size());
            
            // 如果启用了降级服务，调用降级服务
            if (config.isEnableFallback() && fallbackService != null) {
                totalFallbackRequests.incrementAndGet();
                log.info("Queue full, calling fallback service for key: {}", key);
                return fallbackService.apply(key)
                        .timeout(config.getFallbackTimeout())
                        .doOnSuccess(result -> log.debug("Fallback service succeeded for key: {}", key))
                        .doOnError(error -> log.error("Fallback service failed for key: {}", key, error));
            }
            
            // 没有降级服务或降级服务未启用，返回错误
            return Mono.error(new RuntimeException("Queue is full, request rejected"));
        }

        // 创建排队请求
        QueuedRequest<T> queuedRequest = new QueuedRequest<>(key, requestSupplier, priority);
        totalQueuedRequests.incrementAndGet();

        log.debug("Request queued for key: {}, queue size: {}, priority: {}", 
                key, queue.size() + 1, priority);

        // 添加到队列
        try {
            if (config.isEnablePriority()) {
                // 优先级队列（简化实现，实际可以使用PriorityBlockingQueue）
                addToPriorityQueue(queue, queuedRequest);
            } else {
                queue.offer(queuedRequest, config.getMaxWaitTime().toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            totalRejectedRequests.incrementAndGet();
            return Mono.error(new RuntimeException("Request queuing interrupted"));
        }

        // 启动队列处理器（如果还没有启动）
        startQueueProcessor(key, config);

        // 返回Future的Mono包装
        return Mono.fromFuture(queuedRequest.getFuture())
                .timeout(config.getMaxWaitTime())
                .doOnError(e -> {
                    if (e instanceof TimeoutException) {
                        totalTimeoutRequests.incrementAndGet();
                        log.warn("Request timeout in queue for key: {}, wait time: {}ms", 
                                key, queuedRequest.getWaitTime());
                    }
                });
    }

    /**
     * 获取或创建队列
     */
    private BlockingQueue<QueuedRequest<?>> getOrCreateQueue(String key, QueueConfig config) {
        return queues.computeIfAbsent(key, k -> {
            if (config.isEnablePriority()) {
                return new PriorityBlockingQueue<>(config.getMaxQueueSize(), 
                    (r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()));
            } else {
                return new LinkedBlockingQueue<>(config.getMaxQueueSize());
            }
        });
    }

    /**
     * 添加到优先级队列
     */
    private void addToPriorityQueue(BlockingQueue<QueuedRequest<?>> queue, QueuedRequest<?> request) {
        try {
            queue.offer(request, 100, TimeUnit.MILLISECONDS); // 短暂等待
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to add request to priority queue", e);
        }
    }

    /**
     * 启动队列处理器
     */
    private void startQueueProcessor(String key, QueueConfig config) {
        queueProcessors.computeIfAbsent(key, k -> {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "queue-processor-" + key);
                t.setDaemon(true);
                return t;
            });

            // 定期处理队列中的请求
            executor.scheduleWithFixedDelay(() -> {
                processQueue(key, config);
            }, 0, 100, TimeUnit.MILLISECONDS); // 每100ms处理一次

            log.info("Started queue processor for key: {}", key);
            return executor;
        });
    }

    /**
     * 立即处理队列（新增：用于高优先级请求）
     */
    public void processQueueImmediately(String key) {
        QueuedRateLimiter.QueueConfig config = getDefaultConfig();
        processQueue(key, config);
    }

    /**
     * 批量处理队列（新增：用于批量请求场景）
     */
    public void processQueueBatch(String key, int batchSize) {
        QueuedRateLimiter.QueueConfig config = getDefaultConfig();
        processQueueBatch(key, config, batchSize);
    }

    /**
     * 自适应处理队列（新增：根据队列大小动态调整处理频率）
     */
    public void processQueueAdaptive(String key) {
        QueuedRateLimiter.QueueConfig config = getDefaultConfig();
        processQueueAdaptive(key, config);
    }

    /**
     * 获取默认配置
     */
    private QueueConfig getDefaultConfig() {
        return new QueueConfig(1000, Duration.ofSeconds(30), 10, true, true, Duration.ofSeconds(5));
    }

    /**
     * 批量处理队列
     */
    private void processQueueBatch(String key, QueueConfig config, int batchSize) {
        BlockingQueue<QueuedRequest<?>> queue = queues.get(key);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        // 检查请求是否超时
        long now = System.currentTimeMillis();
        QueuedRequest<?> request = queue.peek();
        
        if (request != null && (now - request.getQueueTime()) > config.getMaxWaitTime().toMillis()) {
            // 请求超时，移除并标记失败
            queue.poll();
            request.getFuture().completeExceptionally(
                new TimeoutException("Request timeout in queue: " + (now - request.getQueueTime()) + "ms"));
            totalTimeoutRequests.incrementAndGet();
            log.warn("Request timeout removed from queue for key: {}, wait time: {}ms", 
                    key, now - request.getQueueTime());
            return;
        }

        // 批量处理请求
        int processedCount = 0;
        int maxProcess = Math.min(batchSize, config.getMaxConcurrency());
        
        while (processedCount < maxProcess && !queue.isEmpty()) {
            request = queue.poll();
            if (request != null) {
                processQueuedRequest(request);
                processedCount++;
            }
        }
        
        log.debug("Batch processed {} requests for queue key: {}", processedCount, key);
    }

    /**
     * 自适应处理队列
     */
    private void processQueueAdaptive(String key, QueueConfig config) {
        BlockingQueue<QueuedRequest<?>> queue = queues.get(key);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        int queueSize = queue.size();
        
        // 根据队列大小动态调整处理策略
        if (queueSize > config.getMaxQueueSize() * 0.8) {
            // 队列接近满了，加快处理速度
            processQueueBatch(key, config, config.getMaxConcurrency() * 2);
            log.debug("High load detected, accelerated processing for queue key: {}", key);
        } else if (queueSize > config.getMaxQueueSize() * 0.5) {
            // 队列中等负载，正常处理
            processQueueBatch(key, config, config.getMaxConcurrency());
            log.debug("Medium load, normal processing for queue key: {}", key);
        } else {
            // 队列负载较低，减少处理频率
            processQueueBatch(key, config, Math.max(1, config.getMaxConcurrency() / 2));
            log.debug("Low load, reduced processing for queue key: {}", key);
        }
    }

    /**
     * 处理队列中的请求
     */
    private void processQueue(String key, QueueConfig config) {
        BlockingQueue<QueuedRequest<?>> queue = queues.get(key);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        // 检查请求是否超时
        long now = System.currentTimeMillis();
        QueuedRequest<?> request = queue.peek();
        
        if (request != null && (now - request.getQueueTime()) > config.getMaxWaitTime().toMillis()) {
            // 请求超时，移除并标记失败
            queue.poll();
            request.getFuture().completeExceptionally(
                new TimeoutException("Request timeout in queue: " + (now - request.getQueueTime()) + "ms"));
            totalTimeoutRequests.incrementAndGet();
            log.warn("Request timeout removed from queue for key: {}, wait time: {}ms", 
                    key, now - request.getQueueTime());
            return;
        }

        // 尝试处理队列中的请求
        int processedCount = 0;
        while (processedCount < config.getMaxConcurrency() && !queue.isEmpty()) {
            request = queue.poll();
            if (request != null) {
                processQueuedRequest(request);
                processedCount++;
            }
        }
    }

    /**
     * 处理单个排队请求
     */
    @SuppressWarnings("unchecked")
    private void processQueuedRequest(QueuedRequest<?> request) {
        long waitTime = request.getWaitTime();
        log.debug("Processing queued request for key: {}, wait time: {}ms", request.getKey(), waitTime);

        // 执行请求
        request.getRequestSupplier().get()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    result -> {
                        ((CompletableFuture<Object>) request.getFuture()).complete(result);
                        totalProcessedRequests.incrementAndGet();
                        log.debug("Queued request completed successfully for key: {}, wait time: {}ms", 
                                request.getKey(), waitTime);
                    },
                    error -> {
                        request.getFuture().completeExceptionally(error);
                        log.error("Queued request failed for key: {}, wait time: {}ms", 
                                request.getKey(), waitTime, error);
                    }
                );
    }

    /**
     * 获取队列统计信息
     */
    public QueueStats getQueueStats() {
        return QueueStats.builder()
                .totalQueuedRequests(totalQueuedRequests.get())
                .totalProcessedRequests(totalProcessedRequests.get())
                .totalRejectedRequests(totalRejectedRequests.get())
                .totalTimeoutRequests(totalTimeoutRequests.get())
                .totalFallbackRequests(totalFallbackRequests.get())
                .activeQueues(queues.size())
                .activeProcessors(queueProcessors.size())
                .build();
    }

    /**
     * 获取特定队列的状态
     */
    public QueueStatus getQueueStatus(String key) {
        BlockingQueue<QueuedRequest<?>> queue = queues.get(key);
        if (queue == null) {
            return null;
        }

        return QueueStatus.builder()
                .key(key)
                .queueSize(queue.size())
                .hasProcessor(queueProcessors.containsKey(key))
                .build();
    }

    /**
     * 清空指定队列
     */
    public void clearQueue(String key) {
        BlockingQueue<QueuedRequest<?>> queue = queues.remove(key);
        if (queue != null) {
            // 标记所有排队请求为失败
            QueuedRequest<?> request;
            while ((request = queue.poll()) != null) {
                request.getFuture().completeExceptionally(
                    new RuntimeException("Queue cleared"));
            }
            log.info("Cleared queue for key: {}", key);
        }

        // 停止队列处理器
        ScheduledExecutorService processor = queueProcessors.remove(key);
        if (processor != null) {
            processor.shutdown();
            log.info("Stopped queue processor for key: {}", key);
        }
    }

    /**
     * 队列统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class QueueStats {
        private long totalQueuedRequests;
        private long totalProcessedRequests;
        private long totalRejectedRequests;
        private long totalTimeoutRequests;
        private long totalFallbackRequests;
        private int activeQueues;
        private int activeProcessors;
    }

    /**
     * 队列状态信息
     */
    @lombok.Builder
    @lombok.Data
    public static class QueueStatus {
        private String key;
        private int queueSize;
        private boolean hasProcessor;
    }
} 