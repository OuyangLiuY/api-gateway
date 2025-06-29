package com.citi.tts.api.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${gateway.webclient.max-connections:200}")
    private int maxConnections;

    @Value("${gateway.webclient.acquire-timeout:3000}")
    private int acquireTimeout;

    @Value("${gateway.webclient.connect-timeout:2000}")
    private int connectTimeout;

    @Value("${gateway.webclient.read-timeout:5000}")
    private int readTimeout;

    @Value("${gateway.webclient.write-timeout:3000}")
    private int writeTimeout;

    @Value("${gateway.webclient.keep-alive:true}")
    private boolean keepAlive;

    @Value("${gateway.webclient.max-idle-time:30000}")
    private int maxIdleTime;

    /**
     * 核心服务WebClient
     * 2核CPU + QPS 100场景优化配置
     * 核心服务占比约40%，即40 QPS
     * 连接数 = QPS * 平均响应时间(ms) / 1000 * 连接复用系数(0.3)
     * 40 * 200ms / 1000 * 0.3 = 24连接，取整为25
     */
    @Bean("coreServiceWebClient")
    public WebClient coreServiceWebClient() {
        return createOptimizedWebClient("core-service", 25, 8);
    }

    /**
     * 重要服务WebClient
     * 重要服务占比约30%，即30 QPS
     * 30 * 150ms / 1000 * 0.3 = 13.5连接，取整为15
     */
    @Bean("importantServiceWebClient")
    public WebClient importantServiceWebClient() {
        return createOptimizedWebClient("important-service", 15, 5);
    }

    /**
     * 普通服务WebClient
     * 普通服务占比约20%，即20 QPS
     * 20 * 100ms / 1000 * 0.3 = 6连接，取整为8
     */
    @Bean("normalServiceWebClient")
    public WebClient normalServiceWebClient() {
        return createOptimizedWebClient("normal-service", 8, 3);
    }

    /**
     * 非核心服务WebClient
     * 非核心服务占比约10%，即10 QPS
     * 10 * 80ms / 1000 * 0.3 = 2.4连接，取整为4
     */
    @Bean("nonCoreServiceWebClient")
    public WebClient nonCoreServiceWebClient() {
        return createOptimizedWebClient("non-core-service", 4, 2);
    }

    /**
     * 默认WebClient
     * 兜底配置，用于未分类的服务
     */
    @Bean
    public WebClient defaultWebClient() {
        return createOptimizedWebClient("default", 6, 2);
    }

    /**
     * 创建优化的WebClient
     * 
     * @param name WebClient名称
     * @param maxConnections 最大连接数
     * @param maxIdleConnections 最大空闲连接数
     */
    private WebClient createOptimizedWebClient(String name, int maxConnections, int maxIdleConnections) {
        // 根据2核CPU优化连接池配置
        ConnectionProvider connectionProvider = ConnectionProvider.builder(name)
                .maxConnections(maxConnections)
                .maxIdleTime(Duration.ofMillis(maxIdleTime))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofMillis(acquireTimeout))
                .pendingAcquireMaxCount(maxConnections * 2) // 限制等待队列大小
                .metrics(true)
                .build();

        // 根据服务类型调整超时时间
        int serviceConnectTimeout = getServiceConnectTimeout(name);
        int serviceReadTimeout = getServiceReadTimeout(name);
        int serviceWriteTimeout = getServiceWriteTimeout(name);

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, serviceConnectTimeout)
                .responseTimeout(Duration.ofMillis(serviceReadTimeout))
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(serviceReadTimeout, TimeUnit.MILLISECONDS));
                    conn.addHandlerLast(new WriteTimeoutHandler(serviceWriteTimeout, TimeUnit.MILLISECONDS));
                })
                .keepAlive(keepAlive)
                .compress(true)
                .wiretap(false);

        log.info("Created optimized WebClient: {} - maxConnections: {}, maxIdleConnections: {}, " +
                "connectTimeout: {}ms, readTimeout: {}ms, writeTimeout: {}ms", 
                name, maxConnections, maxIdleConnections, serviceConnectTimeout, serviceReadTimeout, serviceWriteTimeout);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 根据服务类型获取连接超时时间
     */
    private int getServiceConnectTimeout(String serviceName) {
        return switch (serviceName) {
            case "core-service" -> 1500;      // 核心服务：快速连接
            case "important-service" -> 2000; // 重要服务：标准连接
            case "normal-service" -> 2500;    // 普通服务：宽松连接
            case "non-core-service" -> 3000;  // 非核心服务：最宽松连接
            default -> connectTimeout;
        };
    }

    /**
     * 根据服务类型获取读取超时时间
     */
    private int getServiceReadTimeout(String serviceName) {
        return switch (serviceName) {
            case "core-service" -> 3000;      // 核心服务：快速响应
            case "important-service" -> 4000; // 重要服务：标准响应
            case "normal-service" -> 5000;    // 普通服务：宽松响应
            case "non-core-service" -> 8000;  // 非核心服务：最宽松响应
            default -> readTimeout;
        };
    }

    /**
     * 根据服务类型获取写入超时时间
     */
    private int getServiceWriteTimeout(String serviceName) {
        return switch (serviceName) {
            case "core-service" -> 2000;      // 核心服务：快速写入
            case "important-service" -> 2500; // 重要服务：标准写入
            case "normal-service" -> 3000;    // 普通服务：宽松写入
            case "non-core-service" -> 4000;  // 非核心服务：最宽松写入
            default -> writeTimeout;
        };
    }
} 