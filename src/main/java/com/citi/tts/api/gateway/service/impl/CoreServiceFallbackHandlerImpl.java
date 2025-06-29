package com.citi.tts.api.gateway.service.impl;

import com.citi.tts.api.gateway.service.CoreServiceFallbackHandler;
import com.citi.tts.api.gateway.service.ServiceDegradationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 核心服务降级处理器实现
 * 为核心交易接口提供最丰富的兜底机制
 */
@Slf4j
@Service
public class CoreServiceFallbackHandlerImpl implements CoreServiceFallbackHandler {

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    // 本地缓存（内存兜底）
    private final Map<String, Object> localCache = new ConcurrentHashMap<>();

    @Override
    public Mono<ServiceDegradationService.DegradationResponse> handleFallback(
            ServiceDegradationService.DegradationRequest request) {
        
        log.info("Core service fallback triggered - Service: {}, Path: {}", 
                request.getServiceName(), request.getApiPath());

        // 1. 尝试从Redis缓存获取兜底数据
        return getRedisFallbackData(request)
                .flatMap(redisData -> {
                    if (redisData != null) {
                        log.info("Core service fallback from Redis cache - Service: {}", request.getServiceName());
                        return Mono.just(new ServiceDegradationService.DegradationResponse(
                            true, 
                            "Service degraded, using cached data", 
                            redisData, 
                            "REDIS_CACHE"
                        ));
                    }
                    
                    // 2. Redis没有数据，尝试从本地缓存获取
                    return getLocalFallbackData(request)
                            .flatMap(localData -> {
                                if (localData != null) {
                                    log.info("Core service fallback from local cache - Service: {}", request.getServiceName());
                                    return Mono.just(new ServiceDegradationService.DegradationResponse(
                                        true, 
                                        "Service degraded, using local cached data", 
                                        localData, 
                                        "LOCAL_CACHE"
                                    ));
                                }
                                
                                // 3. 本地缓存也没有数据，尝试备用服务
                                return getBackupServiceData(request)
                                        .flatMap(backupData -> {
                                            if (backupData != null) {
                                                log.info("Core service fallback from backup service - Service: {}", request.getServiceName());
                                                return Mono.just(new ServiceDegradationService.DegradationResponse(
                                                    true, 
                                                    "Service degraded, using backup service data", 
                                                    backupData, 
                                                    "BACKUP_SERVICE"
                                                ));
                                            }
                                            
                                            // 4. 备用服务也没有数据，使用本地计算兜底
                                            return getLocalComputedData(request)
                                                    .flatMap(computedData -> {
                                                        log.info("Core service fallback from local computation - Service: {}", request.getServiceName());
                                                        return Mono.just(new ServiceDegradationService.DegradationResponse(
                                                            true, 
                                                            "Service degraded, using computed data", 
                                                            computedData, 
                                                            "LOCAL_COMPUTATION"
                                                        ));
                                                    });
                                        });
                            });
                })
                .onErrorResume(e -> {
                    log.error("Core service fallback failed - Service: {}", request.getServiceName(), e);
                    // 所有兜底机制都失败，返回基础兜底
                    return Mono.just(new ServiceDegradationService.DegradationResponse(
                        false, 
                        "Service temporarily unavailable, please try again later", 
                        getBasicFallbackData(request), 
                        "BASIC_FALLBACK"
                    ));
                });
    }

    /**
     * 从Redis缓存获取兜底数据
     */
    private Mono<Object> getRedisFallbackData(ServiceDegradationService.DegradationRequest request) {
        String cacheKey = "fallback:core:" + request.getServiceName() + ":" + request.getApiPath();
        return redisTemplate.opsForValue().get(cacheKey)
                .doOnSuccess(data -> {
                    if (data != null) {
                        log.debug("Redis fallback data found for key: {}", cacheKey);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Failed to get Redis fallback data for key: {}", cacheKey, e);
                    return Mono.empty();
                });
    }

    /**
     * 从本地缓存获取兜底数据
     */
    private Mono<Object> getLocalFallbackData(ServiceDegradationService.DegradationRequest request) {
        String cacheKey = request.getServiceName() + ":" + request.getApiPath();
        Object data = localCache.get(cacheKey);
        
        if (data != null) {
            log.debug("Local fallback data found for key: {}", cacheKey);
            return Mono.just(data);
        }
        
        return Mono.empty();
    }

    /**
     * 从备用服务获取兜底数据
     */
    private Mono<Object> getBackupServiceData(ServiceDegradationService.DegradationRequest request) {
        // 这里可以实现调用备用服务的逻辑
        // 例如：调用备用数据中心、备用服务实例等
        
        log.debug("Attempting to get backup service data for: {}", request.getServiceName());
        
        // 模拟备用服务调用
        return Mono.defer(() -> {
            // 根据服务类型返回不同的备用数据
            if (request.getServiceName().contains("payment")) {
                return Mono.just(Map.of(
                    "serviceName", request.getServiceName(),
                    "status", "backup",
                    "message", "Using backup payment service",
                    "data", Map.of(
                        "transactionId", "BACKUP_" + System.currentTimeMillis(),
                        "status", "pending",
                        "amount", request.getRequestData().get("amount")
                    ),
                    "timestamp", System.currentTimeMillis()
                ));
            } else if (request.getServiceName().contains("order")) {
                return Mono.just(Map.of(
                    "serviceName", request.getServiceName(),
                    "status", "backup",
                    "message", "Using backup order service",
                    "data", Map.of(
                        "orderId", "BACKUP_" + System.currentTimeMillis(),
                        "status", "processing",
                        "items", request.getRequestData().get("items")
                    ),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            return Mono.empty();
        });
    }

    /**
     * 使用本地计算生成兜底数据
     */
    private Mono<Object> getLocalComputedData(ServiceDegradationService.DegradationRequest request) {
        log.debug("Computing local fallback data for: {}", request.getServiceName());
        
        return Mono.defer(() -> {
            // 根据服务类型进行本地计算
            if (request.getServiceName().contains("payment")) {
                // 支付服务的本地计算兜底
                return Mono.just(Map.of(
                    "serviceName", request.getServiceName(),
                    "status", "computed",
                    "message", "Using computed payment data",
                    "data", Map.of(
                        "transactionId", "COMPUTED_" + System.currentTimeMillis(),
                        "status", "estimated",
                        "amount", request.getRequestData().get("amount"),
                        "estimatedCompletionTime", System.currentTimeMillis() + 300000 // 5分钟后
                    ),
                    "timestamp", System.currentTimeMillis()
                ));
            } else if (request.getServiceName().contains("order")) {
                // 订单服务的本地计算兜底
                return Mono.just(Map.of(
                    "serviceName", request.getServiceName(),
                    "status", "computed",
                    "message", "Using computed order data",
                    "data", Map.of(
                        "orderId", "COMPUTED_" + System.currentTimeMillis(),
                        "status", "estimated",
                        "estimatedDeliveryTime", System.currentTimeMillis() + 86400000, // 24小时后
                        "items", request.getRequestData().get("items")
                    ),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // 默认计算兜底
            return Mono.just(Map.of(
                "serviceName", request.getServiceName(),
                "status", "computed",
                "message", "Using computed fallback data",
                "data", request.getRequestData(),
                "timestamp", System.currentTimeMillis()
            ));
        });
    }

    /**
     * 获取基础兜底数据
     */
    private Object getBasicFallbackData(ServiceDegradationService.DegradationRequest request) {
        return Map.of(
            "serviceName", request.getServiceName(),
            "status", "degraded",
            "message", "Service is temporarily degraded, please try again later",
            "errorType", request.getErrorType(),
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 更新本地缓存
     */
    public void updateLocalCache(String serviceName, String apiPath, Object data) {
        String cacheKey = serviceName + ":" + apiPath;
        localCache.put(cacheKey, data);
        log.debug("Updated local cache for key: {}", cacheKey);
    }

    /**
     * 清理本地缓存
     */
    public void clearLocalCache() {
        localCache.clear();
        log.info("Local cache cleared");
    }

    /**
     * 获取本地缓存统计
     */
    public Map<String, Object> getLocalCacheStats() {
        return Map.of(
            "size", localCache.size(),
            "timestamp", System.currentTimeMillis()
        );
    }
} 