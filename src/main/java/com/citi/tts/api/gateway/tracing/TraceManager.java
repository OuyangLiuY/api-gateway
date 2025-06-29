package com.citi.tts.api.gateway.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 追踪管理器
 * 管理追踪上下文和采样策略
 */
@Slf4j
@Component
public class TraceManager {

    @Value("${tracing.sampling.rate:1.0}")
    private double samplingRate = 1.0; // 采样率，1.0表示100%采样

    @Value("${tracing.sampling.enabled:true}")
    private boolean samplingEnabled = true;

    @Value("${tracing.max-spans-per-trace:100}")
    private int maxSpansPerTrace = 100;

    // 追踪上下文缓存（按traceId）
    private final ConcurrentHashMap<String, TraceContext> traceContexts = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicLong totalTraces = new AtomicLong(0);
    private final AtomicLong sampledTraces = new AtomicLong(0);
    private final AtomicLong droppedTraces = new AtomicLong(0);

    /**
     * 创建或获取追踪上下文
     */
    public Mono<TraceContext> getOrCreateTraceContext(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            String traceId = extractTraceId(exchange);
            
            if (traceId != null) {
                // 如果请求中已有traceId，尝试获取现有上下文
                TraceContext existingContext = traceContexts.get(traceId);
                if (existingContext != null) {
                    log.debug("Reusing existing trace context: {}", traceId);
                    return existingContext;
                }
            }
            
            // 创建新的追踪上下文
            TraceContext context = TraceContext.createRoot(exchange);
            
            // 应用采样策略
            if (shouldSample(context)) {
                traceContexts.put(context.getTraceId(), context);
                sampledTraces.incrementAndGet();
                log.debug("Created new sampled trace context: {}", context.getTraceId());
            } else {
                context.setSampled(false);
                droppedTraces.incrementAndGet();
                log.debug("Created new non-sampled trace context: {}", context.getTraceId());
            }
            
            totalTraces.incrementAndGet();
            return context;
        });
    }

    /**
     * 获取追踪上下文
     */
    public TraceContext getTraceContext(String traceId) {
        return traceContexts.get(traceId);
    }

    /**
     * 移除追踪上下文
     */
    public void removeTraceContext(String traceId) {
        TraceContext removed = traceContexts.remove(traceId);
        if (removed != null) {
            log.debug("Removed trace context: {}", traceId);
        }
    }

    /**
     * 清理过期的追踪上下文
     */
    public void cleanupExpiredTraces(long maxAgeMs) {
        long now = System.currentTimeMillis();
        final AtomicInteger cleanedCount = new AtomicInteger(0);
        
        traceContexts.entrySet().removeIf(entry -> {
            TraceContext context = entry.getValue();
            if (context.getStartTime() != null && 
                now - context.getStartTime().toEpochMilli() > maxAgeMs) {
                cleanedCount.incrementAndGet();
                return true;
            }
            return false;
        });
        
        if (cleanedCount.get() > 0) {
            log.info("Cleaned up {} expired trace contexts", cleanedCount.get());
        }
    }

    /**
     * 判断是否应该采样
     */
    private boolean shouldSample(TraceContext context) {
        if (!samplingEnabled) {
            return false;
        }
        
        if (samplingRate >= 1.0) {
            return true;
        }
        
        if (samplingRate <= 0.0) {
            return false;
        }
        
        // 基于traceId的哈希值进行采样
        int hash = context.getTraceId().hashCode();
        double normalizedHash = Math.abs(hash) / (double) Integer.MAX_VALUE;
        
        return normalizedHash <= samplingRate;
    }

    /**
     * 从请求中提取traceId
     */
    private String extractTraceId(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst("X-Trace-ID");
    }

    /**
     * 获取统计信息
     */
    public TraceStatistics getStatistics() {
        return TraceStatistics.builder()
                .totalTraces(totalTraces.get())
                .sampledTraces(sampledTraces.get())
                .droppedTraces(droppedTraces.get())
                .activeTraces(traceContexts.size())
                .samplingRate(samplingRate)
                .samplingEnabled(samplingEnabled)
                .maxSpansPerTrace(maxSpansPerTrace)
                .build();
    }

    /**
     * 设置采样率
     */
    public void setSamplingRate(double rate) {
        this.samplingRate = Math.max(0.0, Math.min(1.0, rate));
        log.info("Updated sampling rate to: {}", this.samplingRate);
    }

    /**
     * 启用/禁用采样
     */
    public void setSamplingEnabled(boolean enabled) {
        this.samplingEnabled = enabled;
        log.info("Sampling enabled: {}", this.samplingEnabled);
    }

    /**
     * 获取活跃追踪数量
     */
    public int getActiveTraceCount() {
        return traceContexts.size();
    }

    /**
     * 获取追踪上下文缓存大小
     */
    public int getCacheSize() {
        return traceContexts.size();
    }

    /**
     * 强制采样指定追踪
     */
    public void forceSample(String traceId) {
        TraceContext context = traceContexts.get(traceId);
        if (context != null) {
            context.setSampled(true);
            log.debug("Forced sampling for trace: {}", traceId);
        }
    }

    /**
     * 追踪统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class TraceStatistics {
        private long totalTraces;
        private long sampledTraces;
        private long droppedTraces;
        private int activeTraces;
        private double samplingRate;
        private boolean samplingEnabled;
        private int maxSpansPerTrace;
    }
} 