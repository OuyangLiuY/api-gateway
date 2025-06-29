package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.metrics.QPSMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * QPS统计过滤器
 * 记录请求的QPS统计信息
 */
@Slf4j
@Component
public class QPSMetricsFilter implements GlobalFilter, Ordered {

    @Autowired
    private QPSMetrics qpsMetrics;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 获取请求路径
        String path = request.getPath().value();
        
        // 获取客户端IP
        String clientIp = getClientIp(request);
        
        // 获取用户ID（从请求头或token中提取）
        String userId = extractUserId(request);
        
        // 获取API优先级（从请求头或路径中提取）
        String priority = extractPriority(request, path);
        
        // 记录QPS统计
        qpsMetrics.recordRequest(path, clientIp, userId, priority);
        
        log.debug("QPS recorded - Path: {}, IP: {}, User: {}, Priority: {}", 
                path, clientIp, userId, priority);
        
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 在限流过滤器之前执行，确保所有请求都被统计
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(ServerHttpRequest request) {
        // 优先从X-Forwarded-For获取
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 从X-Real-IP获取
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 从远程地址获取
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    /**
     * 提取用户ID
     */
    private String extractUserId(ServerHttpRequest request) {
        // 从请求头获取用户ID
        String userId = request.getHeaders().getFirst("X-User-ID");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        
        // 从Authorization头中提取用户ID（JWT token）
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            // 这里可以解析JWT token获取用户ID
            // 简化处理，实际项目中需要JWT解析
            return "jwt-user";
        }
        
        return "anonymous";
    }

    /**
     * 提取API优先级
     */
    private String extractPriority(ServerHttpRequest request, String path) {
        // 从请求头获取优先级
        String priority = request.getHeaders().getFirst("X-API-Priority");
        if (priority != null && !priority.isEmpty()) {
            return priority;
        }
        
        // 根据路径判断优先级
        if (path.startsWith("/api/core/")) {
            return "core";
        } else if (path.startsWith("/api/normal/")) {
            return "normal";
        } else if (path.startsWith("/api/non-core/")) {
            return "non-core";
        } else if (path.startsWith("/api/payment/")) {
            return "payment";
        } else if (path.startsWith("/api/user/")) {
            return "user";
        } else if (path.startsWith("/api/admin/")) {
            return "admin";
        }
        
        return "default";
    }
} 