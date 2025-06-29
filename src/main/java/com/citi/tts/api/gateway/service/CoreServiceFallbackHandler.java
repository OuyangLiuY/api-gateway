package com.citi.tts.api.gateway.service;

import reactor.core.publisher.Mono;

/**
 * 核心服务降级处理器
 * 为核心交易接口提供丰富的兜底机制
 */
public interface CoreServiceFallbackHandler {
    
    /**
     * 处理核心服务降级
     * 提供最丰富的兜底机制：
     * 1. 缓存数据兜底
     * 2. 备用服务兜底
     * 3. 本地计算兜底
     * 4. 异步处理兜底
     */
    Mono<ServiceDegradationService.DegradationResponse> handleFallback(
        ServiceDegradationService.DegradationRequest request
    );
} 