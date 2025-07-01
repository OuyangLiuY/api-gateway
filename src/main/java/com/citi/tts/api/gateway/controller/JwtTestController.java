package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.crypto.JwtTestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT测试控制器
 * 用于验证JWT认证和X-Session头部功能
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
public class JwtTestController {

    @Autowired
    private JwtTestUtil jwtTestUtil;

    /**
     * 公开接口 - 无需认证
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint(ServerHttpRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is a public endpoint");
        response.put("timestamp", System.currentTimeMillis());
        response.put("headers", extractHeaders(request));
        
        log.info("Public endpoint accessed");
        return ResponseEntity.ok(response);
    }

    /**
     * 受保护的接口 - 需要JWT认证
     */
    @GetMapping("/protected")
    public ResponseEntity<Map<String, Object>> protectedEndpoint(ServerHttpRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is a protected endpoint");
        response.put("timestamp", System.currentTimeMillis());
        response.put("headers", extractHeaders(request));
        response.put("session", request.getHeaders().getFirst("X-Session"));
        response.put("userId", request.getHeaders().getFirst("X-User-ID"));
        response.put("tenantId", request.getHeaders().getFirst("X-Tenant-ID"));
        
        log.info("Protected endpoint accessed by user: {}, tenant: {}, session: {}", 
                request.getHeaders().getFirst("X-User-ID"),
                request.getHeaders().getFirst("X-Tenant-ID"),
                request.getHeaders().getFirst("X-Session"));
        
        return ResponseEntity.ok(response);
    }

    /**
     * 管理员接口 - 需要管理员权限
     */
    @GetMapping("/admin")
    public ResponseEntity<Map<String, Object>> adminEndpoint(ServerHttpRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is an admin endpoint");
        response.put("timestamp", System.currentTimeMillis());
        response.put("headers", extractHeaders(request));
        response.put("session", request.getHeaders().getFirst("X-Session"));
        response.put("userId", request.getHeaders().getFirst("X-User-ID"));
        response.put("tenantId", request.getHeaders().getFirst("X-Tenant-ID"));
        
        log.info("Admin endpoint accessed by user: {}, tenant: {}, session: {}", 
                request.getHeaders().getFirst("X-User-ID"),
                request.getHeaders().getFirst("X-Tenant-ID"),
                request.getHeaders().getFirst("X-Session"));
        
        return ResponseEntity.ok(response);
    }

    /**
     * 匿名接口 - 支持匿名访问
     */
    @GetMapping("/anonymous")
    public ResponseEntity<Map<String, Object>> anonymousEndpoint(ServerHttpRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is an anonymous endpoint");
        response.put("timestamp", System.currentTimeMillis());
        response.put("headers", extractHeaders(request));
        response.put("session", request.getHeaders().getFirst("X-Session"));
        response.put("userId", request.getHeaders().getFirst("X-User-ID"));
        response.put("tenantId", request.getHeaders().getFirst("X-Tenant-ID"));
        
        log.info("Anonymous endpoint accessed");
        return ResponseEntity.ok(response);
    }

    /**
     * 生成测试token
     */
    @PostMapping("/generate-token")
    public ResponseEntity<Map<String, Object>> generateToken(
            @RequestParam String userId,
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "user") String role) {
        
        String token = jwtTestUtil.generateTestToken(userId, tenantId, role);
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("authHeader", "Bearer " + token);
        response.put("userId", userId);
        response.put("tenantId", tenantId);
        response.put("role", role);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("Generated test token for user: {}, tenant: {}, role: {}", userId, tenantId, role);
        return ResponseEntity.ok(response);
    }

    /**
     * 验证token
     */
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        boolean isValid = jwtTestUtil.validateToken(token);
        String userId = jwtTestUtil.getUserIdFromToken(token);
        String tenantId = jwtTestUtil.getTenantIdFromToken(token);
        
        Map<String, Object> response = new HashMap<>();
        response.put("valid", isValid);
        response.put("userId", userId);
        response.put("tenantId", tenantId);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("Token validation result: valid={}, userId={}, tenantId={}", isValid, userId, tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * 租户相关接口
     */
    @GetMapping("/tenant/{tenantId}/users")
    public ResponseEntity<Map<String, Object>> tenantUsersEndpoint(
            @PathVariable String tenantId,
            ServerHttpRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Tenant users endpoint");
        response.put("tenantId", tenantId);
        response.put("timestamp", System.currentTimeMillis());
        response.put("headers", extractHeaders(request));
        response.put("session", request.getHeaders().getFirst("X-Session"));
        response.put("userId", request.getHeaders().getFirst("X-User-ID"));
        response.put("extractedTenantId", request.getHeaders().getFirst("X-Tenant-ID"));
        
        log.info("Tenant users endpoint accessed for tenant: {}, user: {}, session: {}", 
                tenantId,
                request.getHeaders().getFirst("X-User-ID"),
                request.getHeaders().getFirst("X-Session"));
        
        return ResponseEntity.ok(response);
    }

    /**
     * 提取请求头信息
     */
    private Map<String, String> extractHeaders(ServerHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });
        return headers;
    }
} 