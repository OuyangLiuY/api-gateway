package com.citi.tts.api.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API版本管理过滤器
 * 支持多版本API管理，包括版本路由、版本兼容性检查等
 */
@Slf4j
@Component
public class ApiVersionFilter implements GatewayFilter, Ordered {

    // 版本路由映射
    private final Map<String, String> versionRouteMap = new ConcurrentHashMap<>();
    
    // 版本兼容性配置
    private final Map<String, String[]> versionCompatibility = new ConcurrentHashMap<>();

    public ApiVersionFilter() {
        initializeVersionMappings();
    }

    /**
     * 初始化版本映射
     */
    private void initializeVersionMappings() {
        // 版本路由映射
        versionRouteMap.put("v1", "http://localhost:8081");
        versionRouteMap.put("v2", "http://localhost:8082");
        versionRouteMap.put("v3", "http://localhost:8083");
        
        // 版本兼容性配置
        versionCompatibility.put("v1", new String[]{"v1"});
        versionCompatibility.put("v2", new String[]{"v1", "v2"});
        versionCompatibility.put("v3", new String[]{"v2", "v3"});
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String apiVersion = extractApiVersion(exchange);
        
        if (apiVersion != null) {
            // 1. 版本验证
            if (!isValidVersion(apiVersion)) {
                return handleInvalidVersion(exchange, apiVersion);
            }
            
            // 2. 版本兼容性检查
            if (!isCompatibleVersion(apiVersion, exchange)) {
                return handleIncompatibleVersion(exchange, apiVersion);
            }
            
            // 3. 版本路由
            ServerWebExchange versionedExchange = routeToVersion(exchange, apiVersion);
            
            // 4. 添加版本信息到响应头
            versionedExchange.getResponse().getHeaders().add("X-API-Version", apiVersion);
            versionedExchange.getResponse().getHeaders().add("X-API-Version-Supported", 
                String.join(",", getSupportedVersions()));
            
            return chain.filter(versionedExchange);
        }
        
        // 没有版本信息，使用默认版本
        return chain.filter(exchange);
    }

    /**
     * 提取API版本
     */
    private String extractApiVersion(ServerWebExchange exchange) {
        // 1. 从URL路径提取版本
        String path = exchange.getRequest().getPath().value();
        if (path.matches("/api/v\\d+/.*")) {
            String[] pathParts = path.split("/");
            if (pathParts.length >= 3 && pathParts[2].startsWith("v")) {
                return pathParts[2];
            }
        }
        
        // 2. 从请求头提取版本
        String versionHeader = exchange.getRequest().getHeaders().getFirst("X-API-Version");
        if (versionHeader != null && !versionHeader.isEmpty()) {
            return versionHeader;
        }
        
        // 3. 从Accept头提取版本
        String accept = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains("version=")) {
            String[] parts = accept.split(";");
            for (String part : parts) {
                if (part.trim().startsWith("version=")) {
                    return part.trim().substring(8);
                }
            }
        }
        
        // 4. 从查询参数提取版本
        String versionParam = exchange.getRequest().getQueryParams().getFirst("version");
        if (versionParam != null && !versionParam.isEmpty()) {
            return versionParam;
        }
        
        return null;
    }

    /**
     * 验证版本是否有效
     */
    private boolean isValidVersion(String version) {
        return versionRouteMap.containsKey(version);
    }

    /**
     * 检查版本兼容性
     */
    private boolean isCompatibleVersion(String version, ServerWebExchange exchange) {
        String[] compatibleVersions = versionCompatibility.get(version);
        if (compatibleVersions == null) {
            return false;
        }
        
        // 检查请求的资源是否在兼容版本中
        String resource = extractResource(exchange);
        return isResourceAvailableInVersion(resource, compatibleVersions);
    }

    /**
     * 提取资源名称
     */
    private String extractResource(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String[] pathParts = path.split("/");
        if (pathParts.length >= 4) {
            return pathParts[3]; // 跳过 /api/v1/ 部分
        }
        return "";
    }

    /**
     * 检查资源在指定版本中是否可用
     */
    private boolean isResourceAvailableInVersion(String resource, String[] versions) {
        // 这里可以实现更复杂的资源可用性检查逻辑
        // 暂时返回true，表示所有资源在所有版本中都可用
        return true;
    }

    /**
     * 路由到指定版本
     */
    private ServerWebExchange routeToVersion(ServerWebExchange exchange, String version) {
        String targetUri = versionRouteMap.get(version);
        if (targetUri != null) {
            log.debug("Routing API version {} to {}", version, targetUri);
            
            // 这里可以修改请求URI，将版本信息传递给后端服务
            // 实际实现中需要根据具体的路由策略来处理
            
            return exchange;
        }
        
        return exchange;
    }

    /**
     * 处理无效版本
     */
    private Mono<Void> handleInvalidVersion(ServerWebExchange exchange, String version) {
        log.warn("Invalid API version requested: {}", version);
        
        exchange.getResponse().setRawStatusCode(400);
        exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        
        String errorResponse = String.format(
            "{\"error\":\"Invalid API version\",\"requestedVersion\":\"%s\",\"supportedVersions\":\"%s\"}",
            version, String.join(",", getSupportedVersions())
        );
        
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes()))
        );
    }

    /**
     * 处理不兼容版本
     */
    private Mono<Void> handleIncompatibleVersion(ServerWebExchange exchange, String version) {
        log.warn("Incompatible API version requested: {}", version);
        
        exchange.getResponse().setRawStatusCode(400);
        exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        
        String errorResponse = String.format(
            "{\"error\":\"Incompatible API version\",\"requestedVersion\":\"%s\",\"message\":\"This version is not compatible with the requested resource\"}",
            version
        );
        
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes()))
        );
    }

    /**
     * 获取支持的版本列表
     */
    private String[] getSupportedVersions() {
        return versionRouteMap.keySet().toArray(new String[0]);
    }

    /**
     * 添加版本路由映射
     */
    public void addVersionRoute(String version, String targetUri) {
        versionRouteMap.put(version, targetUri);
        log.info("Added version route: {} -> {}", version, targetUri);
    }

    /**
     * 设置版本兼容性
     */
    public void setVersionCompatibility(String version, String[] compatibleVersions) {
        versionCompatibility.put(version, compatibleVersions);
        log.info("Set version compatibility: {} -> {}", version, String.join(",", compatibleVersions));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 150; // 在协议适配之后
    }
} 