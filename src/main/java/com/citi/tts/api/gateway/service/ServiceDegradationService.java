package com.citi.tts.api.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务降级服务
 * 设计理念：服务分级降级策略
 * 
 * 分级策略：
 * 1. 核心交易接口（CORE）：提供丰富的兜底机制
 * 2. 重要业务接口（IMPORTANT）：提供基础兜底
 * 3. 普通业务接口（NORMAL）：提供简单兜底
 * 4. 非核心接口（NON_CORE）：直接失败
 */
@Slf4j
@Service
public class ServiceDegradationService {

    @Autowired
    private CoreServiceFallbackHandler coreFallbackHandler;

    @Autowired
    private ImportantServiceFallbackHandler importantFallbackHandler;

    @Autowired
    private NormalServiceFallbackHandler normalFallbackHandler;

    // 服务分级缓存
    private final Map<String, ServiceLevel> serviceLevelCache = new ConcurrentHashMap<>();

    /**
     * 服务级别枚举
     */
    public enum ServiceLevel {
        CORE("核心交易接口", 1),
        IMPORTANT("重要业务接口", 2),
        NORMAL("普通业务接口", 3),
        NON_CORE("非核心接口", 4);

        private final String description;
        private final int priority;

        ServiceLevel(String description, int priority) {
            this.description = description;
            this.priority = priority;
        }

        public String getDescription() { return description; }
        public int getPriority() { return priority; }
    }

    /**
     * 服务降级请求
     */
    public static class DegradationRequest {
        private final String serviceName;
        private final String apiPath;
        private final ServiceLevel serviceLevel;
        private final Map<String, Object> requestData;
        private final String errorType;
        private final String errorMessage;

        public DegradationRequest(String serviceName, String apiPath, ServiceLevel serviceLevel,
                                Map<String, Object> requestData, String errorType, String errorMessage) {
            this.serviceName = serviceName;
            this.apiPath = apiPath;
            this.serviceLevel = serviceLevel;
            this.requestData = requestData;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        // Getters
        public String getServiceName() { return serviceName; }
        public String getApiPath() { return apiPath; }
        public ServiceLevel getServiceLevel() { return serviceLevel; }
        public Map<String, Object> getRequestData() { return requestData; }
        public String getErrorType() { return errorType; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 服务降级响应
     */
    public static class DegradationResponse {
        private final boolean success;
        private final String message;
        private final Object data;
        private final String fallbackType;
        private final long timestamp;

        public DegradationResponse(boolean success, String message, Object data, String fallbackType) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.fallbackType = fallbackType;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
        public String getFallbackType() { return fallbackType; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 执行服务降级
     */
    public Mono<DegradationResponse> executeDegradation(DegradationRequest request) {
        log.info("Service degradation triggered - Service: {}, Level: {}, Error: {}", 
                request.getServiceName(), request.getServiceLevel(), request.getErrorType());

        switch (request.getServiceLevel()) {
            case CORE:
                return handleCoreServiceDegradation(request);
            case IMPORTANT:
                return handleImportantServiceDegradation(request);
            case NORMAL:
                return handleNormalServiceDegradation(request);
            case NON_CORE:
                return handleNonCoreServiceDegradation(request);
            default:
                return Mono.just(new DegradationResponse(false, "Unknown service level", null, "UNKNOWN"));
        }
    }

    /**
     * 核心交易接口降级处理
     * 提供最丰富的兜底机制
     */
    private Mono<DegradationResponse> handleCoreServiceDegradation(DegradationRequest request) {
        log.info("Handling CORE service degradation for: {}", request.getServiceName());
        
        return coreFallbackHandler.handleFallback(request)
                .map(response -> {
                    log.info("CORE service fallback successful - Service: {}, Type: {}", 
                            request.getServiceName(), response.getFallbackType());
                    return response;
                })
                .onErrorResume(e -> {
                    log.error("CORE service fallback failed - Service: {}", request.getServiceName(), e);
                    // 核心服务降级失败，返回基础兜底
                    return Mono.just(new DegradationResponse(
                        false, 
                        "Service temporarily unavailable, please try again later", 
                        getBasicFallbackData(request),
                        "BASIC_FALLBACK"
                    ));
                });
    }

    /**
     * 重要业务接口降级处理
     * 提供基础兜底机制
     */
    private Mono<DegradationResponse> handleImportantServiceDegradation(DegradationRequest request) {
        log.info("Handling IMPORTANT service degradation for: {}", request.getServiceName());
        
        return importantFallbackHandler.handleFallback(request)
                .map(response -> {
                    log.info("IMPORTANT service fallback successful - Service: {}, Type: {}", 
                            request.getServiceName(), response.getFallbackType());
                    return response;
                })
                .onErrorResume(e -> {
                    log.error("IMPORTANT service fallback failed - Service: {}", request.getServiceName(), e);
                    // 重要服务降级失败，返回简单兜底
                    return Mono.just(new DegradationResponse(
                        false, 
                        "Service temporarily unavailable", 
                        getSimpleFallbackData(request),
                        "SIMPLE_FALLBACK"
                    ));
                });
    }

    /**
     * 普通业务接口降级处理
     * 提供简单兜底机制
     */
    private Mono<DegradationResponse> handleNormalServiceDegradation(DegradationRequest request) {
        log.info("Handling NORMAL service degradation for: {}", request.getServiceName());
        
        return normalFallbackHandler.handleFallback(request)
                .map(response -> {
                    log.info("NORMAL service fallback successful - Service: {}, Type: {}", 
                            request.getServiceName(), response.getFallbackType());
                    return response;
                })
                .onErrorResume(e -> {
                    log.error("NORMAL service fallback failed - Service: {}", request.getServiceName(), e);
                    // 普通服务降级失败，返回错误信息
                    return Mono.just(new DegradationResponse(
                        false, 
                        "Service unavailable", 
                        null,
                        "ERROR"
                    ));
                });
    }

    /**
     * 非核心接口降级处理
     * 直接失败，不提供兜底
     */
    private Mono<DegradationResponse> handleNonCoreServiceDegradation(DegradationRequest request) {
        log.warn("NON_CORE service degradation - Service: {}, Error: {}", 
                request.getServiceName(), request.getErrorMessage());
        
        return Mono.just(new DegradationResponse(
            false, 
            "Service temporarily unavailable", 
            null,
            "DIRECT_FAILURE"
        ));
    }

    /**
     * 获取服务级别
     */
    public ServiceLevel getServiceLevel(String serviceName, String apiPath) {
        return serviceLevelCache.computeIfAbsent(serviceName + ":" + apiPath, key -> {
            // 根据服务名和API路径判断服务级别
            if (isCoreService(serviceName, apiPath)) {
                return ServiceLevel.CORE;
            } else if (isImportantService(serviceName, apiPath)) {
                return ServiceLevel.IMPORTANT;
            } else if (isNormalService(serviceName, apiPath)) {
                return ServiceLevel.NORMAL;
            } else {
                return ServiceLevel.NON_CORE;
            }
        });
    }

    /**
     * 判断是否为核心服务
     */
    private boolean isCoreService(String serviceName, String apiPath) {
        // 核心交易接口判断逻辑
        return apiPath.contains("/payment/") || 
               apiPath.contains("/transaction/") || 
               apiPath.contains("/order/") ||
               serviceName.contains("payment") ||
               serviceName.contains("transaction") ||
               serviceName.contains("order");
    }

    /**
     * 判断是否为重要服务
     */
    private boolean isImportantService(String serviceName, String apiPath) {
        // 重要业务接口判断逻辑
        return apiPath.contains("/user/") || 
               apiPath.contains("/account/") || 
               apiPath.contains("/balance/") ||
               serviceName.contains("user") ||
               serviceName.contains("account") ||
               serviceName.contains("balance");
    }

    /**
     * 判断是否为普通服务
     */
    private boolean isNormalService(String serviceName, String apiPath) {
        // 普通业务接口判断逻辑
        return apiPath.contains("/query/") || 
               apiPath.contains("/report/") || 
               apiPath.contains("/statistics/") ||
               serviceName.contains("query") ||
               serviceName.contains("report") ||
               serviceName.contains("statistics");
    }

    /**
     * 获取基础兜底数据
     */
    private Object getBasicFallbackData(DegradationRequest request) {
        return Map.of(
            "serviceName", request.getServiceName(),
            "status", "degraded",
            "message", "Service is temporarily degraded, using fallback data",
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 获取简单兜底数据
     */
    private Object getSimpleFallbackData(DegradationRequest request) {
        return Map.of(
            "serviceName", request.getServiceName(),
            "status", "unavailable",
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 更新服务级别配置
     */
    public void updateServiceLevel(String serviceName, String apiPath, ServiceLevel level) {
        String key = serviceName + ":" + apiPath;
        serviceLevelCache.put(key, level);
        log.info("Updated service level - Service: {}, Path: {}, Level: {}", serviceName, apiPath, level);
    }

    /**
     * 获取服务级别统计
     */
    public Map<String, Object> getServiceLevelStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        Map<String, Long> levelCounts = new ConcurrentHashMap<>();
        
        serviceLevelCache.values().forEach(level -> 
            levelCounts.merge(level.name(), 1L, Long::sum));
        
        stats.put("totalServices", serviceLevelCache.size());
        stats.put("levelDistribution", levelCounts);
        stats.put("timestamp", System.currentTimeMillis());
        
        return stats;
    }
} 