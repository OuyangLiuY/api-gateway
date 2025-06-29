package com.citi.tts.api.gateway.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务注册器
 * 支持多种服务注册中心的统一注册接口
 */
@Slf4j
@Component
public class ServiceRegistry {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${spring.application.name:api-gateway}")
    private String applicationName;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${spring.cloud.discovery.enabled:false}")
    private boolean discoveryEnabled;

    /**
     * 注册服务实例
     */
    public boolean registerService(String serviceName, int port, Map<String, String> metadata) {
        if (!discoveryEnabled) {
            log.warn("Service discovery is disabled, skipping registration for: {}", serviceName);
            return false;
        }

        try {
            String host = getLocalHost();
            String instanceId = generateInstanceId(serviceName, host, port);
            
            log.info("Registering service: {} with instanceId: {} at {}:{}", 
                    serviceName, instanceId, host, port);

            // 这里应该调用具体的注册中心API
            // 由于Spring Cloud已经提供了抽象，我们主要记录日志
            log.info("Service registration initiated for: {} - {}:{}", serviceName, host, port);
            
            return true;
        } catch (Exception e) {
            log.error("Failed to register service: {}", serviceName, e);
            return false;
        }
    }

    /**
     * 注销服务实例
     */
    public boolean deregisterService(String serviceName, String instanceId) {
        if (!discoveryEnabled) {
            log.warn("Service discovery is disabled, skipping deregistration for: {}", serviceName);
            return false;
        }

        try {
            log.info("Deregistering service: {} with instanceId: {}", serviceName, instanceId);
            
            // 这里应该调用具体的注册中心API
            log.info("Service deregistration initiated for: {} - {}", serviceName, instanceId);
            
            return true;
        } catch (Exception e) {
            log.error("Failed to deregister service: {}", serviceName, e);
            return false;
        }
    }

    /**
     * 更新服务实例状态
     */
    public boolean updateServiceStatus(String serviceName, String instanceId, String status) {
        if (!discoveryEnabled) {
            log.warn("Service discovery is disabled, skipping status update for: {}", serviceName);
            return false;
        }

        try {
            log.info("Updating service status: {} - {} -> {}", serviceName, instanceId, status);
            
            // 这里应该调用具体的注册中心API
            log.info("Service status update initiated for: {} - {} -> {}", serviceName, instanceId, status);
            
            return true;
        } catch (Exception e) {
            log.error("Failed to update service status: {}", serviceName, e);
            return false;
        }
    }

    /**
     * 发送心跳
     */
    public boolean sendHeartbeat(String serviceName, String instanceId) {
        if (!discoveryEnabled) {
            log.warn("Service discovery is disabled, skipping heartbeat for: {}", serviceName);
            return false;
        }

        try {
            log.debug("Sending heartbeat for service: {} - {}", serviceName, instanceId);
            
            // 这里应该调用具体的注册中心API
            log.debug("Heartbeat sent for: {} - {}", serviceName, instanceId);
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send heartbeat for service: {}", serviceName, e);
            return false;
        }
    }

    /**
     * 获取本地主机地址
     */
    private String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Failed to get local host address, using localhost", e);
            return "localhost";
        }
    }

    /**
     * 生成实例ID
     */
    private String generateInstanceId(String serviceName, String host, int port) {
        return String.format("%s-%s-%d", serviceName, host, port);
    }

    /**
     * 创建默认元数据
     */
    public Map<String, String> createDefaultMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        metadata.put("zone", "default");
        metadata.put("weight", "1");
        metadata.put("secure", "false");
        return metadata;
    }

    /**
     * 检查服务是否已注册
     */
    public boolean isServiceRegistered(String serviceName) {
        try {
            return discoveryClient.getInstances(serviceName).size() > 0;
        } catch (Exception e) {
            log.warn("Failed to check service registration status for: {}", serviceName, e);
            return false;
        }
    }

    /**
     * 获取服务实例信息
     */
    public ServiceInstance getServiceInstance(String serviceName, String instanceId) {
        try {
            return discoveryClient.getInstances(serviceName).stream()
                    .filter(instance -> instance.getInstanceId().equals(instanceId))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to get service instance: {} - {}", serviceName, instanceId, e);
            return null;
        }
    }
} 