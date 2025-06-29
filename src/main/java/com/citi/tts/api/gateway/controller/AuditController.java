package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.audit.AsyncAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 审计日志监控控制器
 * 提供异步日志服务的状态监控和管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway/audit")
public class AuditController {

    @Autowired
    private AsyncAuditService asyncAuditService;

    /**
     * 获取审计日志服务统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAuditStats() {
        Map<String, Object> stats = asyncAuditService.getStats();
        log.debug("Audit stats requested: {}", stats);
        return ResponseEntity.ok(stats);
    }

    /**
     * 手动刷新审计日志批次
     */
    @PostMapping("/flush")
    public ResponseEntity<Map<String, Object>> flushAuditLogs() {
        long startTime = System.currentTimeMillis();
        asyncAuditService.flush();
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = Map.of(
            "status", "success",
            "message", "Audit logs flushed successfully",
            "duration", duration + "ms",
            "timestamp", System.currentTimeMillis()
        );
        
        log.info("Manual audit log flush completed in {}ms", duration);
        return ResponseEntity.ok(result);
    }

    /**
     * 测试审计日志记录
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testAuditLog(@RequestParam String action, 
                                                           @RequestParam String message) {
        long startTime = System.currentTimeMillis();
        asyncAuditService.log(action, message);
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = Map.of(
            "status", "success",
            "message", "Test audit log recorded",
            "action", action,
            "message", message,
            "duration", duration + "ms",
            "timestamp", System.currentTimeMillis()
        );
        
        log.info("Test audit log recorded - Action: {}, Message: {}, Duration: {}ms", 
                action, message, duration);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量测试审计日志记录
     */
    @PostMapping("/test/batch")
    public ResponseEntity<Map<String, Object>> testBatchAuditLog(@RequestParam int count) {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < count; i++) {
            asyncAuditService.log("test_batch", "Test message " + (i + 1));
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = Map.of(
            "status", "success",
            "message", "Batch test audit logs recorded",
            "count", count,
            "duration", duration + "ms",
            "avgDuration", String.format("%.2f", (double) duration / count) + "ms",
            "timestamp", System.currentTimeMillis()
        );
        
        log.info("Batch test audit logs recorded - Count: {}, Duration: {}ms, Avg: {}ms", 
                count, duration, String.format("%.2f", (double) duration / count));
        return ResponseEntity.ok(result);
    }

    /**
     * 获取审计日志服务健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getAuditHealth() {
        Map<String, Object> stats = asyncAuditService.getStats();
        
        Map<String, Object> health = Map.of(
            "status", "UP",
            "asyncEnabled", stats.get("asyncEnabled"),
            "queueSize", stats.get("queueSize"),
            "currentBatchSize", stats.get("currentBatchSize"),
            "totalLogs", stats.get("totalLogs"),
            "asyncLogs", stats.get("asyncLogs"),
            "syncLogs", stats.get("syncLogs"),
            "batchWrites", stats.get("batchWrites"),
            "failedWrites", stats.get("failedWrites"),
            "timestamp", System.currentTimeMillis()
        );
        
        log.debug("Audit health check: {}", health);
        return ResponseEntity.ok(health);
    }

    /**
     * 获取审计日志服务配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAuditConfig() {
        Map<String, Object> config = Map.of(
            "asyncEnabled", true,
            "batchSize", 100,
            "flushInterval", "5000ms",
            "queueSize", 10000,
            "queueTimeout", "100ms",
            "workerThreads", 1,
            "timestamp", System.currentTimeMillis()
        );
        
        log.debug("Audit config requested: {}", config);
        return ResponseEntity.ok(config);
    }
} 