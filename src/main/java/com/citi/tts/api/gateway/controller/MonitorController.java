package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.filter.AdvancedRateLimitFilter;
import com.citi.tts.api.gateway.services.ApiRoutingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控控制器
 * 提供限流和熔断状态的监控接口
 * 基于系统最高QPS 100优化设计
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway/monitor")
public class MonitorController {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Autowired
    private ApiRoutingService apiRoutingService;

    @Autowired
    private AdvancedRateLimitFilter advancedRateLimitFilter;

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

    /**
     * 获取熔断器状态
     */
    @GetMapping("/circuit-breakers")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 核心API熔断器状态
        status.put("coreApi", getCircuitBreakerInfo(coreApiCircuitBreaker));
        status.put("normalApi", getCircuitBreakerInfo(normalApiCircuitBreaker));
        status.put("nonCoreApi", getCircuitBreakerInfo(nonCoreApiCircuitBreaker));
        status.put("cryptoApi", getCircuitBreakerInfo(cryptoApiCircuitBreaker));
        
        // 熔断器配置信息
        status.put("config", Map.of(
            "coreApi", Map.of(
                "slidingWindowSize", 10,
                "failureRateThreshold", 50.0,
                "slowCallDurationThreshold", "2s",
                "waitDurationInOpenState", "5s"
            ),
            "normalApi", Map.of(
                "slidingWindowSize", 20,
                "failureRateThreshold", 30.0,
                "slowCallDurationThreshold", "5s",
                "waitDurationInOpenState", "10s"
            ),
            "nonCoreApi", Map.of(
                "slidingWindowSize", 30,
                "failureRateThreshold", 20.0,
                "slowCallDurationThreshold", "10s",
                "waitDurationInOpenState", "15s"
            ),
            "cryptoApi", Map.of(
                "slidingWindowSize", 15,
                "failureRateThreshold", 40.0,
                "slowCallDurationThreshold", "3s",
                "waitDurationInOpenState", "8s"
            )
        ));
        
        return ResponseEntity.ok(status);
    }

    /**
     * 获取限流器状态
     */
    @GetMapping("/rate-limiters")
    public ResponseEntity<Map<String, Object>> getRateLimiterStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 获取所有限流器状态
        rateLimiterRegistry.getAllRateLimiters().forEach(rateLimiter -> {
            Map<String, Object> limiterInfo = new HashMap<>();
            limiterInfo.put("name", rateLimiter.getName());
            limiterInfo.put("availablePermissions", rateLimiter.getMetrics().getAvailablePermissions());
            limiterInfo.put("numberOfWaitingThreads", rateLimiter.getMetrics().getNumberOfWaitingThreads());
            status.put(rateLimiter.getName(), limiterInfo);
        });
        
        return ResponseEntity.ok(status);
    }

    /**
     * 获取限流统计信息
     */
    @GetMapping("/rate-limit-statistics")
    public ResponseEntity<Map<String, Object>> getRateLimitStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        // 限流触发统计
        statistics.put("triggerCounts", advancedRateLimitFilter.getRateLimitStatistics());
        
        // API权重限流器状态
        statistics.put("apiWeightLimiters", advancedRateLimitFilter.getApiWeightRateLimitersStatus());
        
        // 限流配置信息
        statistics.put("config", Map.of(
            "ipLimit", Map.of("qps", 30, "burst", 50, "description", "防止单个IP攻击"),
            "userLimit", Map.of("qps", 20, "burst", 35, "description", "防止单个用户过度使用"),
            "urlLimit", Map.of("qps", 40, "burst", 60, "description", "防止热点API被过度调用"),
            "apiWeights", Map.of(
                "CORE", Map.of("qps", 60, "burst", 80, "weight", 1.0, "description", "核心API：支付、转账等"),
                "NORMAL", Map.of("qps", 25, "burst", 35, "weight", 0.4, "description", "普通API：一般业务查询"),
                "NON_CORE", Map.of("qps", 10, "burst", 15, "weight", 0.2, "description", "非核心API：统计、报表等"),
                "CRYPTO", Map.of("qps", 15, "burst", 20, "weight", 0.3, "description", "加解密API：特殊处理")
            )
        ));
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取API统计信息
     */
    @GetMapping("/api-statistics")
    public ResponseEntity<ApiRoutingService.ApiStatistics> getApiStatistics() {
        return ResponseEntity.ok(apiRoutingService.getApiStatistics());
    }

    /**
     * 获取线程池状态
     */
    @GetMapping("/thread-pool-status")
    public ResponseEntity<ApiRoutingService.ThreadPoolStatus> getThreadPoolStatus() {
        return ResponseEntity.ok(apiRoutingService.getThreadPoolStatus());
    }

    /**
     * 获取系统健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // 熔断器健康状态
        Map<String, Object> circuitBreakerHealth = new HashMap<>();
        circuitBreakerHealth.put("coreApi", Map.of(
            "state", coreApiCircuitBreaker.getState(),
            "failureRate", coreApiCircuitBreaker.getMetrics().getFailureRate(),
            "healthy", coreApiCircuitBreaker.getState() == CircuitBreaker.State.CLOSED
        ));
        circuitBreakerHealth.put("normalApi", Map.of(
            "state", normalApiCircuitBreaker.getState(),
            "failureRate", normalApiCircuitBreaker.getMetrics().getFailureRate(),
            "healthy", normalApiCircuitBreaker.getState() == CircuitBreaker.State.CLOSED
        ));
        circuitBreakerHealth.put("nonCoreApi", Map.of(
            "state", nonCoreApiCircuitBreaker.getState(),
            "failureRate", nonCoreApiCircuitBreaker.getMetrics().getFailureRate(),
            "healthy", nonCoreApiCircuitBreaker.getState() == CircuitBreaker.State.CLOSED
        ));
        circuitBreakerHealth.put("cryptoApi", Map.of(
            "state", cryptoApiCircuitBreaker.getState(),
            "failureRate", cryptoApiCircuitBreaker.getMetrics().getFailureRate(),
            "healthy", cryptoApiCircuitBreaker.getState() == CircuitBreaker.State.CLOSED
        ));
        health.put("circuitBreakers", circuitBreakerHealth);
        
        // API统计
        health.put("apiStatistics", apiRoutingService.getApiStatistics());
        
        // 线程池状态
        health.put("threadPoolStatus", apiRoutingService.getThreadPoolStatus());
        
        // 限流统计
        health.put("rateLimitStatistics", advancedRateLimitFilter.getRateLimitStatistics());
        
        // 系统整体健康状态
        boolean overallHealthy = circuitBreakerHealth.values().stream()
                .allMatch(breaker -> (Boolean) ((Map<?, ?>) breaker).get("healthy"));
        health.put("overallStatus", overallHealthy ? "HEALTHY" : "DEGRADED");
        health.put("timestamp", java.time.Instant.now());
        
        return ResponseEntity.ok(health);
    }

    /**
     * 获取系统配置概览
     */
    @GetMapping("/config-overview")
    public ResponseEntity<Map<String, Object>> getConfigOverview() {
        Map<String, Object> overview = new HashMap<>();
        
        // 系统限制
        overview.put("systemLimits", Map.of(
            "maxQps", 100,
            "description", "系统最高QPS限制"
        ));
        
        // 限流策略
        overview.put("rateLimitStrategy", Map.of(
            "layers", 4,
            "description", "四层限流防护：IP、用户、URL、API权重",
            "totalQps", "60+25+10+15=110 (允许轻微超载)"
        ));
        
        // API优先级
        overview.put("apiPriorities", Map.of(
            "CORE", Map.of("qps", 60, "percentage", "60%", "description", "核心业务：支付、转账"),
            "NORMAL", Map.of("qps", 25, "percentage", "25%", "description", "普通业务：查询、通知"),
            "NON_CORE", Map.of("qps", 10, "percentage", "10%", "description", "非核心业务：统计、报表"),
            "CRYPTO", Map.of("qps", 15, "percentage", "15%", "description", "加解密业务：特殊处理")
        ));
        
        // 防护机制
        overview.put("protectionMechanisms", Map.of(
            "ddosProtection", "IP限流防止分布式攻击",
            "apiAbuseProtection", "用户限流防止恶意调用",
            "hotspotProtection", "URL限流防止热点API被刷",
            "resourceProtection", "权重限流保证核心业务可用"
        ));
        
        return ResponseEntity.ok(overview);
    }

    /**
     * 获取熔断器详细信息
     */
    private Map<String, Object> getCircuitBreakerInfo(CircuitBreaker circuitBreaker) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", circuitBreaker.getName());
        info.put("state", circuitBreaker.getState());
        info.put("failureRate", circuitBreaker.getMetrics().getFailureRate());
        info.put("slowCallRate", circuitBreaker.getMetrics().getSlowCallRate());
        info.put("numberOfFailedCalls", circuitBreaker.getMetrics().getNumberOfFailedCalls());
        info.put("numberOfSlowCalls", circuitBreaker.getMetrics().getNumberOfSlowCalls());
        info.put("numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
        info.put("numberOfNotPermittedCalls", circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());
        return info;
    }
} 