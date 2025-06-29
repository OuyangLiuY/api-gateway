package com.citi.tts.api.gateway.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * 负载均衡过滤器
 * 集成高级负载均衡器到网关路由中
 */
@Slf4j
@Component
public class LoadBalancerFilter extends AbstractGatewayFilterFactory<LoadBalancerFilter.Config> {

    @Autowired
    private AdvancedLoadBalancer advancedLoadBalancer;

    public LoadBalancerFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new LoadBalancerGatewayFilter(config);
    }

    /**
     * 负载均衡网关过滤器
     */
    public class LoadBalancerGatewayFilter implements GatewayFilter, Ordered {

        private final Config config;

        public LoadBalancerGatewayFilter(Config config) {
            this.config = config;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest();
            String serviceName = config.getServiceName();
            String requestId = generateRequestId(request);

            log.debug("Load balancing for service: {} with requestId: {}", serviceName, requestId);

            long startTime = System.currentTimeMillis();

            return advancedLoadBalancer.chooseInstance(serviceName, requestId)
                    .flatMap(serviceInstance -> {
                        if (serviceInstance == null) {
                            log.error("No available instances for service: {}", serviceName);
                            return chain.filter(exchange);
                        }

                        // 构建新的URI
                        URI newUri = buildServiceUri(serviceInstance, request.getURI());
                        
                        // 更新请求URI
                        ServerHttpRequest newRequest = request.mutate()
                                .uri(newUri)
                                .build();

                        // 更新exchange
                        ServerWebExchange newExchange = exchange.mutate()
                                .request(newRequest)
                                .build();

                        // 记录负载均衡信息
                        log.debug("Selected instance: {} for service: {} with URI: {}", 
                                serviceInstance.getInstanceId(), serviceName, newUri);

                        // 继续过滤器链
                        return chain.filter(newExchange)
                                .doFinally(signalType -> {
                                    // 更新响应时间统计
                                    long responseTime = System.currentTimeMillis() - startTime;
                                    advancedLoadBalancer.updateResponseTime(
                                        serviceName, serviceInstance.getInstanceId(), responseTime);
                                    
                                    // 减少连接数
                                    advancedLoadBalancer.decrementConnection(
                                        serviceName, serviceInstance.getInstanceId());
                                    
                                    log.debug("Request completed for service: {} instance: {} in {}ms", 
                                            serviceName, serviceInstance.getInstanceId(), responseTime);
                                });
                    })
                    .onErrorResume(throwable -> {
                        log.error("Load balancing failed for service: {}", serviceName, throwable);
                        return chain.filter(exchange);
                    });
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE - 100; // 在路由之后执行
        }

        /**
         * 构建服务URI
         */
        private URI buildServiceUri(org.springframework.cloud.client.ServiceInstance serviceInstance, URI originalUri) {
            String scheme = serviceInstance.isSecure() ? "https" : "http";
            String host = serviceInstance.getHost();
            int port = serviceInstance.getPort();
            String path = originalUri.getPath();
            String query = originalUri.getQuery();

            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append(scheme).append("://").append(host).append(":").append(port).append(path);
            
            if (query != null) {
                uriBuilder.append("?").append(query);
            }

            return URI.create(uriBuilder.toString());
        }

        /**
         * 生成请求ID
         */
        private String generateRequestId(ServerHttpRequest request) {
            // 从请求头中获取请求ID，如果没有则生成新的
            String requestId = request.getHeaders().getFirst("X-Request-ID");
            if (requestId == null) {
                requestId = UUID.randomUUID().toString();
            }
            return requestId;
        }
    }

    /**
     * 配置类
     */
    @lombok.Data
    public static class Config {
        private String serviceName;
        private AdvancedLoadBalancer.LoadBalancingStrategy strategy = 
            AdvancedLoadBalancer.LoadBalancingStrategy.ROUND_ROBIN;
        private boolean enabled = true;
    }
} 