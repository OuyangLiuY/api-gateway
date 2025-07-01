package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.limiter.QueuedRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 排队限流过滤器
 * 当限流超出时，将请求放入队列等待处理，而不是直接丢弃
 * 支持降级服务兜底机制
 */
@Slf4j
@Component
public class QueuedRateLimitFilter implements GatewayFilter, Ordered {

    @Autowired
    private QueuedRateLimiter queuedRateLimiter;

    // 排队配置
    @Value("${gateway.queued-rate-limit.max-queue-size:1000}")
    private int maxQueueSize;

    @Value("${gateway.queued-rate-limit.max-wait-time-ms:30000}")
    private long maxWaitTimeMs;

    @Value("${gateway.queued-rate-limit.max-concurrency:10}")
    private int maxConcurrency;

    @Value("${gateway.queued-rate-limit.enable-priority:true}")
    private boolean enablePriority;

    @Value("${gateway.queued-rate-limit.enable-fallback:true}")
    private boolean enableFallback;

    @Value("${gateway.queued-rate-limit.fallback-timeout-ms:5000}")
    private long fallbackTimeoutMs;

    // 统计信息
    private final AtomicLong processedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        String path = request.getPath().value();
        String method = request.getMethod().name();
        String clientIp = getClientIp(request);
        String userId = getUserId(request);

        log.debug("Processing queued rate limit for: {} {} from IP: {}, User: {}", 
                method, path, clientIp, userId);

        // 构建限流key
        String rateLimitKey = String.format("%s:%s:%s", clientIp, method, path);
        
        // 获取请求优先级
        int priority = getRequestPriority(request);
        
        // 创建队列配置
        QueuedRateLimiter.QueueConfig queueConfig = new QueuedRateLimiter.QueueConfig(
            maxQueueSize,
            Duration.ofMillis(maxWaitTimeMs),
            maxConcurrency,
            enablePriority,
            enableFallback,
            Duration.ofMillis(fallbackTimeoutMs)
        );

        // 创建降级服务函数
        Function<String, Mono<String>> fallbackService = null;
        if (enableFallback) {
            fallbackService = key -> createFallbackResponse(exchange, key);
        }

        // 执行排队限流
        return queuedRateLimiter.rateLimitWithQueue(
            rateLimitKey,
            () -> chain.filter(exchange).then(Mono.just("success")), // 原始请求
            () -> checkRateLimit(rateLimitKey), // 限流检查
            queueConfig,
            priority,
            fallbackService
        ).doOnSuccess(result -> {
            processedRequests.incrementAndGet();
            log.debug("Request processed successfully for queue key: {}", rateLimitKey);
        }).doOnError(error -> {
            rejectedRequests.incrementAndGet();
            log.warn("Request rejected for queue key: {}, error: {}", rateLimitKey, error.getMessage());
            handleQueueRejection(response, error.getMessage());
        }).onErrorResume(error -> {
            handleQueueRejection(response, error.getMessage());
            return Mono.empty();
        }).then();
    }

    /**
     * 检查限流
     */
    private boolean checkRateLimit(String key) {
        // 这里可以集成具体的限流算法
        // 目前使用简单的计数器，实际项目中应该使用RedisRateLimiter等
        return true; // 暂时返回true，实际应该根据限流算法判断
    }

    /**
     * 获取请求优先级
     */
    private int getRequestPriority(ServerHttpRequest request) {
        // 从请求头获取优先级
        String priorityHeader = request.getHeaders().getFirst("X-Request-Priority");
        if (priorityHeader != null) {
            try {
                return Integer.parseInt(priorityHeader);
            } catch (NumberFormatException e) {
                log.warn("Invalid priority header: {}", priorityHeader);
            }
        }
        
        // 根据路径判断优先级
        String path = request.getPath().value();
        if (path.contains("/api/v1/important")) {
            return 0; // 重要API，最高优先级
        } else if (path.contains("/api/v1/normal")) {
            return 5; // 普通API，中等优先级
        } else {
            return 9; // 其他API，最低优先级
        }
    }

    /**
     * 创建降级响应
     */
    private Mono<String> createFallbackResponse(ServerWebExchange exchange, String key) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 设置响应头
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        
        // 构建降级响应内容
        String fallbackResponse = String.format(
            "{\"code\":503,\"message\":\"Service temporarily unavailable due to high load. Request queued and fallback service activated.\",\"data\":null,\"timestamp\":%d,\"queueKey\":\"%s\"}",
            System.currentTimeMillis(),
            key
        );
        
        // 写入响应体
        DataBuffer buffer = response.bufferFactory().wrap(fallbackResponse.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer))
                .then(Mono.just(fallbackResponse));
    }

    /**
     * 处理队列拒绝
     */
    private void handleQueueRejection(ServerHttpResponse response, String errorMessage) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", "60");
        response.getHeaders().set("X-Queue-Status", "rejected");
        
        String errorResponse = String.format(
            "{\"error\":\"%s\",\"code\":429,\"queueStatus\":\"rejected\",\"retryAfter\":60,\"timestamp\":\"%s\"}",
            errorMessage, java.time.Instant.now()
        );
        
        DataBuffer buffer = response.bufferFactory().wrap(errorResponse.getBytes(StandardCharsets.UTF_8));
        response.writeWith(Mono.just(buffer));
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        
        return Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
    }

    /**
     * 获取用户ID
     */
    private String getUserId(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            // 这里应该解析JWT获取用户ID，简化实现
            return "user_" + authHeader.hashCode();
        }
        return "anonymous";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100; // 在认证过滤器之后执行
    }

    /**
     * 获取统计信息
     */
    public QueuedRateLimitStats getStats() {
        return QueuedRateLimitStats.builder()
                .processedRequests(processedRequests.get())
                .rejectedRequests(rejectedRequests.get())
                .build();
    }

    /**
     * 统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class QueuedRateLimitStats {
        private long processedRequests;
        private long rejectedRequests;
    }
} 