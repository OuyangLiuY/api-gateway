package com.citi.tts.api.gateway.service;

import reactor.core.publisher.Mono;

/**
 * 重要服务降级处理器
 * 为重要业务接口提供基础兜底机制。
 */
public interface ImportantServiceFallbackHandler {
    
    /**
     * 处理重要服务降级
     * 提供基础兜底机制：
     * 1. 缓存数据兜底
     * 2. 备用服务兜底
     * 3. 基础响应兜底
     */
    Mono<ServiceDegradationService.DegradationResponse> handleFallback(
        ServiceDegradationService.DegradationRequest request
    );
} 