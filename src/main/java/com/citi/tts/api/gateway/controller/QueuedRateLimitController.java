package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.filter.QueuedRateLimitFilter;
import com.citi.tts.api.gateway.limiter.QueuedRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 排队限流管理控制器
 * 提供队列状态监控和管理功能
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/queue-rate-limit")
public class QueuedRateLimitController {

    @Autowired
    private QueuedRateLimiter queuedRateLimiter;

    @Autowired
    private QueuedRateLimitFilter queuedRateLimitFilter;

    /**
     * 获取队列统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        QueuedRateLimiter.QueueStats queueStats = queuedRateLimiter.getQueueStats();
        QueuedRateLimitFilter.QueuedRateLimitStats filterStats = queuedRateLimitFilter.getStats();
        
        Map<String, Object> response = new HashMap<>();
        response.put("queueStats", queueStats);
        response.put("filterStats", filterStats);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("Queue stats requested: {}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取特定队列状态
     */
    @GetMapping("/queue/{key}")
    public ResponseEntity<QueuedRateLimiter.QueueStatus> getQueueStatus(@PathVariable String key) {
        QueuedRateLimiter.QueueStatus status = queuedRateLimiter.getQueueStatus(key);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        
        log.debug("Queue status requested for key: {}", key);
        return ResponseEntity.ok(status);
    }

    /**
     * 清空指定队列
     */
    @DeleteMapping("/queue/{key}")
    public ResponseEntity<Map<String, String>> clearQueue(@PathVariable String key) {
        queuedRateLimiter.clearQueue(key);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Queue cleared successfully");
        response.put("key", key);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        log.info("Queue cleared for key: {}", key);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取降级服务统计信息
     */
    @GetMapping("/fallback-stats")
    public ResponseEntity<Map<String, Object>> getFallbackStats() {
        QueuedRateLimiter.QueueStats queueStats = queuedRateLimiter.getQueueStats();
        
        Map<String, Object> fallbackStats = new HashMap<>();
        fallbackStats.put("totalFallbackRequests", queueStats.getTotalFallbackRequests());
        fallbackStats.put("fallbackRate", calculateFallbackRate(queueStats));
        fallbackStats.put("totalRejectedRequests", queueStats.getTotalRejectedRequests());
        fallbackStats.put("totalQueuedRequests", queueStats.getTotalQueuedRequests());
        fallbackStats.put("timestamp", System.currentTimeMillis());
        
        log.info("Fallback stats requested: {}", fallbackStats);
        return ResponseEntity.ok(fallbackStats);
    }

    /**
     * 获取队列健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getQueueHealth() {
        QueuedRateLimiter.QueueStats queueStats = queuedRateLimiter.getQueueStats();
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("activeQueues", queueStats.getActiveQueues());
        health.put("activeProcessors", queueStats.getActiveProcessors());
        health.put("totalQueuedRequests", queueStats.getTotalQueuedRequests());
        health.put("totalProcessedRequests", queueStats.getTotalProcessedRequests());
        health.put("totalRejectedRequests", queueStats.getTotalRejectedRequests());
        health.put("totalTimeoutRequests", queueStats.getTotalTimeoutRequests());
        health.put("totalFallbackRequests", queueStats.getTotalFallbackRequests());
        health.put("timestamp", System.currentTimeMillis());
        
        // 计算健康指标
        long totalRequests = queueStats.getTotalQueuedRequests() + queueStats.getTotalRejectedRequests();
        if (totalRequests > 0) {
            double successRate = (double) queueStats.getTotalProcessedRequests() / totalRequests;
            health.put("successRate", String.format("%.2f%%", successRate * 100));
            
            if (successRate < 0.8) {
                health.put("status", "DEGRADED");
                health.put("warning", "Success rate is below 80%");
            }
        }
        
        log.debug("Queue health check: {}", health);
        return ResponseEntity.ok(health);
    }

    /**
     * 重置统计信息
     */
    @PostMapping("/reset-stats")
    public ResponseEntity<Map<String, String>> resetStats() {
        // 注意：这里只是返回成功，实际的重置需要在QueuedRateLimiter中实现
        Map<String, String> response = new HashMap<>();
        response.put("message", "Statistics reset requested");
        response.put("note", "Statistics reset functionality needs to be implemented in QueuedRateLimiter");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        log.info("Statistics reset requested");
        return ResponseEntity.ok(response);
    }

    /**
     * 立即处理指定队列（新增）
     */
    @PostMapping("/queue/{key}/process-immediately")
    public ResponseEntity<Map<String, Object>> processQueueImmediately(@PathVariable String key) {
        queuedRateLimiter.processQueueImmediately(key);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Queue processing triggered immediately");
        response.put("key", key);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("Immediate queue processing triggered for key: {}", key);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量处理指定队列（新增）
     */
    @PostMapping("/queue/{key}/process-batch")
    public ResponseEntity<Map<String, Object>> processQueueBatch(
            @PathVariable String key, 
            @RequestParam(defaultValue = "10") int batchSize) {
        queuedRateLimiter.processQueueBatch(key, batchSize);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Batch queue processing triggered");
        response.put("key", key);
        response.put("batchSize", batchSize);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("Batch queue processing triggered for key: {}, batch size: {}", key, batchSize);
        return ResponseEntity.ok(response);
    }

    /**
     * 自适应处理指定队列（新增）
     */
    @PostMapping("/queue/{key}/process-adaptive")
    public ResponseEntity<Map<String, Object>> processQueueAdaptive(@PathVariable String key) {
        queuedRateLimiter.processQueueAdaptive(key);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Adaptive queue processing triggered");
        response.put("key", key);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("Adaptive queue processing triggered for key: {}", key);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取队列处理策略信息（新增）
     */
    @GetMapping("/queue/{key}/processing-info")
    public ResponseEntity<Map<String, Object>> getQueueProcessingInfo(@PathVariable String key) {
        QueuedRateLimiter.QueueStatus status = queuedRateLimiter.getQueueStatus(key);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> processingInfo = new HashMap<>();
        processingInfo.put("key", key);
        processingInfo.put("queueSize", status.getQueueSize());
        processingInfo.put("hasProcessor", status.isHasProcessor());
        processingInfo.put("processingStrategies", getProcessingStrategies());
        processingInfo.put("recommendedAction", getRecommendedAction(status.getQueueSize()));
        processingInfo.put("timestamp", System.currentTimeMillis());
        
        log.debug("Queue processing info requested for key: {}", key);
        return ResponseEntity.ok(processingInfo);
    }

    /**
     * 获取处理策略列表
     */
    private Map<String, String> getProcessingStrategies() {
        Map<String, String> strategies = new HashMap<>();
        strategies.put("scheduled", "定时处理：每100ms自动处理一次");
        strategies.put("immediate", "立即处理：手动触发立即处理");
        strategies.put("batch", "批量处理：一次性处理多个请求");
        strategies.put("adaptive", "自适应处理：根据队列负载动态调整");
        return strategies;
    }

    /**
     * 根据队列大小推荐处理策略
     */
    private String getRecommendedAction(int queueSize) {
        if (queueSize == 0) {
            return "队列为空，无需处理";
        } else if (queueSize < 10) {
            return "队列负载较低，建议使用定时处理";
        } else if (queueSize < 100) {
            return "队列负载中等，建议使用批量处理";
        } else if (queueSize < 500) {
            return "队列负载较高，建议使用自适应处理";
        } else {
            return "队列负载很高，建议立即处理并检查系统负载";
        }
    }

    /**
     * 计算降级率
     */
    private double calculateFallbackRate(QueuedRateLimiter.QueueStats queueStats) {
        long totalRequests = queueStats.getTotalQueuedRequests() + queueStats.getTotalRejectedRequests();
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) queueStats.getTotalFallbackRequests() / totalRequests;
    }
} 