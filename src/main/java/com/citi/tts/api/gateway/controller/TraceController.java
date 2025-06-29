package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.tracing.TraceManager;
import com.citi.tts.api.gateway.tracing.TraceReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 链路追踪控制器
 * 提供追踪管理的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    @Autowired
    private TraceManager traceManager;

    @Autowired
    private TraceReporter traceReporter;

    /**
     * 获取追踪统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTraceStats() {
        TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
        TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("manager", managerStats);
        stats.put("reporter", reporterStats);
        stats.put("timestamp", System.currentTimeMillis());
        
        log.debug("Trace stats requested: {}", stats);
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取指定追踪的详细信息
     */
    @GetMapping("/{traceId}")
    public ResponseEntity<Map<String, Object>> getTraceDetails(@PathVariable String traceId) {
        var traceContext = traceManager.getTraceContext(traceId);
        
        if (traceContext == null) {
            Map<String, Object> error = Map.of(
                "error", "Trace not found",
                "traceId", traceId,
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> details = new HashMap<>();
        details.put("traceId", traceContext.getTraceId());
        details.put("spanId", traceContext.getSpanId());
        details.put("parentSpanId", traceContext.getParentSpanId());
        details.put("requestId", traceContext.getRequestId());
        details.put("correlationId", traceContext.getCorrelationId());
        details.put("userId", traceContext.getUserId());
        details.put("tenantId", traceContext.getTenantId());
        details.put("serviceName", traceContext.getServiceName());
        details.put("operationName", traceContext.getOperationName());
        details.put("startTime", traceContext.getStartTime());
        details.put("endTime", traceContext.getEndTime());
        details.put("durationMs", traceContext.getDurationMs());
        details.put("statusCode", traceContext.getStatusCode());
        details.put("errorMessage", traceContext.getErrorMessage());
        details.put("tags", traceContext.getTags());
        details.put("events", traceContext.getEvents());
        details.put("sampled", traceContext.isSampled());
        details.put("childSpans", traceContext.getChildSpans().size());
        details.put("timestamp", System.currentTimeMillis());
        
        log.debug("Trace details requested for: {}", traceId);
        return ResponseEntity.ok(details);
    }

    /**
     * 设置采样率
     */
    @PostMapping("/sampling/rate")
    public ResponseEntity<Map<String, Object>> setSamplingRate(@RequestParam double rate) {
        traceManager.setSamplingRate(rate);
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Sampling rate updated",
            "newRate", rate,
            "timestamp", System.currentTimeMillis()
        );
        
        log.info("Sampling rate updated to: {}", rate);
        return ResponseEntity.ok(response);
    }

    /**
     * 启用/禁用采样
     */
    @PostMapping("/sampling/enabled")
    public ResponseEntity<Map<String, Object>> setSamplingEnabled(@RequestParam boolean enabled) {
        traceManager.setSamplingEnabled(enabled);
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Sampling enabled updated",
            "enabled", enabled,
            "timestamp", System.currentTimeMillis()
        );
        
        log.info("Sampling enabled: {}", enabled);
        return ResponseEntity.ok(response);
    }

    /**
     * 强制采样指定追踪
     */
    @PostMapping("/{traceId}/force-sample")
    public ResponseEntity<Map<String, Object>> forceSample(@PathVariable String traceId) {
        traceManager.forceSample(traceId);
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Trace forced to sample",
            "traceId", traceId,
            "timestamp", System.currentTimeMillis()
        );
        
        log.info("Forced sampling for trace: {}", traceId);
        return ResponseEntity.ok(response);
    }

    /**
     * 清理过期的追踪上下文
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupTraces(@RequestParam(defaultValue = "300000") long maxAgeMs) {
        traceManager.cleanupExpiredTraces(maxAgeMs);
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Trace cleanup completed",
            "maxAgeMs", maxAgeMs,
            "activeTraces", traceManager.getActiveTraceCount(),
            "timestamp", System.currentTimeMillis()
        );
        
        log.info("Trace cleanup completed with maxAge: {}ms", maxAgeMs);
        return ResponseEntity.ok(response);
    }

    /**
     * 手动刷新追踪报告
     */
    @PostMapping("/reporter/flush")
    public ResponseEntity<Map<String, Object>> flushTraceReports() {
        long startTime = System.currentTimeMillis();
        
        traceReporter.flush()
                .subscribe(
                    (Void v) -> log.info("Trace report flush completed"),
                    error -> log.error("Trace report flush failed", error)
                );
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Trace report flush initiated",
            "duration", (System.currentTimeMillis() - startTime) + "ms",
            "timestamp", System.currentTimeMillis()
        );
        
        log.info("Trace report flush initiated");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取追踪健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getTraceHealth() {
        TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
        TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("manager", Map.of(
            "activeTraces", managerStats.getActiveTraces(),
            "samplingEnabled", managerStats.isSamplingEnabled(),
            "samplingRate", managerStats.getSamplingRate()
        ));
        health.put("reporter", Map.of(
            "enabled", reporterStats.isEnabled(),
            "queueSize", reporterStats.getQueueSize(),
            "reportedTraces", reporterStats.getReportedTraces(),
            "failedReports", reporterStats.getFailedReports()
        ));
        health.put("timestamp", System.currentTimeMillis());
        
        log.debug("Trace health check: {}", health);
        return ResponseEntity.ok(health);
    }

    /**
     * 获取活跃追踪列表
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveTraces(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("activeTraces", traceManager.getActiveTraceCount());
        response.put("limit", limit);
        response.put("offset", offset);
        response.put("timestamp", System.currentTimeMillis());
        
        // 注意：这里简化实现，实际项目中需要从TraceManager获取活跃追踪列表
        response.put("traces", "Feature not implemented yet");
        
        log.debug("Active traces requested - limit: {}, offset: {}", limit, offset);
        return ResponseEntity.ok(response);
    }
} 