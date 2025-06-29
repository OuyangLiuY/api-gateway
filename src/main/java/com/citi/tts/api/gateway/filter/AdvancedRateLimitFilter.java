package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.config.RateLimitConfig;
import com.citi.tts.api.gateway.limiter.AdvancedRateLimiter;
import com.citi.tts.api.gateway.services.ApiRoutingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强限流过滤器
 * 支持：API权重限流、IP限流、用户限流、URL路径限流
 * 基于系统最高QPS 100优化设计，支持突发流量处理
 */
@Slf4j
@Component
public class AdvancedRateLimitFilter implements GatewayFilter, Ordered {

    @Autowired
    private RateLimitConfig.RateLimitProperties rateLimitProperties;

    @Autowired
    private AdvancedRateLimiter advancedRateLimiter;

    @Autowired
    private ApiRoutingService apiRoutingService;

    // 限流统计计数器
    private final AtomicLong ipLimitCount = new AtomicLong(0);
    private final AtomicLong userLimitCount = new AtomicLong(0);
    private final AtomicLong urlLimitCount = new AtomicLong(0);
    private final AtomicLong apiWeightLimitCount = new AtomicLong(0);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String path = request.getPath().value();
        String method = request.getMethod().name();
        String clientIp = getClientIp(exchange);
        String userId = getUserId(exchange);

        log.debug("Processing rate limit for: {} {} from IP: {}, User: {}", method, path, clientIp, userId);

        // 记录请求开始时间
        long startTime = System.currentTimeMillis();

        // 1. IP限流检查 - 使用滑动窗口支持突发流量
        if (rateLimitProperties.isIpLimitEnabled() && !checkIpRateLimit(exchange, clientIp)) {
            ipLimitCount.incrementAndGet();
            log.warn("IP rate limit exceeded for IP: {}, path: {}", clientIp, path);
            return handleRateLimitExceeded(response, "IP rate limit exceeded", "IP_LIMIT", clientIp);
        }

        // 2. 用户限流检查 - 使用滑动窗口支持突发流量
        if (rateLimitProperties.isUserLimitEnabled() && !checkUserRateLimit(exchange, userId)) {
            userLimitCount.incrementAndGet();
            log.warn("User rate limit exceeded for user: {}, path: {}", userId, path);
            return handleRateLimitExceeded(response, "User rate limit exceeded", "USER_LIMIT", userId);
        }

        // 3. URL路径限流检查 - 使用滑动窗口支持突发流量
        if (rateLimitProperties.isUrlLimitEnabled() && !checkUrlRateLimit(exchange, method, path)) {
            urlLimitCount.incrementAndGet();
            log.warn("URL rate limit exceeded for path: {} {}", method, path);
            return handleRateLimitExceeded(response, "URL rate limit exceeded", "URL_LIMIT", path);
        }

        // 4. API权重限流检查 - 使用增强令牌桶支持突发流量
        if (rateLimitProperties.isApiWeightLimitEnabled() && !checkApiWeightRateLimit(exchange, path)) {
            apiWeightLimitCount.incrementAndGet();
            log.warn("API weight rate limit exceeded for path: {}", path);
            return handleRateLimitExceeded(response, "API weight rate limit exceeded", "API_WEIGHT_LIMIT", path);
        }

        // 记录处理时间
        long processingTime = System.currentTimeMillis() - startTime;
        if (processingTime > 10) { // 超过10ms记录警告
            log.warn("Rate limit processing took {}ms for path: {}", processingTime, path);
        }

        // 所有限流检查通过，继续处理请求
        return chain.filter(exchange);
    }

    /**
     * IP限流检查 - 使用滑动窗口支持突发流量
     * 配置：30 QPS，50 burst，1秒窗口
     */
    private boolean checkIpRateLimit(ServerWebExchange exchange, String ip) {
        if (ip == null) {
            return true; // IP为空，跳过限流
        }
        
        String key = "ip:" + ip;
        AdvancedRateLimiter.SlidingWindowRateLimiter limiter = advancedRateLimiter.getSlidingWindowLimiter(
            key, 1, rateLimitProperties.getIpQps(), rateLimitProperties.getIpBurst());
        
        boolean allowed = limiter.tryAcquire();
        
        if (!allowed) {
            log.debug("IP rate limit check failed - IP: {}, current: {}, limit: {} + burst: {}", 
                    ip, limiter.getCurrentCount(), rateLimitProperties.getIpQps(), rateLimitProperties.getIpBurst());
        }
        
        return allowed;
    }

    /**
     * 用户限流检查 - 使用滑动窗口支持突发流量
     * 配置：20 QPS，35 burst，1秒窗口
     */
    private boolean checkUserRateLimit(ServerWebExchange exchange, String userId) {
        if (userId == null) {
            return true; // 没有用户ID，跳过用户限流
        }
        
        String key = "user:" + userId;
        AdvancedRateLimiter.SlidingWindowRateLimiter limiter = advancedRateLimiter.getSlidingWindowLimiter(
            key, 1, rateLimitProperties.getUserQps(), rateLimitProperties.getUserBurst());
        
        boolean allowed = limiter.tryAcquire();
        
        if (!allowed) {
            log.debug("User rate limit check failed - User: {}, current: {}, limit: {} + burst: {}", 
                    userId, limiter.getCurrentCount(), rateLimitProperties.getUserQps(), rateLimitProperties.getUserBurst());
        }
        
        return allowed;
    }

    /**
     * URL路径限流检查 - 使用滑动窗口支持突发流量
     * 配置：40 QPS，60 burst，1秒窗口
     */
    private boolean checkUrlRateLimit(ServerWebExchange exchange, String method, String path) {
        String key = "url:" + method + ":" + path;
        AdvancedRateLimiter.SlidingWindowRateLimiter limiter = advancedRateLimiter.getSlidingWindowLimiter(
            key, 1, rateLimitProperties.getUrlQps(), rateLimitProperties.getUrlBurst());
        
        boolean allowed = limiter.tryAcquire();
        
        if (!allowed) {
            log.debug("URL rate limit check failed - Path: {} {}, current: {}, limit: {} + burst: {}", 
                    method, path, limiter.getCurrentCount(), rateLimitProperties.getUrlQps(), rateLimitProperties.getUrlBurst());
        }
        
        return allowed;
    }

    /**
     * API权重限流检查 - 使用增强令牌桶支持突发流量
     * 基于令牌桶算法，支持突发流量处理
     */
    private boolean checkApiWeightRateLimit(ServerWebExchange exchange, String path) {
        ApiRoutingService.ApiPriority priority = apiRoutingService.getApiPriority(path);
        String priorityKey = priority.name();
        
        RateLimitConfig.ApiWeightConfig config = rateLimitProperties.getApiWeights().get(priorityKey);
        if (config == null) {
            log.warn("No API weight config found for priority: {}, using default", priorityKey);
            config = new RateLimitConfig.ApiWeightConfig(25, 35, 0.4);
        }
        
        String key = "api_weight:" + priorityKey;
        AdvancedRateLimiter.TokenBucketRateLimiter limiter = advancedRateLimiter.getTokenBucketLimiter(
            key, config.getBurst(), config.getQps(), 1000); // 使用burst作为容量，QPS作为补充速率
        
        boolean allowed = limiter.tryAcquire();
        
        if (!allowed) {
            log.debug("API weight rate limit check failed - Priority: {}, path: {}, available tokens: {}/{}", 
                    priority, path, limiter.getAvailableTokens(), limiter.getCapacity());
        }
        
        return allowed;
    }

    /**
     * 获取客户端IP
     * 支持代理环境下的真实IP获取
     */
    private String getClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 检查X-Forwarded-For头
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 检查X-Real-IP头
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        
        // 检查CF-Connecting-IP头（Cloudflare）
        String cfConnectingIp = request.getHeaders().getFirst("CF-Connecting-IP");
        if (StringUtils.hasText(cfConnectingIp)) {
            return cfConnectingIp;
        }
        
        // 使用远程地址
        return Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
    }

    /**
     * 获取用户ID
     * 支持多种用户识别方式
     */
    private String getUserId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 从Authorization头中提取用户ID（JWT token）
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return extractUserIdFromToken(authHeader.substring(7));
        }
        
        // 从自定义头中获取用户ID
        String userId = request.getHeaders().getFirst("X-User-ID");
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        
        // 从查询参数中获取用户ID（不推荐，仅作为备选）
        String queryUserId = request.getQueryParams().getFirst("userId");
        if (StringUtils.hasText(queryUserId)) {
            return queryUserId;
        }
        
        return null;
    }

    /**
     * 从JWT token中提取用户ID（简化实现）
     * 实际项目中应使用proper JWT解析库
     */
    private String extractUserIdFromToken(String token) {
        try {
            // 这里应该proper解析JWT token
            // 简化实现，实际项目中需要使用JWT库
            return "user_" + token.hashCode();
        } catch (Exception e) {
            log.warn("Failed to extract user ID from token", e);
            return null;
        }
    }

    /**
     * 处理限流超限响应
     * 返回详细的错误信息和重试建议
     */
    private Mono<Void> handleRateLimitExceeded(ServerHttpResponse response, String message, String limitType, String identifier) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-RateLimit-Type", limitType);
        response.getHeaders().set("Retry-After", "60");
        response.getHeaders().set("X-RateLimit-Identifier", identifier);
        
        String errorResponse = String.format(
            "{\"error\":\"%s\",\"code\":429,\"limitType\":\"%s\",\"identifier\":\"%s\",\"retryAfter\":60,\"timestamp\":\"%s\"}",
            message, limitType, identifier, java.time.Instant.now()
        );
        
        DataBuffer buffer = response.bufferFactory().wrap(errorResponse.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 获取限流统计信息
     */
    public Map<String, Long> getRateLimitStatistics() {
        return Map.of(
            "ipLimitCount", ipLimitCount.get(),
            "userLimitCount", userLimitCount.get(),
            "urlLimitCount", urlLimitCount.get(),
            "apiWeightLimitCount", apiWeightLimitCount.get()
        );
    }

    /**
     * 获取增强限流器状态
     */
    public Map<String, Object> getAdvancedRateLimitersStatus() {
        return advancedRateLimiter.getAllLimitersStatus();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50; // 在认证过滤器之后，业务过滤器之前
    }
} 