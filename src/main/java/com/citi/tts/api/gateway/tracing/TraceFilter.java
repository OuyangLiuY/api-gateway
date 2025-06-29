package com.citi.tts.api.gateway.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 链路追踪过滤器
 * 自动为每个请求创建追踪上下文并注入追踪头信息
 */
@Slf4j
@Component
public class TraceFilter implements GlobalFilter, Ordered {

    @Autowired
    private TraceManager traceManager;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        
        return traceManager.getOrCreateTraceContext(exchange)
                .flatMap(traceContext -> {
                    // 添加追踪头信息到请求
                    ServerHttpRequest request = addTraceHeaders(exchange.getRequest(), traceContext);
                    ServerWebExchange tracedExchange = exchange.mutate().request(request).build();
                    
                    // 记录请求开始事件
                    traceContext.addEvent("request.start", "Request processing started");
                    
                    return chain.filter(tracedExchange)
                            .doFinally(signalType -> {
                                // 记录请求结束事件
                                ServerHttpResponse response = tracedExchange.getResponse();
                                var status = response.getStatusCode();
                                String errorMessage = null;
                                
                                if (status != null && status.isError()) {
                                    errorMessage = "HTTP " + status.value() + " " + status.toString();
                                }
                                
                                traceContext.finish(
                                    status != null ? status.value() : null,
                                    errorMessage
                                );
                                
                                // 记录性能指标
                                long duration = System.currentTimeMillis() - startTime;
                                traceContext.addTag("duration.ms", String.valueOf(duration));
                                traceContext.addTag("signal.type", signalType.name());
                                
                                // 记录响应头信息
                                if (response.getHeaders().getContentType() != null) {
                                    traceContext.addTag("response.content_type", 
                                        response.getHeaders().getContentType().toString());
                                }
                                
                                // 添加追踪头信息到响应
                                addTraceHeadersToResponse(response, traceContext);
                                
                                // 如果追踪已采样，记录详细信息
                                if (traceContext.isSampled()) {
                                    logTraceInfo(traceContext, duration);
                                }
                                
                                // 清理追踪上下文（可选，也可以保留一段时间用于调试）
                                // traceManager.removeTraceContext(traceContext.getTraceId());
                            });
                })
                .onErrorResume(throwable -> {
                    // 错误处理
                    log.error("Error in trace filter", throwable);
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        // 在限流过滤器之前执行，确保所有请求都被追踪
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    /**
     * 添加追踪头信息到请求
     */
    private ServerHttpRequest addTraceHeaders(ServerHttpRequest request, TraceContext traceContext) {
        ServerHttpRequest.Builder builder = request.mutate();
        
        Map<String, String> traceHeaders = traceContext.getTraceHeaders();
        for (Map.Entry<String, String> entry : traceHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        
        return builder.build();
    }

    /**
     * 添加追踪头信息到响应
     */
    private void addTraceHeadersToResponse(ServerHttpResponse response, TraceContext traceContext) {
        Map<String, String> traceHeaders = traceContext.getTraceHeaders();
        for (Map.Entry<String, String> entry : traceHeaders.entrySet()) {
            response.getHeaders().add(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 记录追踪信息
     */
    private void logTraceInfo(TraceContext traceContext, long duration) {
        if (log.isDebugEnabled()) {
            log.debug("Trace completed - TraceId: {}, SpanId: {}, Duration: {}ms, Status: {}, Path: {}", 
                    traceContext.getTraceId(),
                    traceContext.getSpanId(),
                    duration,
                    traceContext.getStatusCode(),
                    traceContext.getTags().get("http.path"));
        }
        
        // 记录慢请求
        if (duration > 1000) { // 超过1秒的请求
            log.warn("Slow request detected - TraceId: {}, Duration: {}ms, Path: {}", 
                    traceContext.getTraceId(), duration, traceContext.getTags().get("http.path"));
        }
        
        // 记录错误请求
        if (traceContext.getStatusCode() != null && traceContext.getStatusCode() >= 400) {
            log.warn("Error request detected - TraceId: {}, Status: {}, Path: {}, Error: {}", 
                    traceContext.getTraceId(), 
                    traceContext.getStatusCode(), 
                    traceContext.getTags().get("http.path"),
                    traceContext.getErrorMessage());
        }
    }
} 