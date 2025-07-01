package com.citi.tts.api.gateway.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT结构演示控制器
 * 展示JWT的Header、Payload和Signature三个部分
 */
@Slf4j
@RestController
@RequestMapping("/api/jwt-structure")
public class JwtStructureController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 演示JWT的三个部分
     */
    @GetMapping("/demo")
    public Map<String, Object> demonstrateJwtStructure() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. Header示例
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            header.put("kid", "gateway-key-2024");
            header.put("cty", "application/json");
            header.put("x-version", "1.0");
            
            // 2. Payload示例
            Map<String, Object> payload = new HashMap<>();
            
            // 标准Claims
            payload.put("iss", "citi-gateway");
            payload.put("sub", "user123");
            payload.put("aud", "api-gateway");
            payload.put("exp", System.currentTimeMillis() / 1000 + 3600);
            payload.put("iat", System.currentTimeMillis() / 1000);
            payload.put("jti", "jwt-" + System.currentTimeMillis());
            
            // 用户信息
            payload.put("name", "张三");
            payload.put("email", "zhangsan@citi.com");
            payload.put("role", "admin");
            payload.put("tenantId", "tenant-123");
            
            // 权限信息
            payload.put("permissions", new String[]{"user:read", "user:write", "admin:manage"});
            
            // 会话信息
            payload.put("sessionId", "session-" + System.currentTimeMillis());
            payload.put("loginTime", System.currentTimeMillis() / 1000);
            
            // 偏好设置
            Map<String, Object> preferences = new HashMap<>();
            preferences.put("theme", "dark");
            preferences.put("language", "zh-CN");
            preferences.put("timezone", "Asia/Shanghai");
            payload.put("preferences", preferences);
            
            // 3. 编码后的部分
            String headerJson = objectMapper.writeValueAsString(header);
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes());
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes());
            
            result.put("header", header);
            result.put("payload", payload);
            result.put("encodedHeader", encodedHeader);
            result.put("encodedPayload", encodedPayload);
            result.put("signaturePlaceholder", "SIGNATURE_WOULD_BE_HERE");
            result.put("completeJwt", encodedHeader + "." + encodedPayload + ".SIGNATURE_WOULD_BE_HERE");
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize JWT structure: {}", e.getMessage());
            result.put("error", "Failed to serialize JWT structure: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 展示不同类型的Header
     */
    @GetMapping("/headers")
    public Map<String, Object> showDifferentHeaders() {
        Map<String, Object> result = new HashMap<>();
        
        // HS256 Header
        Map<String, Object> hs256Header = new HashMap<>();
        hs256Header.put("alg", "HS256");
        hs256Header.put("typ", "JWT");
        hs256Header.put("kid", "hs256-key");
        
        // RS256 Header
        Map<String, Object> rs256Header = new HashMap<>();
        rs256Header.put("alg", "RS256");
        rs256Header.put("typ", "JWT");
        rs256Header.put("kid", "rs256-key");
        rs256Header.put("x5u", "https://example.com/cert.pem");
        
        // ES256 Header
        Map<String, Object> es256Header = new HashMap<>();
        es256Header.put("alg", "ES256");
        es256Header.put("typ", "JWT");
        es256Header.put("kid", "es256-key");
        
        result.put("HS256", hs256Header);
        result.put("RS256", rs256Header);
        result.put("ES256", es256Header);
        
        return result;
    }

    /**
     * 展示不同类型的Payload
     */
    @GetMapping("/payloads")
    public Map<String, Object> showDifferentPayloads() {
        Map<String, Object> result = new HashMap<>();
        
        // 最小Payload
        Map<String, Object> minimalPayload = new HashMap<>();
        minimalPayload.put("sub", "user123");
        minimalPayload.put("exp", System.currentTimeMillis() / 1000 + 3600);
        
        // 标准Payload
        Map<String, Object> standardPayload = new HashMap<>();
        standardPayload.put("iss", "citi-gateway");
        standardPayload.put("sub", "user123");
        standardPayload.put("aud", "api-gateway");
        standardPayload.put("exp", System.currentTimeMillis() / 1000 + 3600);
        standardPayload.put("iat", System.currentTimeMillis() / 1000);
        standardPayload.put("jti", "jwt-" + System.currentTimeMillis());
        
        // 完整Payload
        Map<String, Object> completePayload = new HashMap<>();
        completePayload.put("iss", "citi-gateway");
        completePayload.put("sub", "user123");
        completePayload.put("aud", "api-gateway");
        completePayload.put("exp", System.currentTimeMillis() / 1000 + 3600);
        completePayload.put("iat", System.currentTimeMillis() / 1000);
        completePayload.put("jti", "jwt-" + System.currentTimeMillis());
        completePayload.put("name", "张三");
        completePayload.put("email", "zhangsan@citi.com");
        completePayload.put("role", "admin");
        completePayload.put("tenantId", "tenant-123");
        completePayload.put("permissions", new String[]{"user:read", "user:write", "admin:manage"});
        completePayload.put("sessionId", "session-" + System.currentTimeMillis());
        completePayload.put("loginTime", System.currentTimeMillis() / 1000);
        
        // 偏好设置
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("theme", "dark");
        preferences.put("language", "zh-CN");
        preferences.put("timezone", "Asia/Shanghai");
        completePayload.put("preferences", preferences);
        
        // 地理位置
        Map<String, Object> location = new HashMap<>();
        location.put("country", "CN");
        location.put("region", "Beijing");
        location.put("city", "Beijing");
        completePayload.put("location", location);
        
        // 设备信息
        Map<String, Object> device = new HashMap<>();
        device.put("deviceId", "device-" + System.currentTimeMillis());
        device.put("deviceType", "desktop");
        device.put("os", "Windows 10");
        device.put("browser", "Chrome");
        completePayload.put("device", device);
        
        result.put("minimal", minimalPayload);
        result.put("standard", standardPayload);
        result.put("complete", completePayload);
        
        return result;
    }

    /**
     * 展示签名算法对比
     */
    @GetMapping("/signatures")
    public Map<String, Object> showSignatureAlgorithms() {
        Map<String, Object> result = new HashMap<>();
        
        // HMAC算法
        Map<String, Object> hmac = new HashMap<>();
        hmac.put("type", "对称加密");
        hmac.put("algorithms", new String[]{"HS256", "HS384", "HS512"});
        hmac.put("description", "使用密钥进行签名和验证");
        hmac.put("pros", new String[]{"计算速度快", "密钥管理简单", "适合单点认证"});
        hmac.put("cons", new String[]{"密钥分发困难", "不适合分布式系统", "密钥泄露风险高"});
        
        // RSA算法
        Map<String, Object> rsa = new HashMap<>();
        rsa.put("type", "非对称加密");
        rsa.put("algorithms", new String[]{"RS256", "RS384", "RS512"});
        rsa.put("description", "使用私钥签名，公钥验证");
        rsa.put("pros", new String[]{"密钥分发容易", "适合分布式系统", "安全性高"});
        rsa.put("cons", new String[]{"计算速度较慢", "密钥管理复杂", "需要证书管理"});
        
        // ECDSA算法
        Map<String, Object> ecdsa = new HashMap<>();
        ecdsa.put("type", "椭圆曲线");
        ecdsa.put("algorithms", new String[]{"ES256", "ES384", "ES512"});
        ecdsa.put("description", "使用椭圆曲线数字签名");
        ecdsa.put("pros", new String[]{"密钥长度短", "计算速度快", "安全性高"});
        ecdsa.put("cons", new String[]{"实现复杂", "兼容性较差", "调试困难"});
        
        result.put("HMAC", hmac);
        result.put("RSA", rsa);
        result.put("ECDSA", ecdsa);
        
        return result;
    }

    /**
     * 解析JWT Token结构
     */
    @PostMapping("/parse")
    public Map<String, Object> parseJwtToken(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                result.put("error", "Token is required");
                return result;
            }
            
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                result.put("error", "Invalid JWT format. Expected 3 parts separated by dots.");
                return result;
            }
            
            // 解析Header
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);
            
            // 解析Payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            
            // 签名部分
            String signature = parts[2];
            
            result.put("header", header);
            result.put("payload", payload);
            result.put("signature", signature);
            result.put("signatureLength", signature.length());
            result.put("isValidFormat", true);
            
            // 分析Payload大小
            result.put("payloadSize", payloadJson.length());
            result.put("totalSize", token.length());
            
        } catch (Exception e) {
            result.put("error", "Failed to parse JWT: " + e.getMessage());
            result.put("isValidFormat", false);
        }
        
        return result;
    }

    /**
     * 生成示例JWT Token
     */
    @PostMapping("/generate")
    public Map<String, Object> generateSampleToken(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String type = request.getOrDefault("type", "standard");
            String userId = request.getOrDefault("userId", "user123");
            String tenantId = request.getOrDefault("tenantId", "tenant-123");
            String role = request.getOrDefault("role", "user");
            
            // 这里应该调用实际的JWT生成服务
            // 为了演示，我们只返回结构化的信息
            result.put("type", type);
            result.put("userId", userId);
            result.put("tenantId", tenantId);
            result.put("role", role);
            result.put("message", "Token generation would be implemented with actual JWT library");
            
        } catch (Exception e) {
            result.put("error", "Failed to generate token: " + e.getMessage());
        }
        
        return result;
    }
} 