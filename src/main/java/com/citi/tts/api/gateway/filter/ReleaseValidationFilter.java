package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.release.ReleaseValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 发布验证过滤器
 * 集成ReleaseValidationService，实现A/B测试、灰度发布、金丝雀发布等功能
 */
@Slf4j
@Component
public class ReleaseValidationFilter implements GatewayFilter, Ordered {

    @Autowired
    private ReleaseValidationService releaseValidationService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 检查是否有活跃的发布策略
        String strategyId = getActiveStrategyId(path);
        if (strategyId == null) {
            // 没有活跃策略，正常处理请求
            return chain.filter(exchange);
        }

        // 构建分流条件
        ReleaseValidationService.TrafficCriteria criteria = buildTrafficCriteria(exchange);
        
        // 判断是否应该路由到新版本
        boolean shouldRouteToNewVersion = releaseValidationService.shouldRouteToNewVersion(strategyId, criteria);
        
        log.debug("Release validation - Strategy: {}, Path: {}, Route to new version: {}", 
                strategyId, path, shouldRouteToNewVersion);

        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        
        // 根据策略决定路由到哪个版本
        if (shouldRouteToNewVersion) {
            // 路由到新版本
            return routeToNewVersion(exchange, chain, strategyId, startTime);
        } else {
            // 路由到旧版本
            return routeToOldVersion(exchange, chain, strategyId, startTime);
        }
    }

    /**
     * 获取活跃的发布策略ID
     */
    private String getActiveStrategyId(String path) {
        // 从路径中提取服务名称
        String serviceName = extractServiceName(path);
        
        // 查找该服务的活跃策略
        for (ReleaseValidationService.ReleaseStrategy strategy : releaseValidationService.getAllReleaseStrategies()) {
            if (strategy.getServiceName().equals(serviceName) && 
                strategy.isEnabled() && 
                strategy.getStatus() == ReleaseValidationService.ReleaseStatus.RUNNING) {
                return strategy.getId();
            }
        }
        
        return null;
    }

    /**
     * 从路径中提取服务名称
     */
    private String extractServiceName(String path) {
        // 示例路径: /api/user-service/users -> user-service
        String[] pathParts = path.split("/");
        if (pathParts.length >= 3 && pathParts[2].contains("-")) {
            return pathParts[2];
        }
        return "default-service";
    }

    /**
     * 构建分流条件
     */
    private ReleaseValidationService.TrafficCriteria buildTrafficCriteria(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        ReleaseValidationService.TrafficCriteria criteria = new ReleaseValidationService.TrafficCriteria();
        
        // 提取用户ID（从请求头或Cookie）
        String userId = request.getHeaders().getFirst("X-User-ID");
        if (userId == null) {
            userId = request.getHeaders().getFirst("Authorization");
        }
        criteria.setUserId(userId);
        
        // 提取IP地址
        String ipAddress = getClientIp(request);
        criteria.setIpAddress(ipAddress);
        
        // 提取用户代理
        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
        criteria.setUserAgent(userAgent);
        
        // 提取请求路径
        criteria.setRequestPath(request.getPath().value());
        
        // 提取HTTP方法
        criteria.setHttpMethod(request.getMethod().name());
        
        // 提取请求头
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });
        criteria.setHeaders(headers);
        
        return criteria;
    }

    /**
     * 获取客户端IP
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
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    /**
     * 路由到新版本
     */
    private Mono<Void> routeToNewVersion(ServerWebExchange exchange, GatewayFilterChain chain, 
                                        String strategyId, long startTime) {
        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    // 请求成功
                    long responseTime = System.currentTimeMillis() - startTime;
                    releaseValidationService.updateReleaseStats(strategyId, true, true, responseTime, null);
                    
                    // 添加版本标识到响应头
                    exchange.getResponse().getHeaders().add("X-Release-Version", "new");
                    exchange.getResponse().getHeaders().add("X-Release-Strategy", strategyId);
                })
                .doOnError(throwable -> {
                    // 请求失败
                    long responseTime = System.currentTimeMillis() - startTime;
                    String errorType = throwable.getClass().getSimpleName();
                    releaseValidationService.updateReleaseStats(strategyId, true, false, responseTime, errorType);
                    
                    log.error("New version request failed - Strategy: {}, Error: {}", 
                            strategyId, throwable.getMessage());
                });
    }

    /**
     * 路由到旧版本
     */
    private Mono<Void> routeToOldVersion(ServerWebExchange exchange, GatewayFilterChain chain, 
                                        String strategyId, long startTime) {
        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    // 请求成功
                    long responseTime = System.currentTimeMillis() - startTime;
                    releaseValidationService.updateReleaseStats(strategyId, false, true, responseTime, null);
                    
                    // 添加版本标识到响应头
                    exchange.getResponse().getHeaders().add("X-Release-Version", "old");
                    exchange.getResponse().getHeaders().add("X-Release-Strategy", strategyId);
                })
                .doOnError(throwable -> {
                    // 请求失败
                    long responseTime = System.currentTimeMillis() - startTime;
                    String errorType = throwable.getClass().getSimpleName();
                    releaseValidationService.updateReleaseStats(strategyId, false, false, responseTime, errorType);
                    
                    log.error("Old version request failed - Strategy: {}, Error: {}", 
                            strategyId, throwable.getMessage());
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200; // 在版本管理之后，限流之前
    }
} 