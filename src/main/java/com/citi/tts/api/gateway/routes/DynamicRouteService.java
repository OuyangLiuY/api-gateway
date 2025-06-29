package com.citi.tts.api.gateway.routes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动态路由服务
 * 支持路由的动态增删改查和热更新
 */
@Slf4j
@Service
public class DynamicRouteService {

    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private ApplicationEventPublisher publisher;

    // 路由定义缓存
    private final Map<String, RouteDefinition> routeCache = new ConcurrentHashMap<>();
    
    // 路由统计信息
    private final Map<String, RouteStats> routeStatsMap = new ConcurrentHashMap<>();
    
    // 路由版本号
    private final AtomicLong routeVersion = new AtomicLong(1);

    /**
     * 路由定义
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DynamicRouteDefinition {
        private String id;                    // 路由ID
        private String name;                  // 路由名称
        private String description;           // 路由描述
        private List<String> predicates;      // 断言列表
        private List<String> filters;         // 过滤器列表
        private String uri;                   // 目标URI
        private int order;                    // 路由顺序
        private boolean enabled;              // 是否启用
        private Map<String, Object> metadata; // 元数据
        private String serviceLevel;          // 服务级别
        private int weight;                   // 权重
        private String loadBalancerStrategy;  // 负载均衡策略
        private int timeout;                  // 超时时间
        private int retryCount;               // 重试次数
        private boolean circuitBreakerEnabled; // 是否启用熔断器
        private boolean rateLimitEnabled;     // 是否启用限流
        private int qps;                      // QPS限制
        private String fallbackUri;           // 降级URI
    }

    /**
     * 路由统计信息
     */
    @Data
    public static class RouteStats {
        private String routeId;
        private long totalRequests;
        private long successRequests;
        private long failedRequests;
        private double avgResponseTime;
        private LocalDateTime lastAccessTime;
        private String status; // ACTIVE, INACTIVE, ERROR
        private Map<String, Long> errorCounts = new HashMap<>();
    }

    /**
     * 添加路由
     */
    public Mono<Boolean> addRoute(DynamicRouteDefinition routeDef) {
        try {
            RouteDefinition routeDefinition = convertToRouteDefinition(routeDef);
            
            // 验证路由定义
            if (!validateRouteDefinition(routeDefinition)) {
                log.error("Invalid route definition: {}", routeDef.getId());
                return Mono.just(false);
            }

            // 添加到缓存
            routeCache.put(routeDef.getId(), routeDefinition);
            
            // 保存路由统计信息
            RouteStats stats = new RouteStats();
            stats.setRouteId(routeDef.getId());
            stats.setStatus("ACTIVE");
            stats.setLastAccessTime(LocalDateTime.now());
            routeStatsMap.put(routeDef.getId(), stats);

            // 写入路由定义
            return routeDefinitionWriter.save(Mono.just(routeDefinition))
                    .then(Mono.fromRunnable(() -> {
                        // 发布路由刷新事件
                        publisher.publishEvent(new RefreshRoutesEvent(this));
                        log.info("Route added successfully: {}", routeDef.getId());
                    }))
                    .thenReturn(true)
                    .onErrorReturn(false);

        } catch (Exception e) {
            log.error("Failed to add route: {}", routeDef.getId(), e);
            return Mono.just(false);
        }
    }

    /**
     * 更新路由
     */
    public Mono<Boolean> updateRoute(DynamicRouteDefinition routeDef) {
        try {
            RouteDefinition routeDefinition = convertToRouteDefinition(routeDef);
            
            // 验证路由定义
            if (!validateRouteDefinition(routeDefinition)) {
                log.error("Invalid route definition: {}", routeDef.getId());
                return Mono.just(false);
            }

            // 更新缓存
            routeCache.put(routeDef.getId(), routeDefinition);

            // 删除旧路由
            return routeDefinitionWriter.delete(Mono.just(routeDef.getId()))
                    .then(routeDefinitionWriter.save(Mono.just(routeDefinition)))
                    .then(Mono.fromRunnable(() -> {
                        // 发布路由刷新事件
                        publisher.publishEvent(new RefreshRoutesEvent(this));
                        log.info("Route updated successfully: {}", routeDef.getId());
                    }))
                    .thenReturn(true)
                    .onErrorReturn(false);

        } catch (Exception e) {
            log.error("Failed to update route: {}", routeDef.getId(), e);
            return Mono.just(false);
        }
    }

    /**
     * 删除路由
     */
    public Mono<Boolean> deleteRoute(String routeId) {
        try {
            // 从缓存中移除
            routeCache.remove(routeId);
            routeStatsMap.remove(routeId);

            // 删除路由定义
            return routeDefinitionWriter.delete(Mono.just(routeId))
                    .then(Mono.fromRunnable(() -> {
                        // 发布路由刷新事件
                        publisher.publishEvent(new RefreshRoutesEvent(this));
                        log.info("Route deleted successfully: {}", routeId);
                    }))
                    .thenReturn(true)
                    .onErrorReturn(false);

        } catch (Exception e) {
            log.error("Failed to delete route: {}", routeId, e);
            return Mono.just(false);
        }
    }

    /**
     * 获取所有路由
     */
    public Mono<List<DynamicRouteDefinition>> getAllRoutes() {
        return routeDefinitionLocator.getRouteDefinitions()
                .map(this::convertToDynamicRouteDefinition)
                .collectList();
    }

    /**
     * 根据ID获取路由
     */
    public Mono<DynamicRouteDefinition> getRouteById(String routeId) {
        return routeDefinitionLocator.getRouteDefinitions()
                .filter(route -> routeId.equals(route.getId()))
                .map(this::convertToDynamicRouteDefinition)
                .next();
    }

    /**
     * 启用/禁用路由
     */
    public Mono<Boolean> toggleRoute(String routeId, boolean enabled) {
        return getRouteById(routeId)
                .flatMap(routeDef -> {
                    routeDef.setEnabled(enabled);
                    return updateRoute(routeDef);
                })
                .onErrorReturn(false);
    }

    /**
     * 获取路由统计信息
     */
    public Map<String, RouteStats> getRouteStats() {
        return new HashMap<>(routeStatsMap);
    }

    /**
     * 更新路由统计信息
     */
    public void updateRouteStats(String routeId, boolean success, long responseTime, String errorType) {
        RouteStats stats = routeStatsMap.computeIfAbsent(routeId, k -> {
            RouteStats newStats = new RouteStats();
            newStats.setRouteId(routeId);
            newStats.setStatus("ACTIVE");
            return newStats;
        });

        stats.setTotalRequests(stats.getTotalRequests() + 1);
        stats.setLastAccessTime(LocalDateTime.now());

        if (success) {
            stats.setSuccessRequests(stats.getSuccessRequests() + 1);
        } else {
            stats.setFailedRequests(stats.getFailedRequests() + 1);
            if (errorType != null) {
                stats.getErrorCounts().merge(errorType, 1L, Long::sum);
            }
        }

        // 更新响应时间统计
        if (responseTime > 0) {
            if (stats.getAvgResponseTime() == 0) {
                stats.setAvgResponseTime(responseTime);
            } else {
                double totalTime = stats.getAvgResponseTime() * (stats.getSuccessRequests() - 1) + responseTime;
                stats.setAvgResponseTime(totalTime / stats.getSuccessRequests());
            }
        }
    }

    /**
     * 转换为RouteDefinition
     */
    private RouteDefinition convertToRouteDefinition(DynamicRouteDefinition routeDef) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(routeDef.getId());
        definition.setOrder(routeDef.getOrder());
        try {
            definition.setUri(new URI(routeDef.getUri()));
        } catch (URISyntaxException e) {
            log.error("Invalid URI: {}", routeDef.getUri(), e);
            throw new RuntimeException("Invalid URI: " + routeDef.getUri(), e);
        }

        List<org.springframework.cloud.gateway.handler.predicate.PredicateDefinition> predicates = new ArrayList<>();
        for (String predicate : routeDef.getPredicates()) {
            org.springframework.cloud.gateway.handler.predicate.PredicateDefinition predicateDef = 
                new org.springframework.cloud.gateway.handler.predicate.PredicateDefinition();
            predicateDef.setName(predicate);
            predicates.add(predicateDef);
        }
        definition.setPredicates(predicates);

        List<org.springframework.cloud.gateway.filter.FilterDefinition> filters = new ArrayList<>();
        for (String filter : routeDef.getFilters()) {
            org.springframework.cloud.gateway.filter.FilterDefinition filterDef = 
                new org.springframework.cloud.gateway.filter.FilterDefinition();
            filterDef.setName(filter);
            filters.add(filterDef);
        }
        definition.setFilters(filters);

        if (routeDef.getMetadata() != null) {
            definition.setMetadata(routeDef.getMetadata());
        }

        return definition;
    }

    /**
     * 转换为DynamicRouteDefinition
     */
    private DynamicRouteDefinition convertToDynamicRouteDefinition(RouteDefinition routeDef) {
        DynamicRouteDefinition dynamicDef = new DynamicRouteDefinition();
        dynamicDef.setId(routeDef.getId());
        dynamicDef.setOrder(routeDef.getOrder());
        dynamicDef.setUri(routeDef.getUri().toString());
        dynamicDef.setEnabled(true);

        // 转换断言
        List<String> predicates = new ArrayList<>();
        for (PredicateDefinition predicate : routeDef.getPredicates()) {
            predicates.add(predicate.getName());
        }
        dynamicDef.setPredicates(predicates);

        // 转换过滤器
        List<String> filters = new ArrayList<>();
        for (org.springframework.cloud.gateway.filter.FilterDefinition filter : routeDef.getFilters()) {
            filters.add(filter.getName());
        }
        dynamicDef.setFilters(filters);

        // 转换元数据
        if (routeDef.getMetadata() != null) {
            dynamicDef.setMetadata(routeDef.getMetadata());
        }

        return dynamicDef;
    }

    /**
     * 验证路由定义
     */
    private boolean validateRouteDefinition(RouteDefinition routeDefinition) {
        if (routeDefinition.getId() == null || routeDefinition.getId().trim().isEmpty()) {
            return false;
        }
        if (routeDefinition.getUri() == null) {
            return false;
        }
        if (routeDefinition.getPredicates() == null || routeDefinition.getPredicates().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * 获取路由版本号
     */
    public long getRouteVersion() {
        return routeVersion.get();
    }

    /**
     * 增加路由版本号
     */
    public void incrementRouteVersion() {
        routeVersion.incrementAndGet();
    }
} 