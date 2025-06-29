package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.metrics.QPSMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * QPS监控控制器
 * 提供QPS统计信息的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor/qps")
public class QPSMonitorController {

    @Autowired
    private QPSMetrics qpsMetrics;

    /**
     * 获取全局QPS
     */
    @GetMapping("/global")
    public ResponseEntity<Map<String, Object>> getGlobalQPS() {
        long globalQPS = qpsMetrics.getGlobalQPS();
        
        Map<String, Object> response = new HashMap<>();
        response.put("globalQPS", globalQPS);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        
        log.info("Global QPS: {}", globalQPS);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取API路径QPS
     */
    @GetMapping("/api")
    public ResponseEntity<Map<String, Object>> getApiQPS() {
        Map<String, Long> apiQPS = qpsMetrics.getApiQPS();
        
        Map<String, Object> response = new HashMap<>();
        response.put("apiQPS", apiQPS);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        response.put("totalApis", apiQPS.size());
        
        log.info("API QPS: {}", apiQPS);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定API路径的QPS
     */
    @GetMapping("/api/{path}")
    public ResponseEntity<Map<String, Object>> getApiQPSByPath(@PathVariable String path) {
        Map<String, Long> apiQPS = qpsMetrics.getApiQPS();
        Long qps = apiQPS.get(path);
        
        Map<String, Object> response = new HashMap<>();
        response.put("path", path);
        response.put("qps", qps != null ? qps : 0);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        
        log.info("API QPS for path {}: {}", path, qps);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取IP QPS
     */
    @GetMapping("/ip")
    public ResponseEntity<Map<String, Object>> getIpQPS() {
        Map<String, Long> ipQPS = qpsMetrics.getIpQPS();
        
        Map<String, Object> response = new HashMap<>();
        response.put("ipQPS", ipQPS);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        response.put("totalIps", ipQPS.size());
        
        log.info("IP QPS: {}", ipQPS);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定IP的QPS
     */
    @GetMapping("/ip/{ip}")
    public ResponseEntity<Map<String, Object>> getIpQPSByIp(@PathVariable String ip) {
        Map<String, Long> ipQPS = qpsMetrics.getIpQPS();
        Long qps = ipQPS.get(ip);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ip", ip);
        response.put("qps", qps != null ? qps : 0);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        
        log.info("IP QPS for {}: {}", ip, qps);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户QPS
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUserQPS() {
        Map<String, Long> userQPS = qpsMetrics.getUserQPS();
        
        Map<String, Object> response = new HashMap<>();
        response.put("userQPS", userQPS);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        response.put("totalUsers", userQPS.size());
        
        log.info("User QPS: {}", userQPS);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定用户的QPS
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserQPSByUserId(@PathVariable String userId) {
        Map<String, Long> userQPS = qpsMetrics.getUserQPS();
        Long qps = userQPS.get(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("qps", qps != null ? qps : 0);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        
        log.info("User QPS for {}: {}", userId, qps);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取优先级QPS
     */
    @GetMapping("/priority")
    public ResponseEntity<Map<String, Object>> getPriorityQPS() {
        Map<String, Long> priorityQPS = qpsMetrics.getPriorityQPS();
        
        Map<String, Object> response = new HashMap<>();
        response.put("priorityQPS", priorityQPS);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        response.put("totalPriorities", priorityQPS.size());
        
        log.info("Priority QPS: {}", priorityQPS);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定优先级的QPS
     */
    @GetMapping("/priority/{priority}")
    public ResponseEntity<Map<String, Object>> getPriorityQPSByPriority(@PathVariable String priority) {
        Map<String, Long> priorityQPS = qpsMetrics.getPriorityQPS();
        Long qps = priorityQPS.get(priority);
        
        Map<String, Object> response = new HashMap<>();
        response.put("priority", priority);
        response.put("qps", qps != null ? qps : 0);
        response.put("timestamp", System.currentTimeMillis());
        response.put("unit", "requests/second");
        
        log.info("Priority QPS for {}: {}", priority, qps);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取完整的QPS统计信息
     */
    @GetMapping("/all")
    public ResponseEntity<QPSMetrics.QPSStatistics> getAllQPS() {
        QPSMetrics.QPSStatistics statistics = qpsMetrics.getQPSStatistics();
        
        log.info("Complete QPS statistics: {}", statistics);
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取QPS统计摘要
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getQPSSummary() {
        QPSMetrics.QPSStatistics statistics = qpsMetrics.getQPSStatistics();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("globalQPS", statistics.getGlobalQPS());
        summary.put("totalApis", statistics.getApiQPS().size());
        summary.put("totalIps", statistics.getIpQPS().size());
        summary.put("totalUsers", statistics.getUserQPS().size());
        summary.put("totalPriorities", statistics.getPriorityQPS().size());
        summary.put("timestamp", statistics.getTimestamp());
        
        // 计算各维度的总QPS
        long totalApiQPS = statistics.getApiQPS().values().stream().mapToLong(Long::longValue).sum();
        long totalIpQPS = statistics.getIpQPS().values().stream().mapToLong(Long::longValue).sum();
        long totalUserQPS = statistics.getUserQPS().values().stream().mapToLong(Long::longValue).sum();
        long totalPriorityQPS = statistics.getPriorityQPS().values().stream().mapToLong(Long::longValue).sum();
        
        summary.put("totalApiQPS", totalApiQPS);
        summary.put("totalIpQPS", totalIpQPS);
        summary.put("totalUserQPS", totalUserQPS);
        summary.put("totalPriorityQPS", totalPriorityQPS);
        
        log.info("QPS Summary: {}", summary);
        return ResponseEntity.ok(summary);
    }

    /**
     * 清理过期的QPS数据
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupQPSData() {
        qpsMetrics.cleanup();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "QPS data cleanup completed");
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("QPS data cleanup completed");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取QPS监控健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getQPSHealth() {
        QPSMetrics.QPSStatistics statistics = qpsMetrics.getQPSStatistics();
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("globalQPS", statistics.getGlobalQPS());
        health.put("activeApis", statistics.getApiQPS().size());
        health.put("activeIps", statistics.getIpQPS().size());
        health.put("activeUsers", statistics.getUserQPS().size());
        health.put("activePriorities", statistics.getPriorityQPS().size());
        health.put("timestamp", statistics.getTimestamp());
        
        log.debug("QPS Health check: {}", health);
        return ResponseEntity.ok(health);
    }
} 