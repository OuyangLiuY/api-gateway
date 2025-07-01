package com.citi.tts.api.gateway.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高级负载均衡器
 * 支持多种负载均衡策略和服务发现
 */
@Slf4j
@Component
public class AdvancedLoadBalancer {

    @Autowired
    private DiscoveryClient discoveryClient;

    // 服务实例缓存
    private final Map<String, List<ServiceInstance>> serviceInstanceCache = new ConcurrentHashMap<>();
    
    // 负载均衡策略
    private final Map<String, LoadBalancingStrategy> strategyMap = new ConcurrentHashMap<>();
    
    // 服务实例统计信息
    private final Map<String, ServiceInstanceStats> statsMap = new ConcurrentHashMap<>();

    /**
     * 负载均衡策略枚举
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,        // 轮询
        WEIGHTED_ROUND_ROBIN, // 权重轮询
        LEAST_CONNECTIONS,  // 最少连接
        RESPONSE_TIME,      // 响应时间
        CONSISTENT_HASH,    // 一致性哈希
        RANDOM,            // 随机
        IP_HASH            // IP哈希
    }

    /**
     * 选择服务实例
     */
    public Mono<ServiceInstance> chooseInstance(String serviceName, String requestId) {
        return Mono.fromCallable(() -> {
            List<ServiceInstance> instances = getServiceInstances(serviceName);
            if (instances.isEmpty()) {
                log.warn("No available instances for service: {}", serviceName);
                return null;
            }

            LoadBalancingStrategy strategy = getStrategy(serviceName);
            ServiceInstance selectedInstance = selectInstance(instances, strategy, requestId);
            
            if (selectedInstance != null) {
                updateStats(serviceName, selectedInstance);
                log.debug("Selected instance: {} for service: {} using strategy: {}", 
                        selectedInstance.getInstanceId(), serviceName, strategy);
            }

            return selectedInstance;
        });
    }

    /**
     * 获取服务实例列表
     */
    private List<ServiceInstance> getServiceInstances(String serviceName) {
        return serviceInstanceCache.computeIfAbsent(serviceName, name -> {
            List<ServiceInstance> instances = discoveryClient.getInstances(name);
            log.info("Discovered {} instances for service: {}", instances.size(), name);
            return instances;
        });
    }

    /**
     * 获取负载均衡策略
     */
    private LoadBalancingStrategy getStrategy(String serviceName) {
        return strategyMap.getOrDefault(serviceName, LoadBalancingStrategy.ROUND_ROBIN);
    }

    /**
     * 根据策略选择实例
     */
    private ServiceInstance selectInstance(List<ServiceInstance> instances, 
                                         LoadBalancingStrategy strategy, 
                                         String requestId) {
        return switch (strategy) {
            case ROUND_ROBIN -> selectRoundRobin(instances);
            case WEIGHTED_ROUND_ROBIN -> selectWeightedRoundRobin(instances);
            case LEAST_CONNECTIONS -> selectLeastConnections(instances);
            case RESPONSE_TIME -> selectBestResponseTime(instances);
            case CONSISTENT_HASH -> selectConsistentHash(instances, requestId);
            case RANDOM -> selectRandom(instances);
            case IP_HASH -> selectIpHash(instances, requestId);
        };
    }

    /**
     * 轮询选择
     */
    private ServiceInstance selectRoundRobin(List<ServiceInstance> instances) {
        ServiceInstanceStats stats = getOrCreateStats("default");
        int index = (int) (stats.getRoundRobinCounter().incrementAndGet() % instances.size());
        return instances.get(index);
    }

    /**
     * 权重轮询选择
     */
    private ServiceInstance selectWeightedRoundRobin(List<ServiceInstance> instances) {
        ServiceInstanceStats stats = getOrCreateStats("default");
        AtomicLong currentWeight = stats.getCurrentWeight();
        AtomicLong maxWeight = stats.getMaxWeight();
        AtomicInteger currentIndex = stats.getCurrentIndex();

        while (true) {
            currentIndex.set((currentIndex.get() + 1) % instances.size());
            if (currentIndex.get() == 0) {
                currentWeight.addAndGet(-maxWeight.get());
                if (currentWeight.get() <= 0) {
                    currentWeight.set(maxWeight.get());
                }
            }

            ServiceInstance instance = instances.get(currentIndex.get());
            int weight = getInstanceWeight(instance);
            if (weight >= currentWeight.get()) {
                return instance;
            }
        }
    }

    /**
     * 最少连接选择
     */
    private ServiceInstance selectLeastConnections(List<ServiceInstance> instances) {
        ServiceInstanceStats stats = getOrCreateStats("default");
        Map<String, AtomicLong> connectionCounts = stats.getConnectionCounts();

        ServiceInstance selectedInstance = null;
        long minConnections = Long.MAX_VALUE;

        for (ServiceInstance instance : instances) {
            AtomicLong connectionCount = connectionCounts.computeIfAbsent(
                instance.getInstanceId(), k -> new AtomicLong(0));
            
            if (connectionCount.get() < minConnections) {
                minConnections = connectionCount.get();
                selectedInstance = instance;
            }
        }

        return selectedInstance;
    }

    /**
     * 最佳响应时间选择
     */
    private ServiceInstance selectBestResponseTime(List<ServiceInstance> instances) {
        ServiceInstanceStats stats = getOrCreateStats("default");
        Map<String, AtomicLong> responseTimes = stats.getResponseTimes();

        ServiceInstance selectedInstance = null;
        long bestResponseTime = Long.MAX_VALUE;

        for (ServiceInstance instance : instances) {
            AtomicLong responseTime = responseTimes.computeIfAbsent(
                instance.getInstanceId(), k -> new AtomicLong(Long.MAX_VALUE));
            
            if (responseTime.get() < bestResponseTime) {
                bestResponseTime = responseTime.get();
                selectedInstance = instance;
            }
        }

        return selectedInstance != null ? selectedInstance : instances.get(0);
    }

    /**
     * 一致性哈希选择
     */
    private ServiceInstance selectConsistentHash(List<ServiceInstance> instances, String requestId) {
        if (instances.isEmpty()) {
            return null;
        }

        int hash = requestId.hashCode();
        int index = Math.abs(hash) % instances.size();
        return instances.get(index);
    }

    /**
     * 随机选择
     */
    private ServiceInstance selectRandom(List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        int index = (int) (Math.random() * instances.size());
        return instances.get(index);
    }

    /**
     * IP哈希选择
     */
    private ServiceInstance selectIpHash(List<ServiceInstance> instances, String requestId) {
        if (instances.isEmpty()) {
            return null;
        }

        // 从requestId中提取IP地址（假设requestId包含IP信息）
        String ip = extractIpFromRequestId(requestId);
        int hash = ip.hashCode();
        int index = Math.abs(hash) % instances.size();
        return instances.get(index);
    }

    /**
     * 更新统计信息
     */
    private void updateStats(String serviceName, ServiceInstance instance) {
        ServiceInstanceStats stats = getOrCreateStats(serviceName);
        String instanceId = instance.getInstanceId();

        // 更新连接数
        AtomicLong connectionCount = stats.getConnectionCounts()
            .computeIfAbsent(instanceId, k -> new AtomicLong(0));
        connectionCount.incrementAndGet();

        // 更新请求计数
        AtomicLong requestCount = stats.getRequestCounts()
            .computeIfAbsent(instanceId, k -> new AtomicLong(0));
        requestCount.incrementAndGet();
    }

    /**
     * 更新响应时间
     */
    public void updateResponseTime(String serviceName, String instanceId, long responseTime) {
        ServiceInstanceStats stats = getOrCreateStats(serviceName);
        AtomicLong avgResponseTime = stats.getResponseTimes()
            .computeIfAbsent(instanceId, k -> new AtomicLong(0));
        
        // 计算移动平均响应时间
        long currentAvg = avgResponseTime.get();
        long newAvg = (currentAvg + responseTime) / 2;
        avgResponseTime.set(newAvg);
    }

    /**
     * 减少连接数
     */
    public void decrementConnection(String serviceName, String instanceId) {
        ServiceInstanceStats stats = getOrCreateStats(serviceName);
        AtomicLong connectionCount = stats.getConnectionCounts().get(instanceId);
        if (connectionCount != null) {
            connectionCount.decrementAndGet();
        }
    }

    /**
     * 获取或创建统计信息
     */
    private ServiceInstanceStats getOrCreateStats(String serviceName) {
        return statsMap.computeIfAbsent(serviceName, k -> new ServiceInstanceStats());
    }

    /**
     * 获取实例权重
     */
    private int getInstanceWeight(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        String weightStr = metadata.get("weight");
        return weightStr != null ? Integer.parseInt(weightStr) : 1;
    }

    /**
     * 从请求ID中提取IP地址
     */
    private String extractIpFromRequestId(String requestId) {
        // 这里需要根据实际的requestId格式来提取IP
        // 假设requestId包含IP信息，格式如：ip:port:timestamp
        if (requestId.contains(":")) {
            return requestId.split(":")[0];
        }
        return requestId;
    }

    /**
     * 设置负载均衡策略
     */
    public void setStrategy(String serviceName, LoadBalancingStrategy strategy) {
        strategyMap.put(serviceName, strategy);
        log.info("Set load balancing strategy for service {}: {}", serviceName, strategy);
    }

    /**
     * 刷新服务实例缓存
     */
    public void refreshServiceInstances(String serviceName) {
        serviceInstanceCache.remove(serviceName);
        log.info("Refreshed service instances for: {}", serviceName);
    }

    /**
     * 获取服务统计信息
     */
    public ServiceInstanceStats getStats(String serviceName) {
        return statsMap.get(serviceName);
    }

    /**
     * 服务实例统计信息
     */
    @lombok.Data
    public static class ServiceInstanceStats {
        private final AtomicLong roundRobinCounter = new AtomicLong(0);
        private final AtomicLong currentWeight = new AtomicLong(0);
        private final AtomicLong maxWeight = new AtomicLong(1);
        private final AtomicInteger currentIndex = new AtomicInteger(-1);
        private final Map<String, AtomicLong> connectionCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> responseTimes = new ConcurrentHashMap<>();
    }
} 