package com.citi.tts.api.gateway.service.impl;

import com.citi.tts.api.gateway.service.NormalServiceFallbackHandler;
import com.citi.tts.api.gateway.service.ServiceDegradationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 普通服务降级处理器实现
 * 为普通业务接口提供简单兜底机制
 */
@Slf4j
@Service
public class NormalServiceFallbackHandlerImpl implements NormalServiceFallbackHandler {

    // 本地缓存（内存兜底）
    private final Map<String, Object> localCache = new ConcurrentHashMap<>();

    @Override
    public Mono<ServiceDegradationService.DegradationResponse> handleFallback(
            ServiceDegradationService.DegradationRequest request) {
        
        log.info("Normal service fallback triggered - Service: {}, Path: {}", 
                request.getServiceName(), request.getApiPath());

        // 1. 尝试从本地缓存获取兜底数据
        return getLocalFallbackData(request)
                .flatMap(localData -> {
                    if (localData != null) {
                        log.info("Normal service fallback from local cache - Service: {}", request.getServiceName());
                        return Mono.just(new ServiceDegradationService.DegradationResponse(
                            true, 
                            "Service degraded, using cached data", 
                            localData, 
                            "LOCAL_CACHE"
                        ));
                    }
                    
                    // 2. 本地缓存没有数据，返回基础响应兜底
                    return Mono.just(new ServiceDegradationService.DegradationResponse(
                        false, 
                        "Service unavailable", 
                        getBasicFallbackData(request), 
                        "BASIC_RESPONSE"
                    ));
                })
                .onErrorResume(e -> {
                    log.error("Normal service fallback failed - Service: {}", request.getServiceName(), e);
                    return Mono.just(new ServiceDegradationService.DegradationResponse(
                        false, 
                        "Service unavailable", 
                        null, 
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
     * 获取基础兜底数据
     */
    private Object getBasicFallbackData(ServiceDegradationService.DegradationRequest request) {
        return Map.of(
            "serviceName", request.getServiceName(),
            "status", "unavailable",
            "message", "Service is temporarily unavailable",
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