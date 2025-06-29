package com.citi.tts.api.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 服务降级配置
 * 用于配置不同服务级别的降级策略
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "service.degradation")
public class ServiceDegradationConfig {

    /**
     * 缓存配置
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * 降级策略配置
     */
    private StrategyConfig strategy = new StrategyConfig();

    @Data
    public static class CacheConfig {
        /**
         * Redis缓存过期时间（秒）
         */
        private int redisTtl = 3600;
        
        /**
         * 本地缓存过期时间（秒）
         */
        private int localTtl = 1800;
        
        /**
         * 本地缓存最大大小
         */
        private int localMaxSize = 10000;
    }

    @Data
    public static class StrategyConfig {
        /**
         * 核心服务降级配置
         */
        private ServiceLevelConfig core = new ServiceLevelConfig();
        
        /**
         * 重要服务降级配置
         */
        private ServiceLevelConfig important = new ServiceLevelConfig();
        
        /**
         * 普通服务降级配置
         */
        private ServiceLevelConfig normal = new ServiceLevelConfig();
        
        /**
         * 非核心服务降级配置
         */
        private ServiceLevelConfig nonCore = new ServiceLevelConfig();
    }

    @Data
    public static class ServiceLevelConfig {
        /**
         * 启用备用服务
         */
        private boolean backupServiceEnabled = false;
        
        /**
         * 启用本地计算
         */
        private boolean localComputationEnabled = false;
        
        /**
         * 启用异步处理
         */
        private boolean asyncProcessingEnabled = false;
        
        /**
         * 降级超时时间（毫秒）
         */
        private int timeout = 3000;
    }

    /**
     * 根据服务级别获取配置
     */
    public ServiceLevelConfig getConfigByLevel(String level) {
        switch (level.toUpperCase()) {
            case "CORE":
                return strategy.getCore();
            case "IMPORTANT":
                return strategy.getImportant();
            case "NORMAL":
                return strategy.getNormal();
            case "NON_CORE":
                return strategy.getNonCore();
            default:
                return strategy.getNormal(); // 默认返回普通配置
        }
    }

    /**
     * 检查是否启用备用服务
     */
    public boolean isBackupServiceEnabled(String level) {
        return getConfigByLevel(level).isBackupServiceEnabled();
    }

    /**
     * 检查是否启用本地计算
     */
    public boolean isLocalComputationEnabled(String level) {
        return getConfigByLevel(level).isLocalComputationEnabled();
    }

    /**
     * 检查是否启用异步处理
     */
    public boolean isAsyncProcessingEnabled(String level) {
        return getConfigByLevel(level).isAsyncProcessingEnabled();
    }

    /**
     * 获取超时时间
     */
    public int getTimeout(String level) {
        return getConfigByLevel(level).getTimeout();
    }
} 