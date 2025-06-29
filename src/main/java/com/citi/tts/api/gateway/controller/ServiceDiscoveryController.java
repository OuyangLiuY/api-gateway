package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.discovery.ServiceDiscoveryManager;
import com.citi.tts.api.gateway.discovery.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务发现管理控制器
 * 提供服务发现、注册、健康检查等REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway/discovery")
public class ServiceDiscoveryController {

    @Autowired
    private ServiceDiscoveryManager discoveryManager;

    @Autowired
    private ServiceRegistry serviceRegistry;

    /**
     * 获取所有服务列表
     */
    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getAllServices() {
        log.debug("Getting all services");
        
        try {
            List<String> services = discoveryManager.getServiceNames();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("services", services);
            response.put("count", services.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get all services", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取服务实例列表
     */
    @GetMapping("/services/{serviceName}/instances")
    public ResponseEntity<Map<String, Object>> getServiceInstances(@PathVariable String serviceName) {
        log.debug("Getting instances for service: {}", serviceName);
        
        try {
            List<ServiceInstance> instances = discoveryManager.getServiceInstances(serviceName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("serviceName", serviceName);
            response.put("instances", instances);
            response.put("count", instances.size());
            response.put("available", discoveryManager.getAvailableInstanceCount(serviceName));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get instances for service: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取服务健康状态
     */
    @GetMapping("/services/{serviceName}/health")
    public ResponseEntity<Map<String, Object>> getServiceHealth(@PathVariable String serviceName) {
        log.debug("Getting health status for service: {}", serviceName);
        
        try {
            List<ServiceInstance> instances = discoveryManager.getServiceInstances(serviceName);
            Map<String, ServiceDiscoveryManager.ServiceHealthStatus> healthStatuses = new HashMap<>();
            
            for (ServiceInstance instance : instances) {
                String instanceId = instance.getInstanceId();
                ServiceDiscoveryManager.ServiceHealthStatus status = 
                    discoveryManager.getServiceHealthStatus(instanceId);
                healthStatuses.put(instanceId, status);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("serviceName", serviceName);
            response.put("healthStatuses", healthStatuses);
            response.put("available", discoveryManager.isServiceAvailable(serviceName));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get health status for service: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取实例健康状态
     */
    @GetMapping("/instances/{instanceId}/health")
    public ResponseEntity<Map<String, Object>> getInstanceHealth(@PathVariable String instanceId) {
        log.debug("Getting health status for instance: {}", instanceId);
        
        try {
            ServiceDiscoveryManager.ServiceHealthStatus status = 
                discoveryManager.getServiceHealthStatus(instanceId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("instanceId", instanceId);
            response.put("healthStatus", status);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get health status for instance: {}", instanceId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("instanceId", instanceId);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 注册服务
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerService(
            @RequestParam String serviceName,
            @RequestParam int port,
            @RequestParam(required = false) Map<String, String> metadata) {
        
        log.info("Registering service: {} on port: {}", serviceName, port);
        
        try {
            if (metadata == null) {
                metadata = serviceRegistry.createDefaultMetadata();
            }
            
            boolean success = serviceRegistry.registerService(serviceName, port, metadata);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("serviceName", serviceName);
            response.put("port", port);
            response.put("metadata", metadata);
            response.put("message", success ? "Service registered successfully" : "Service registration failed");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to register service: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 注销服务
     */
    @PostMapping("/deregister")
    public ResponseEntity<Map<String, Object>> deregisterService(
            @RequestParam String serviceName,
            @RequestParam String instanceId) {
        
        log.info("Deregistering service: {} with instanceId: {}", serviceName, instanceId);
        
        try {
            boolean success = serviceRegistry.deregisterService(serviceName, instanceId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("serviceName", serviceName);
            response.put("instanceId", instanceId);
            response.put("message", success ? "Service deregistered successfully" : "Service deregistration failed");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to deregister service: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 更新服务状态
     */
    @PutMapping("/services/{serviceName}/status")
    public ResponseEntity<Map<String, Object>> updateServiceStatus(
            @PathVariable String serviceName,
            @RequestParam String instanceId,
            @RequestParam String status) {
        
        log.info("Updating service status: {} - {} -> {}", serviceName, instanceId, status);
        
        try {
            boolean success = serviceRegistry.updateServiceStatus(serviceName, instanceId, status);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("serviceName", serviceName);
            response.put("instanceId", instanceId);
            response.put("status", status);
            response.put("message", success ? "Service status updated successfully" : "Service status update failed");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update service status: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 发送心跳
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> sendHeartbeat(
            @RequestParam String serviceName,
            @RequestParam String instanceId) {
        
        log.debug("Sending heartbeat for service: {} - {}", serviceName, instanceId);
        
        try {
            boolean success = serviceRegistry.sendHeartbeat(serviceName, instanceId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("serviceName", serviceName);
            response.put("instanceId", instanceId);
            response.put("timestamp", System.currentTimeMillis());
            response.put("message", success ? "Heartbeat sent successfully" : "Heartbeat failed");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send heartbeat for service: {}", serviceName, e);
            
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
    @PostMapping("/refresh/{serviceName}")
    public ResponseEntity<Map<String, Object>> refreshServiceInstances(@PathVariable String serviceName) {
        log.info("Refreshing service instances for: {}", serviceName);
        
        try {
            discoveryManager.refreshServiceInstances(serviceName);
            
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
     * 检查服务是否可用
     */
    @GetMapping("/services/{serviceName}/available")
    public ResponseEntity<Map<String, Object>> isServiceAvailable(@PathVariable String serviceName) {
        log.debug("Checking if service is available: {}", serviceName);
        
        try {
            boolean available = discoveryManager.isServiceAvailable(serviceName);
            int availableCount = discoveryManager.getAvailableInstanceCount(serviceName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("serviceName", serviceName);
            response.put("available", available);
            response.put("availableCount", availableCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to check service availability for: {}", serviceName, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("serviceName", serviceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取服务发现统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDiscoveryStats() {
        log.debug("Getting discovery statistics");
        
        try {
            List<String> services = discoveryManager.getServiceNames();
            Map<String, Object> stats = new HashMap<>();
            
            for (String serviceName : services) {
                Map<String, Object> serviceStats = new HashMap<>();
                serviceStats.put("available", discoveryManager.isServiceAvailable(serviceName));
                serviceStats.put("availableCount", discoveryManager.getAvailableInstanceCount(serviceName));
                serviceStats.put("totalCount", discoveryManager.getServiceInstances(serviceName).size());
                stats.put(serviceName, serviceStats);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);
            response.put("totalServices", services.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get discovery statistics", e);
            
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
        response.put("component", "ServiceDiscovery");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
} 