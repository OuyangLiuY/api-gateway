package com.citi.tts.api.gateway.service.impl;

import com.citi.tts.api.gateway.service.ImportantServiceFallbackHandler;
import com.citi.tts.api.gateway.service.ServiceDegradationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重要服务降级处理器实现
 * 为重要业务接口提供基础兜底机制
 */
@Slf4j
@Service
public class ImportantServiceFallbackHandlerImpl implements ImportantServiceFallbackHandler {

    // 本地缓存（内存兜底）
    private final Map<String, Object> localCache = new ConcurrentHashMap<>();

    @Override
    public Mono<ServiceDegradationService.DegradationResponse> handleFallback(
            ServiceDegradationService.DegradationRequest request) {
        
        log.info("Important service fallback triggered - Service: {}, Path: {}", 
                request.getServiceName(), request.getApiPath());

        // 1. 尝试从本地缓存获取兜底数据
        return getLocalFallbackData(request)
                .flatMap(localData -> {
                    if (localData != null) {
                        log.info("Important service fallback from local cache - Service: {}", request.getServiceName());
                        return Mono.just(new ServiceDegradationService.DegradationResponse(
                            true, 
                            "Service degraded, using cached data", 
                            localData, 
                            "LOCAL_CACHE"
                        ));
                    }
                    
                    // 2. 本地缓存没有数据，尝试备用服务
                    return getBackupServiceData(request)
                            .flatMap(backupData -> {
                                if (backupData != null) {
                                    log.info("Important service fallback from backup service - Service: {}", request.getServiceName());
                                    return Mono.just(new ServiceDegradationService.DegradationResponse(
                                        true, 
                                        "Service degraded, using backup service data", 
                                        backupData, 
                                        "BACKUP_SERVICE"
                                    ));
                                }
                                
                                // 3. 备用服务也没有数据，返回基础响应兜底
                                return Mono.just(new ServiceDegradationService.DegradationResponse(
                                    false, 
                                    "Service temporarily unavailable", 
                                    getBasicFallbackData(request), 
                                    "BASIC_RESPONSE"
                                ));
                            });
                })
                .onErrorResume(e -> {
                    log.error("Important service fallback failed - Service: {}", request.getServiceName(), e);
                    return Mono.just(new ServiceDegradationService.DegradationResponse(
                        false, 
                        "Service temporarily unavailable", 
                        getBasicFallbackData(request), 
                        "ERROR"
                    ));
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
        log.debug("Attempting to get backup service data for: {}", request.getServiceName());
        
        return Mono.defer(() -> {
            // 根据服务类型返回不同的备用数据
            if (request.getServiceName().contains("user")) {
                return Mono.just(Map.of(
                    "serviceName", request.getServiceName(),
                    "status", "backup",
                    "message", "Using backup user service",
                    "data", Map.of(
                        "userId", request.getRequestData().get("userId"),
                        "status", "cached",
                        "lastUpdateTime", System.currentTimeMillis()
                    ),
                    "timestamp", System.currentTimeMillis()
                ));
            } else if (request.getServiceName().contains("account")) {
                return Mono.just(Map.of(
                    "serviceName", request.getServiceName(),
                    "status", "backup",
                    "message", "Using backup account service",
                    "data", Map.of(
                        "accountId", request.getRequestData().get("accountId"),
                        "status", "cached",
                        "balance", "N/A",
                        "lastUpdateTime", System.currentTimeMillis()
                    ),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            return Mono.empty();
        });
    }

    /**
     * 获取基础兜底数据
     */
    private Object getBasicFallbackData(ServiceDegradationService.DegradationRequest request) {
        return Map.of(
            "serviceName", request.getServiceName(),
            "status", "unavailable",
            "message", "Service is temporarily unavailable",
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