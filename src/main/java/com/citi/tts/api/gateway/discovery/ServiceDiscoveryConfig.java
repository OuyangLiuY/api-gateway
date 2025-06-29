package com.citi.tts.api.gateway.discovery;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 服务发现配置
 * 支持多种服务注册中心的配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.cloud.discovery")
public class ServiceDiscoveryConfig {

    /**
     * 是否启用服务发现
     */
    private boolean enabled = false;

    /**
     * 注册中心类型
     */
    private String type = "simple";

    /**
     * 服务注册配置
     */
    private RegistrationConfig registration = new RegistrationConfig();

    /**
     * 服务发现配置
     */
    private DiscoveryConfig discovery = new DiscoveryConfig();

    /**
     * 健康检查配置
     */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    /**
     * 服务注册配置
     */
    @Data
    public static class RegistrationConfig {
        /**
         * 是否启用服务注册
         */
        private boolean enabled = true;

        /**
         * 注册间隔（毫秒）
         */
        private long interval = 30000;

        /**
         * 注册超时时间（毫秒）
         */
        private long timeout = 5000;

        /**
         * 服务元数据
         */
        private Map<String, String> metadata;

        /**
         * 服务标签
         */
        private Map<String, String> tags;
    }

    /**
     * 服务发现配置
     */
    @Data
    public static class DiscoveryConfig {
        /**
         * 是否启用服务发现
         */
        private boolean enabled = true;

        /**
         * 发现间隔（毫秒）
         */
        private long interval = 30000;

        /**
         * 缓存过期时间（毫秒）
         */
        private long cacheExpiration = 60000;

        /**
         * 是否启用缓存
         */
        private boolean cacheEnabled = true;
    }

    /**
     * 健康检查配置
     */
    @Data
    public static class HealthCheckConfig {
        /**
         * 是否启用健康检查
         */
        private boolean enabled = true;

        /**
         * 健康检查间隔（毫秒）
         */
        private long interval = 30000;

        /**
         * 健康检查超时时间（毫秒）
         */
        private long timeout = 5000;

        /**
         * 健康检查路径
         */
        private String path = "/actuator/health";

        /**
         * 失败阈值
         */
        private int failureThreshold = 3;

        /**
         * 成功阈值
         */
        private int successThreshold = 1;
    }

    /**
     * Eureka配置
     */
    @Data
    public static class EurekaConfig {
        /**
         * Eureka服务器地址
         */
        private String serverUrl = "http://localhost:8761";

        /**
         * 应用名称
         */
        private String appName;

        /**
         * 实例ID
         */
        private String instanceId;

        /**
         * 是否启用Eureka客户端
         */
        private boolean enabled = false;

        /**
         * 心跳间隔（秒）
         */
        private int heartbeatInterval = 30;

        /**
         * 续约间隔（秒）
         */
        private int renewalInterval = 30;

        /**
         * 租约到期时间（秒）
         */
        private int leaseExpirationDuration = 90;
    }

    /**
     * Consul配置
     */
    @Data
    public static class ConsulConfig {
        /**
         * Consul服务器地址
         */
        private String host = "localhost";

        /**
         * Consul服务器端口
         */
        private int port = 8500;

        /**
         * 是否启用Consul
         */
        private boolean enabled = false;

        /**
         * 服务名称
         */
        private String serviceName;

        /**
         * 服务ID
         */
        private String serviceId;

        /**
         * 健康检查路径
         */
        private String healthCheckPath = "/actuator/health";

        /**
         * 健康检查间隔（秒）
         */
        private int healthCheckInterval = 10;

        /**
         * 健康检查超时时间（秒）
         */
        private int healthCheckTimeout = 5;

        /**
         * 服务标签
         */
        private String[] tags = {};
    }

    /**
     * Nacos配置
     */
    @Data
    public static class NacosConfig {
        /**
         * Nacos服务器地址
         */
        private String serverAddr = "localhost:8848";

        /**
         * 命名空间
         */
        private String namespace = "";

        /**
         * 分组
         */
        private String group = "DEFAULT_GROUP";

        /**
         * 是否启用Nacos
         */
        private boolean enabled = false;

        /**
         * 服务名称
         */
        private String serviceName;

        /**
         * 集群名称
         */
        private String clusterName = "DEFAULT";

        /**
         * 权重
         */
        private double weight = 1.0;

        /**
         * 是否启用健康检查
         */
        private boolean healthCheckEnabled = true;

        /**
         * 健康检查路径
         */
        private String healthCheckPath = "/actuator/health";

        /**
         * 健康检查间隔（毫秒）
         */
        private int healthCheckInterval = 5000;

        /**
         * 健康检查超时时间（毫秒）
         */
        private int healthCheckTimeout = 3000;
    }

    /**
     * Zookeeper配置
     */
    @Data
    public static class ZookeeperConfig {
        /**
         * Zookeeper连接地址
         */
        private String connectString = "localhost:2181";

        /**
         * 是否启用Zookeeper
         */
        private boolean enabled = false;

        /**
         * 服务名称
         */
        private String serviceName;

        /**
         * 根路径
         */
        private String rootPath = "/services";

        /**
         * 会话超时时间（毫秒）
         */
        private int sessionTimeout = 60000;

        /**
         * 连接超时时间（毫秒）
         */
        private int connectionTimeout = 15000;

        /**
         * 重试策略
         */
        private RetryConfig retry = new RetryConfig();

        @Data
        public static class RetryConfig {
            /**
             * 最大重试次数
             */
            private int maxRetries = 3;

            /**
             * 重试间隔（毫秒）
             */
            private long retryInterval = 1000;

            /**
             * 指数退避
             */
            private boolean exponentialBackoff = true;
        }
    }
} 