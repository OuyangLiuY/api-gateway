package com.citi.tts.api.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流测试控制器
 * 用于测试各种限流场景，验证配置效果
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway/test")
public class RateLimitTestController {

    private final AtomicLong requestCount = new AtomicLong(0);

    /**
     * 核心API测试接口
     * 配置：60 QPS，权重1.0
     */
    @GetMapping("/core/payment")
    public ResponseEntity<Map<String, Object>> testCoreApi() {
        long count = requestCount.incrementAndGet();
        log.info("Core API test request #{}", count);
        
        return ResponseEntity.ok(Map.of(
            "message", "Core API test successful",
            "requestId", count,
            "timestamp", Instant.now(),
            "priority", "CORE",
            "qps", 60,
            "weight", 1.0
        ));
    }

    /**
     * 普通API测试接口
     * 配置：25 QPS，权重0.4
     */
    @GetMapping("/normal/balance")
    public ResponseEntity<Map<String, Object>> testNormalApi() {
        long count = requestCount.incrementAndGet();
        log.info("Normal API test request #{}", count);
        
        return ResponseEntity.ok(Map.of(
            "message", "Normal API test successful",
            "requestId", count,
            "timestamp", Instant.now(),
            "priority", "NORMAL",
            "qps", 25,
            "weight", 0.4
        ));
    }

    /**
     * 非核心API测试接口
     * 配置：10 QPS，权重0.2
     */
    @GetMapping("/non-core/statistics")
    public ResponseEntity<Map<String, Object>> testNonCoreApi() {
        long count = requestCount.incrementAndGet();
        log.info("Non-core API test request #{}", count);
        
        return ResponseEntity.ok(Map.of(
            "message", "Non-core API test successful",
            "requestId", count,
            "timestamp", Instant.now(),
            "priority", "NON_CORE",
            "qps", 10,
            "weight", 0.2
        ));
    }

    /**
     * 加解密API测试接口
     * 配置：15 QPS，权重0.3
     */
    @GetMapping("/crypto/encrypt")
    public ResponseEntity<Map<String, Object>> testCryptoApi() {
        long count = requestCount.incrementAndGet();
        log.info("Crypto API test request #{}", count);
        
        return ResponseEntity.ok(Map.of(
            "message", "Crypto API test successful",
            "requestId", count,
            "timestamp", Instant.now(),
            "priority", "CRYPTO",
            "qps", 15,
            "weight", 0.3
        ));
    }

    /**
     * 用户限流测试接口
     * 配置：20 QPS per user
     */
    @GetMapping("/user/test")
    public ResponseEntity<Map<String, Object>> testUserRateLimit(
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestHeader(value = "Authorization", required = false) String authToken) {
        
        long count = requestCount.incrementAndGet();
        String actualUserId = userId != null ? userId : 
                             (authToken != null ? "user_" + authToken.hashCode() : "anonymous");
        
        log.info("User rate limit test request #{} for user: {}", count, actualUserId);
        
        return ResponseEntity.ok(Map.of(
            "message", "User rate limit test successful",
            "requestId", count,
            "userId", actualUserId,
            "timestamp", Instant.now(),
            "userQps", 20,
            "description", "用户限流测试：单个用户每秒最多20个请求"
        ));
    }

    /**
     * IP限流测试接口
     * 配置：30 QPS per IP
     */
    @GetMapping("/ip/test")
    public ResponseEntity<Map<String, Object>> testIpRateLimit() {
        long count = requestCount.incrementAndGet();
        log.info("IP rate limit test request #{}", count);
        
        return ResponseEntity.ok(Map.of(
            "message", "IP rate limit test successful",
            "requestId", count,
            "timestamp", Instant.now(),
            "ipQps", 30,
            "description", "IP限流测试：单个IP每秒最多30个请求"
        ));
    }

    /**
     * URL限流测试接口
     * 配置：40 QPS per URL
     */
    @GetMapping("/url/test")
    public ResponseEntity<Map<String, Object>> testUrlRateLimit() {
        long count = requestCount.incrementAndGet();
        log.info("URL rate limit test request #{}", count);
        
        return ResponseEntity.ok(Map.of(
            "message", "URL rate limit test successful",
            "requestId", count,
            "timestamp", Instant.now(),
            "urlQps", 40,
            "description", "URL限流测试：单个URL每秒最多40个请求"
        ));
    }

    /**
     * 压力测试接口
     * 用于模拟高并发场景
     */
    @PostMapping("/pressure-test")
    public ResponseEntity<Map<String, Object>> pressureTest(
            @RequestParam(defaultValue = "100") int requests,
            @RequestParam(defaultValue = "1000") long delayMs) {
        
        log.info("Starting pressure test with {} requests, {}ms delay", requests, delayMs);
        
        return ResponseEntity.ok(Map.of(
            "message", "Pressure test started",
            "requests", requests,
            "delayMs", delayMs,
            "timestamp", Instant.now(),
            "description", "压力测试：模拟高并发请求场景"
        ));
    }

    /**
     * 获取测试统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getTestStatistics() {
        return ResponseEntity.ok(Map.of(
            "totalRequests", requestCount.get(),
            "timestamp", Instant.now(),
            "testEndpoints", Map.of(
                "coreApi", "/api/gateway/test/core/payment",
                "normalApi", "/api/gateway/test/normal/balance",
                "nonCoreApi", "/api/gateway/test/non-core/statistics",
                "cryptoApi", "/api/gateway/test/crypto/encrypt",
                "userLimit", "/api/gateway/test/user/test",
                "ipLimit", "/api/gateway/test/ip/test",
                "urlLimit", "/api/gateway/test/url/test",
                "pressureTest", "/api/gateway/test/pressure-test"
            ),
            "rateLimitConfig", Map.of(
                "systemMaxQps", 100,
                "coreApiQps", 60,
                "normalApiQps", 25,
                "nonCoreApiQps", 10,
                "cryptoApiQps", 15,
                "userQps", 20,
                "ipQps", 30,
                "urlQps", 40
            )
        ));
    }

    /**
     * 重置测试计数器
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetTestCounter() {
        long oldCount = requestCount.getAndSet(0);
        log.info("Reset test counter from {} to 0", oldCount);
        
        return ResponseEntity.ok(Map.of(
            "message", "Test counter reset successfully",
            "previousCount", oldCount,
            "currentCount", 0,
            "timestamp", Instant.now()
        ));
    }
} 