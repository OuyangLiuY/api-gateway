package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.loadbalancer.AdvancedLoadBalancer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 负载均衡管理控制器
 * 提供负载均衡策略配置、监控和管理功能
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway/loadbalancer")
public class LoadBalancerController {

    @Autowired
    private AdvancedLoadBalancer advancedLoadBalancer;

    /**
     * 设置负载均衡策略
     */
    @PostMapping("/strategy")
    public ResponseEntity<Map<String, Object>> setStrategy(
            @RequestParam String serviceName,
            @RequestParam AdvancedLoadBalancer.LoadBalancingStrategy strategy) {
        
        log.info("Setting load balancing strategy for service: {} to: {}", serviceName, strategy);
        
        try {
            advancedLoadBalancer.setStrategy(serviceName, strategy);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("serviceName", serviceName);
            response.put("strategy", strategy);
            response.put("message", "Load balancing strategy updated successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to set load balancing strategy for service: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 刷新服务实例缓存
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshServiceInstances(
            @RequestParam String serviceName) {
        
        log.info("Refreshing service instances for: {}", serviceName);
        
        try {
            advancedLoadBalancer.refreshServiceInstances(serviceName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("serviceName", serviceName);
            response.put("message", "Service instances refreshed successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to refresh service instances for: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取服务统计信息
     */
    @GetMapping("/stats/{serviceName}")
    public ResponseEntity<Map<String, Object>> getServiceStats(@PathVariable String serviceName) {
        log.debug("Getting stats for service: {}", serviceName);
        
        try {
            AdvancedLoadBalancer.ServiceInstanceStats stats = advancedLoadBalancer.getStats(serviceName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("serviceName", serviceName);
            response.put("stats", stats);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get stats for service: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取所有服务统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAllStats() {
        log.debug("Getting all load balancer stats");
        
        try {
            // 这里需要扩展AdvancedLoadBalancer来支持获取所有统计信息
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Load balancer stats retrieved successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get all load balancer stats", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取负载均衡策略列表
     */
    @GetMapping("/strategies")
    public ResponseEntity<Map<String, Object>> getStrategies() {
        log.debug("Getting available load balancing strategies");
        
        try {
            AdvancedLoadBalancer.LoadBalancingStrategy[] strategies = 
                AdvancedLoadBalancer.LoadBalancingStrategy.values();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("strategies", strategies);
            response.put("count", strategies.length);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get load balancing strategies", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("component", "LoadBalancer");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 测试负载均衡
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testLoadBalancing(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "10") int requestCount) {
        
        log.info("Testing load balancing for service: {} with {} requests", serviceName, requestCount);
        
        try {
            Map<String, Integer> instanceDistribution = new HashMap<>();
            
            for (int i = 0; i < requestCount; i++) {
                String requestId = "test-" + System.currentTimeMillis() + "-" + i;
                
                advancedLoadBalancer.chooseInstance(serviceName, requestId)
                        .subscribe(instance -> {
                            String instanceId = instance.getInstanceId();
                            instanceDistribution.merge(instanceId, 1, Integer::sum);
                        });
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("serviceName", serviceName);
            response.put("requestCount", requestCount);
            response.put("instanceDistribution", instanceDistribution);
            response.put("message", "Load balancing test completed");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to test load balancing for service: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 