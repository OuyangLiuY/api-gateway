package com.citi.tts.api.gateway.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步审计日志服务
 * 设计理念：异步批量写入，提升性能，减少I/O阻塞
 * 
 * 特性：
 * 1. 异步处理：不阻塞业务线程
 * 2. 批量写入：减少I/O次数，提升吞吐量
 * 3. 内存缓冲：使用内存队列缓存日志
 * 4. 定时刷新：定期批量写入存储
 * 5. 故障容错：异常时降级到同步写入
 */
@Slf4j
@Service
public class AsyncAuditService implements AuditService {

    @Value("${audit.async.batch-size:100}")
    private int batchSize;

    @Value("${audit.async.flush-interval:5000}")
    private long flushIntervalMs;

    @Value("${audit.async.queue-size:10000}")
    private int queueSize;

    @Value("${audit.async.enabled:true}")
    private boolean asyncEnabled;

    // 异步处理线程池
    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "async-audit-worker");
        t.setDaemon(true);
        return t;
    });

    // 定时刷新线程池
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "async-audit-scheduler");
        t.setDaemon(true);
        return t;
    });

    // 日志队列
    private BlockingQueue<AuditLogEntry> logQueue;
    
    // 当前批次缓存
    private final AtomicReference<List<AuditLogEntry>> currentBatch = new AtomicReference<>(new ArrayList<>());
    
    // 统计信息
    private final AtomicLong totalLogs = new AtomicLong(0);
    private final AtomicLong batchWrites = new AtomicLong(0);
    private final AtomicLong failedWrites = new AtomicLong(0);
    private final AtomicLong asyncLogs = new AtomicLong(0);
    private final AtomicLong syncLogs = new AtomicLong(0);

    // 同步审计服务（降级使用）
    private final SyncAuditService syncAuditService = new SyncAuditService();

    public AsyncAuditService() {
        // 构造方法不再初始化logQueue，避免@Value注入时为0
    }

    @PostConstruct
    public void init() {
        // 在此处安全初始化logQueue
        this.logQueue = new LinkedBlockingQueue<>(queueSize > 0 ? queueSize : 10000);
        if (asyncEnabled) {
            // 启动异步处理线程
            asyncExecutor.submit(this::processLogsAsync);
            
            // 启动定时刷新任务
            scheduledExecutor.scheduleAtFixedRate(
                this::flushBatch, 
                flushIntervalMs, 
                flushIntervalMs, 
                TimeUnit.MILLISECONDS
            );
            
            log.info("Async audit service initialized - batchSize: {}, flushInterval: {}ms, queueSize: {}", 
                    batchSize, flushIntervalMs, queueSize);
        } else {
            log.info("Async audit service disabled, using sync mode");
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down async audit service...");
        
        // 刷新剩余日志
        flushBatch();
        
        // 关闭线程池
        asyncExecutor.shutdown();
        scheduledExecutor.shutdown();
        
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            asyncExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
        }
        
        log.info("Async audit service shutdown completed");
    }

    @Override
    public void log(String action, String message) {
        totalLogs.incrementAndGet();
        
        if (!asyncEnabled) {
            // 同步模式
            syncLogs.incrementAndGet();
            syncAuditService.log(action, message);
            return;
        }

        try {
            // 异步模式：添加到队列
            AuditLogEntry entry = new AuditLogEntry(action, message, System.currentTimeMillis());
            boolean offered = logQueue.offer(entry, 100, TimeUnit.MILLISECONDS);
            
            if (offered) {
                asyncLogs.incrementAndGet();
                log.debug("Audit log queued: {} - {}", action, message);
            } else {
                // 队列满，降级到同步写入
                syncLogs.incrementAndGet();
                log.warn("Audit queue full, falling back to sync write: {} - {}", action, message);
                syncAuditService.log(action, message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 中断时降级到同步写入
            syncLogs.incrementAndGet();
            syncAuditService.log(action, message);
        } catch (Exception e) {
            // 异常时降级到同步写入
            syncLogs.incrementAndGet();
            log.error("Failed to queue audit log, falling back to sync write", e);
            syncAuditService.log(action, message);
        }
    }

    /**
     * 异步处理日志
     */
    private void processLogsAsync() {
        log.info("Async audit log processor started");
        
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 从队列获取日志条目
                AuditLogEntry entry = logQueue.poll(1, TimeUnit.SECONDS);
                if (entry != null) {
                    // 添加到当前批次
                    List<AuditLogEntry> batch = currentBatch.get();
                    batch.add(entry);
                    
                    // 检查是否需要刷新批次
                    if (batch.size() >= batchSize) {
                        flushBatch();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in async audit log processor", e);
            }
        }
        
        log.info("Async audit log processor stopped");
    }

    /**
     * 刷新批次
     */
    private void flushBatch() {
        List<AuditLogEntry> batch = currentBatch.getAndSet(new ArrayList<>());
        if (batch.isEmpty()) {
            return;
        }

        try {
            // 批量写入日志
            writeBatch(batch);
            batchWrites.incrementAndGet();
            
            log.debug("Flushed {} audit logs in batch", batch.size());
        } catch (Exception e) {
            failedWrites.incrementAndGet();
            log.error("Failed to write audit log batch", e);
            
            // 失败时降级到同步写入
            for (AuditLogEntry entry : batch) {
                try {
                    syncAuditService.log(entry.getAction(), entry.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to write audit log entry: {}", entry, ex);
                }
            }
        }
    }

    /**
     * 批量写入日志
     */
    private void writeBatch(List<AuditLogEntry> batch) {
        StringBuilder batchLog = new StringBuilder();
        batchLog.append("=== Batch Audit Log ===\n");
        batchLog.append("Timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        batchLog.append("Batch Size: ").append(batch.size()).append("\n");
        batchLog.append("Entries:\n");
        
        for (AuditLogEntry entry : batch) {
            batchLog.append(String.format("[%s] %s: %s\n", 
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(entry.getTimestamp()), 
                    java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                entry.getAction(), 
                entry.getMessage()));
        }
        
        batchLog.append("=== End Batch ===\n");
        
        // 写入日志文件
        log.info(batchLog.toString());
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalLogs", totalLogs.get());
        stats.put("asyncLogs", asyncLogs.get());
        stats.put("syncLogs", syncLogs.get());
        stats.put("batchWrites", batchWrites.get());
        stats.put("failedWrites", failedWrites.get());
        stats.put("queueSize", logQueue.size());
        stats.put("currentBatchSize", currentBatch.get().size());
        stats.put("asyncEnabled", asyncEnabled);
        stats.put("timestamp", System.currentTimeMillis());
        return stats;
    }

    /**
     * 手动刷新批次
     */
    public void flush() {
        flushBatch();
    }

    /**
     * 审计日志条目
     */
    private static class AuditLogEntry {
        private final String action;
        private final String message;
        private final long timestamp;

        public AuditLogEntry(String action, String message, long timestamp) {
            this.action = action;
            this.message = message;
            this.timestamp = timestamp;
        }

        public String getAction() { return action; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("AuditLogEntry{action='%s', message='%s', timestamp=%d}", 
                    action, message, timestamp);
        }
    }

    /**
     * 同步审计服务（降级使用）
     */
    private static class SyncAuditService {
        public void log(String action, String message) {
            log.info("[AUDIT] {}: {}", action, message);
        }
    }
} 