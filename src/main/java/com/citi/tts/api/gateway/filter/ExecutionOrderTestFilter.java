package com.citi.tts.api.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 过滤器执行顺序测试过滤器
 * 用于验证过滤器在请求和响应阶段的执行顺序
 */
@Slf4j
@Component
public class ExecutionOrderTestFilter extends AbstractGatewayFilterFactory<ExecutionOrderTestFilter.Config> {

    public ExecutionOrderTestFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                String filterName = config.getFilterName();
                String requestId = exchange.getRequest().getId();
                String path = exchange.getRequest().getPath().value();
                
                // Pre 阶段：请求处理前
                log.info("🔵 [{}] {} - PRE 阶段开始 - Path: {}, RequestId: {}", 
                        filterName, System.currentTimeMillis(), path, requestId);
                
                // 在请求头中添加过滤器执行标记
                exchange.getRequest().mutate()
                        .header("X-Filter-Execution", 
                                exchange.getRequest().getHeaders().getFirst("X-Filter-Execution") + 
                                (exchange.getRequest().getHeaders().getFirst("X-Filter-Execution") != null ? "," : "") + 
                                filterName + "-PRE")
                        .build();
                
                // 继续执行过滤器链
                return chain.filter(exchange)
                        .then(Mono.fromRunnable(() -> {
                            // Post 阶段：响应处理后
                            log.info("🔴 [{}] {} - POST 阶段开始 - Path: {}, RequestId: {}, Status: {}", 
                                    filterName, System.currentTimeMillis(), path, requestId, 
                                    exchange.getResponse().getStatusCode());
                            
                            // 在响应头中添加过滤器执行标记
                            exchange.getResponse().getHeaders().add("X-Filter-Execution-Post", 
                                    filterName + "-POST");
                        }));
            }
        };
    }

    /**
     * 配置类
     */
    public static class Config {
        private String filterName;

        public Config() {}

        public Config(String filterName) {
            this.filterName = filterName;
        }

        public String getFilterName() { return filterName; }
        public void setFilterName(String filterName) { this.filterName = filterName; }
    }

    @Override
    public String name() {
        return "ExecutionOrderTest";
    }
} 