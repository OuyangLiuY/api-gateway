package com.citi.tts.api.gateway.service;

import reactor.core.publisher.Mono;

/**
 * 普通服务降级处理器
 * 为普通业务接口提供简单兜底机制
 */
public interface NormalServiceFallbackHandler {
    
    /**
     * 处理普通服务降级
     * 提供简单兜底机制：
     * 1. 缓存数据兜底
     * 2. 基础响应兜底
     */
    Mono<ServiceDegradationService.DegradationResponse> handleFallback(
        ServiceDegradationService.DegradationRequest request
    );
} 