package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.config.RateLimitConfig;
import com.citi.tts.api.gateway.limiter.AdvancedRateLimiter;
import com.citi.tts.api.gateway.services.ApiRoutingService;
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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可配置限流顺序的过滤器
 * 支持不同的限流检查顺序，适应不同的业务场景
 */
@Slf4j
@Component
public class ConfigurableRateLimitFilter implements GatewayFilter, Ordered {

    @Autowired
    private RateLimitConfig.RateLimitProperties rateLimitProperties;

    @Autowired
    private AdvancedRateLimiter advancedRateLimiter;

    @Autowired
    private ApiRoutingService apiRoutingService;

    // 限流顺序配置
    @Value("${rate.limit.order:IP,USER,URL,API_WEIGHT}")
    private String rateLimitOrder;

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

        log.debug("Processing configurable rate limit for: {} {} from IP: {}, User: {}", 
                method, path, clientIp, userId);

        // 记录请求开始时间
        long startTime = System.currentTimeMillis();

        // 解析限流顺序
        String[] orderArray = rateLimitOrder.split(",");
        
        // 按配置顺序执行限流检查
        for (String limitType : orderArray) {
            String trimmedType = limitType.trim().toUpperCase();
            
            switch (trimmedType) {
                case "IP":
                    if (rateLimitProperties.isIpLimitEnabled() && !checkIpRateLimit(exchange, clientIp)) {
                        ipLimitCount.incrementAndGet();
                        log.warn("IP rate limit exceeded for IP: {}, path: {}", clientIp, path);
                        return handleRateLimitExceeded(response, "IP rate limit exceeded", "IP_LIMIT", clientIp);
                    }
                    break;
                    
                case "USER":
                    if (rateLimitProperties.isUserLimitEnabled() && !checkUserRateLimit(exchange, userId)) {
                        userLimitCount.incrementAndGet();
                        log.warn("User rate limit exceeded for user: {}, path: {}", userId, path);
                        return handleRateLimitExceeded(response, "User rate limit exceeded", "USER_LIMIT", userId);
                    }
                    break;
                    
                case "URL":
                    if (rateLimitProperties.isUrlLimitEnabled() && !checkUrlRateLimit(exchange, method, path)) {
                        urlLimitCount.incrementAndGet();
                        log.warn("URL rate limit exceeded for path: {} {}", method, path);
                        return handleRateLimitExceeded(response, "URL rate limit exceeded", "URL_LIMIT", path);
                    }
                    break;
                    
                case "API_WEIGHT":
                    if (rateLimitProperties.isApiWeightLimitEnabled() && !checkApiWeightRateLimit(exchange, path)) {
                        apiWeightLimitCount.incrementAndGet();
                        log.warn("API weight rate limit exceeded for path: {}", path);
                        return handleRateLimitExceeded(response, "API weight rate limit exceeded", "API_WEIGHT_LIMIT", path);
                    }
                    break;
                    
                default:
                    log.warn("Unknown rate limit type: {}", trimmedType);
                    break;
            }
        }

        // 记录处理时间
        long processingTime = System.currentTimeMillis() - startTime;
        if (processingTime > 10) {
            log.warn("Rate limit processing took {}ms for path: {}", processingTime, path);
        }

        // 所有限流检查通过，继续处理请求
        return chain.filter(exchange);
    }

    /**
     * IP限流检查
     */
    private boolean checkIpRateLimit(ServerWebExchange exchange, String ip) {
        if (ip == null) {
            return true;
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
     * 用户限流检查
     */
    private boolean checkUserRateLimit(ServerWebExchange exchange, String userId) {
        if (userId == null) {
            return true;
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
     * URL路径限流检查
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
     * API权重限流检查
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
            key, config.getBurst(), config.getQps(), 1000);
        
        boolean allowed = limiter.tryAcquire();
        
        if (!allowed) {
            log.debug("API weight rate limit check failed - Priority: {}, path: {}, available tokens: {}/{}", 
                    priority, path, limiter.getAvailableTokens(), limiter.getCapacity());
        }
        
        return allowed;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 优先从X-Forwarded-For获取
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 从X-Real-IP获取
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 从远程地址获取
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    /**
     * 获取用户ID
     */
    private String getUserId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 从请求头获取用户ID
        String userId = request.getHeaders().getFirst("X-User-ID");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        
        // 从Authorization头中提取用户ID
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return extractUserIdFromToken(authorization.substring(7));
        }
        
        return "anonymous";
    }

    /**
     * 从JWT token中提取用户ID
     */
    private String extractUserIdFromToken(String token) {
        // 简化实现，实际项目中需要JWT解析
        if (token != null && !token.isEmpty()) {
            return "jwt-user-" + token.hashCode();
        }
        return "anonymous";
    }

    /**
     * 处理限流超限响应
     */
    private Mono<Void> handleRateLimitExceeded(ServerHttpResponse response, String message, String limitType, String identifier) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String responseBody = String.format(
            "{\"error\":\"Rate limit exceeded\",\"message\":\"%s\",\"limitType\":\"%s\",\"identifier\":\"%s\",\"timestamp\":%d}",
            message, limitType, identifier, System.currentTimeMillis()
        );
        
        DataBuffer buffer = response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
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
     * 获取当前限流顺序
     */
    public String getCurrentRateLimitOrder() {
        return rateLimitOrder;
    }

    /**
     * 设置限流顺序
     */
    public void setRateLimitOrder(String newOrder) {
        this.rateLimitOrder = newOrder;
        log.info("Rate limit order updated to: {}", newOrder);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
} 