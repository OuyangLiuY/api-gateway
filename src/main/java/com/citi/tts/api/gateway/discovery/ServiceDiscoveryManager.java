package com.citi.tts.api.gateway.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务发现管理器
 * 统一管理服务发现、注册、健康检查等功能
 */
@Slf4j
@Component
public class ServiceDiscoveryManager {

    @Autowired
    private DiscoveryClient discoveryClient;

    // 服务实例缓存
    private final Map<String, List<ServiceInstance>> serviceInstanceCache = new ConcurrentHashMap<>();
    
    // 服务健康状态缓存
    private final Map<String, ServiceHealthStatus> healthStatusCache = new ConcurrentHashMap<>();
    
    // 服务注册统计
    private final Map<String, ServiceRegistrationStats> registrationStats = new ConcurrentHashMap<>();

    /**
     * 获取服务实例列表
     */
    public List<ServiceInstance> getServiceInstances(String serviceName) {
        return serviceInstanceCache.computeIfAbsent(serviceName, name -> {
            List<ServiceInstance> instances = discoveryClient.getInstances(name);
            log.info("Discovered {} instances for service: {}", instances.size(), name);
            
            // 初始化健康状态
            for (ServiceInstance instance : instances) {
                String instanceId = instance.getInstanceId();
                healthStatusCache.putIfAbsent(instanceId, new ServiceHealthStatus());
                registrationStats.putIfAbsent(instanceId, new ServiceRegistrationStats());
            }
            
            return instances;
        });
    }

    /**
     * 获取所有服务名称
     */
    public List<String> getServiceNames() {
        return discoveryClient.getServices();
    }

    /**
     * 刷新服务实例缓存
     */
    public void refreshServiceInstances(String serviceName) {
        serviceInstanceCache.remove(serviceName);
        log.info("Refreshed service instances for: {}", serviceName);
    }

    /**
     * 获取服务健康状态
     */
    public ServiceHealthStatus getServiceHealthStatus(String instanceId) {
        return healthStatusCache.get(instanceId);
    }

    /**
     * 更新服务健康状态
     */
    public void updateServiceHealthStatus(String instanceId, boolean healthy, String reason) {
        ServiceHealthStatus status = healthStatusCache.computeIfAbsent(instanceId, k -> new ServiceHealthStatus());
        status.setHealthy(healthy);
        status.setLastCheckTime(System.currentTimeMillis());
        status.setReason(reason);
        
        if (!healthy) {
            status.incrementFailureCount();
        } else {
            status.resetFailureCount();
        }
        
        log.debug("Updated health status for instance {}: healthy={}, reason={}", instanceId, healthy, reason);
    }

    /**
     * 获取服务注册统计
     */
    public ServiceRegistrationStats getRegistrationStats(String instanceId) {
        return registrationStats.get(instanceId);
    }

    /**
     * 更新服务注册统计
     */
    public void updateRegistrationStats(String instanceId, boolean registered) {
        ServiceRegistrationStats stats = registrationStats.computeIfAbsent(instanceId, k -> new ServiceRegistrationStats());
        
        if (registered) {
            stats.incrementRegistrationCount();
            stats.setLastRegistrationTime(System.currentTimeMillis());
        } else {
            stats.incrementDeregistrationCount();
            stats.setLastDeregistrationTime(System.currentTimeMillis());
        }
        
        log.debug("Updated registration stats for instance {}: registered={}", instanceId, registered);
    }

    /**
     * 检查服务是否可用
     */
    public boolean isServiceAvailable(String serviceName) {
        List<ServiceInstance> instances = getServiceInstances(serviceName);
        return instances.stream().anyMatch(instance -> {
            ServiceHealthStatus status = getServiceHealthStatus(instance.getInstanceId());
            return status != null && status.isHealthy();
        });
    }

    /**
     * 获取可用实例数量
     */
    public int getAvailableInstanceCount(String serviceName) {
        List<ServiceInstance> instances = getServiceInstances(serviceName);
        return (int) instances.stream().filter(instance -> {
            ServiceHealthStatus status = getServiceHealthStatus(instance.getInstanceId());
            return status != null && status.isHealthy();
        }).count();
    }

    /**
     * 监听服务注册事件
     */
    @EventListener
    public void handleInstanceRegisteredEvent(InstanceRegisteredEvent<?> event) {
        String serviceName = event.getSource().toString();
        log.info("Service registered: {}", serviceName);
        
        // 刷新服务实例缓存
        refreshServiceInstances(serviceName);
        
        // 更新注册统计
        updateRegistrationStats(serviceName, true);
    }

    /**
     * 监听心跳事件
     */
    @EventListener
    public void handleHeartbeatEvent(HeartbeatEvent event) {
        log.debug("Received heartbeat event: {}", event.getValue());
        
        // 可以在这里处理心跳逻辑
        // 比如更新服务状态、清理过期实例等
    }

    /**
     * 定期健康检查
     */
    @Scheduled(fixedRate = 30000) // 每30秒检查一次
    public void performHealthCheck() {
        log.debug("Performing periodic health check");
        
        for (String serviceName : getServiceNames()) {
            List<ServiceInstance> instances = getServiceInstances(serviceName);
            
            for (ServiceInstance instance : instances) {
                performInstanceHealthCheck(instance);
            }
        }
    }

    /**
     * 执行实例健康检查
     */
    private void performInstanceHealthCheck(ServiceInstance instance) {
        String instanceId = instance.getInstanceId();
        
        try {
            // 这里可以发送实际的健康检查请求
            // 比如调用 /actuator/health 端点
            boolean healthy = checkInstanceHealth(instance);
            updateServiceHealthStatus(instanceId, healthy, healthy ? "OK" : "Health check failed");
            
        } catch (Exception e) {
            log.warn("Health check failed for instance {}: {}", instanceId, e.getMessage());
            updateServiceHealthStatus(instanceId, false, "Exception: " + e.getMessage());
        }
    }

    /**
     * 检查实例健康状态
     */
    private boolean checkInstanceHealth(ServiceInstance instance) {
        // 这里实现具体的健康检查逻辑
        // 可以调用实例的健康检查端点
        // 暂时返回true，实际实现中需要发送HTTP请求
        return true;
    }

    /**
     * 清理过期实例
     */
    @Scheduled(fixedRate = 60000) // 每分钟清理一次
    public void cleanupExpiredInstances() {
        log.debug("Cleaning up expired instances");
        
        long currentTime = System.currentTimeMillis();
        long expirationTime = 5 * 60 * 1000; // 5分钟过期
        
        healthStatusCache.entrySet().removeIf(entry -> {
            ServiceHealthStatus status = entry.getValue();
            return currentTime - status.getLastCheckTime() > expirationTime;
        });
        
        registrationStats.entrySet().removeIf(entry -> {
            ServiceRegistrationStats stats = entry.getValue();
            return currentTime - stats.getLastRegistrationTime() > expirationTime;
        });
    }

    /**
     * 服务健康状态
     */
    @lombok.Data
    public static class ServiceHealthStatus {
        private boolean healthy = true;
        private long lastCheckTime = System.currentTimeMillis();
        private String reason = "OK";
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);

        public void incrementFailureCount() {
            failureCount.incrementAndGet();
        }

        public void incrementSuccessCount() {
            successCount.incrementAndGet();
        }

        public void resetFailureCount() {
            failureCount.set(0);
        }

        public double getFailureRate() {
            long total = failureCount.get() + successCount.get();
            return total > 0 ? (double) failureCount.get() / total : 0.0;
        }
    }

    /**
     * 服务注册统计
     */
    @lombok.Data
    public static class ServiceRegistrationStats {
        private final AtomicLong registrationCount = new AtomicLong(0);
        private final AtomicLong deregistrationCount = new AtomicLong(0);
        private long lastRegistrationTime = System.currentTimeMillis();
        private long lastDeregistrationTime = 0;

        public void incrementRegistrationCount() {
            registrationCount.incrementAndGet();
        }

        public void incrementDeregistrationCount() {
            deregistrationCount.incrementAndGet();
        }
    }
} 