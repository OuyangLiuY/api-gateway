package com.citi.tts.api.gateway.routes;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态路由配置
 * 支持多种存储方式和配置管理
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "gateway.dynamic-route")
@Data
public class DynamicRouteConfig {

    /**
     * 存储类型：memory, file
     */
    private String storageType = "memory";

    /**
     * 文件配置
     */
    private File file = new File();

    /**
     * 缓存配置
     */
    private Cache cache = new Cache();

    /**
     * 监控配置
     */
    private Monitor monitor = new Monitor();

    @Data
    public static class File {
        private String path = "config/routes.yml";
        private boolean autoReload = true;
        private int reloadIntervalMs = 5000;
    }

    @Data
    public static class Cache {
        private int maxSize = 1000;
        private int expireSeconds = 300;
        private boolean enableStats = true;
    }

    @Data
    public static class Monitor {
        private boolean enableMetrics = true;
        private int statsIntervalMs = 60000;
        private boolean enableHealthCheck = true;
        private int healthCheckIntervalMs = 30000;
    }

    /**
     * 内存路由存储
     */
    @Bean
    public Map<String, RouteDefinition> routeDefinitionMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * 动态路由监听器
     */
    @Bean
    public DynamicRouteChangeListener dynamicRouteChangeListener(
            RouteDefinitionWriter routeDefinitionWriter,
            RouteDefinitionLocator routeDefinitionLocator) {
        return new DynamicRouteChangeListener(routeDefinitionWriter, routeDefinitionLocator);
    }

    /**
     * 动态路由变更监听器
     */
    public static class DynamicRouteChangeListener {
        private final RouteDefinitionWriter routeDefinitionWriter;
        private final RouteDefinitionLocator routeDefinitionLocator;

        public DynamicRouteChangeListener(RouteDefinitionWriter routeDefinitionWriter,
                                        RouteDefinitionLocator routeDefinitionLocator) {
            this.routeDefinitionWriter = routeDefinitionWriter;
            this.routeDefinitionLocator = routeDefinitionLocator;
        }

        /**
         * 监听路由变更
         */
        public void onRouteChange(String routeId, RouteDefinition routeDefinition) {
            log.info("Route change detected: {}", routeId);
            // 这里可以添加路由变更的处理逻辑
        }

        /**
         * 监听路由删除
         */
        public void onRouteDelete(String routeId) {
            log.info("Route delete detected: {}", routeId);
            // 这里可以添加路由删除的处理逻辑
        }
    }
} 