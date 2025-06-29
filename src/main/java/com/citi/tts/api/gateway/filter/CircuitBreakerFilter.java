package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.config.GatewayCircuitBreakerConfig;
import com.citi.tts.api.gateway.services.ApiRoutingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 熔断器过滤器
 * 基于Resilience4j实现熔断功能
 */
@Slf4j
@Component
public class CircuitBreakerFilter implements GatewayFilter, Ordered {

    @Autowired
    private GatewayCircuitBreakerConfig.CircuitBreakerProperties circuitBreakerProperties;

    @Autowired
    @Qualifier("coreApiCircuitBreaker")
    private CircuitBreaker coreApiCircuitBreaker;

    @Autowired
    @Qualifier("normalApiCircuitBreaker")
    private CircuitBreaker normalApiCircuitBreaker;

    @Autowired
    @Qualifier("nonCoreApiCircuitBreaker")
    private CircuitBreaker nonCoreApiCircuitBreaker;

    @Autowired
    @Qualifier("cryptoApiCircuitBreaker")
    private CircuitBreaker cryptoApiCircuitBreaker;

    @Autowired
    private ApiRoutingService apiRoutingService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!circuitBreakerProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        ApiRoutingService.ApiPriority priority = apiRoutingService.getApiPriority(path);

        // 根据API优先级选择对应的熔断器
        CircuitBreaker circuitBreaker = getCircuitBreaker(priority);
        
        log.debug("Using circuit breaker for API: {} with priority: {}", path, priority);

        return chain.filter(exchange)
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(CallNotPermittedException.class, e -> {
                    log.warn("Circuit breaker opened for API: {} with priority: {}", path, priority);
                    return handleCircuitBreakerOpen(exchange, priority);
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Error in circuit breaker filter for API: {}", path, e);
                    return handleCircuitBreakerError(exchange, priority, e);
                });
    }

    /**
     * 根据API优先级获取对应的熔断器
     */
    private CircuitBreaker getCircuitBreaker(ApiRoutingService.ApiPriority priority) {
        return switch (priority) {
            case CORE -> coreApiCircuitBreaker;
            case NORMAL -> normalApiCircuitBreaker;
            case NON_CORE -> nonCoreApiCircuitBreaker;
            case CRYPTO -> cryptoApiCircuitBreaker;
        };
    }

    /**
     * 处理熔断器开启状态
     */
    private Mono<Void> handleCircuitBreakerOpen(ServerWebExchange exchange, ApiRoutingService.ApiPriority priority) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Circuit-Breaker-Status", "OPEN");
        response.getHeaders().set("Retry-After", "30");

        String errorResponse = String.format(
            "{\"error\":\"Service temporarily unavailable due to circuit breaker\",\"code\":503,\"priority\":\"%s\",\"retryAfter\":30}",
            priority.name()
        );

        DataBuffer buffer = response.bufferFactory().wrap(errorResponse.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 处理熔断器错误
     */
    private Mono<Void> handleCircuitBreakerError(ServerWebExchange exchange, ApiRoutingService.ApiPriority priority, Exception e) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Circuit-Breaker-Status", "ERROR");

        String errorResponse = String.format(
            "{\"error\":\"Service error\",\"code\":500,\"priority\":\"%s\",\"message\":\"%s\"}",
            priority.name(), e.getMessage()
        );

        DataBuffer buffer = response.bufferFactory().wrap(errorResponse.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100; // 在限流过滤器之后执行
    }
} 