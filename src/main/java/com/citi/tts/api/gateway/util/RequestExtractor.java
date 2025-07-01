package com.citi.tts.api.gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 请求参数提取工具类
 * 用于从ServerWebExchange中提取完整的请求信息
 */
@Component
@Slf4j
public class RequestExtractor {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 提取完整的请求信息
     * 包括headers、query parameters、body等
     */
    public  Mono<Map<String, Object>> extractRequestInfo(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        Map<String, Object> requestInfo = new HashMap<>();
        
        // 基本信息
        requestInfo.put("path", request.getPath().value());
        requestInfo.put("method", request.getMethod().name());
        requestInfo.put("uri", request.getURI().toString());
        requestInfo.put("clientIp", getClientIp(request));
        requestInfo.put("userId", getUserId(request));
        requestInfo.put("requestId", getRequestId(request));
        
        // 请求头信息
        Map<String, String> headers = request.getHeaders().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(", ", entry.getValue())
                ));
        requestInfo.put("headers", headers);
        
        // 查询参数
        Map<String, String> queryParams = request.getQueryParams().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(", ", entry.getValue())
                ));
        requestInfo.put("queryParams", queryParams);
        
        // 尝试读取请求体
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .map(dataBuffer -> {
                    try {
                        if (dataBuffer.readableByteCount() > 0) {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            String body = new String(bytes, StandardCharsets.UTF_8);
                            
                            // 尝试解析JSON
                            try {
                                Object jsonBody = getJson(body);
                                requestInfo.put("body", jsonBody);
                            } catch (Exception e) {
                                // 如果不是JSON，直接存储字符串
                                requestInfo.put("body", body);
                            }
                            
                            DataBufferUtils.release(dataBuffer);
                        } else {
                            requestInfo.put("body", null);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read request body", e);
                        requestInfo.put("body", "Unable to read body: " + e.getMessage());
                    }
                    
                    return requestInfo;
                });
    }

    private Map getJson(String body) {
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取客户端IP
     * 支持多种代理环境
     */
    public  String getClientIp(ServerHttpRequest request) {
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
        
        // 从CF-Connecting-IP获取（Cloudflare）
        String cfConnectingIp = request.getHeaders().getFirst("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp;
        }
        
        // 从远程地址获取
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    /**
     * 获取用户ID
     * 支持多种用户识别方式
     */
    public  String getUserId(ServerHttpRequest request) {
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
        
        // 从查询参数中获取用户ID
        String queryUserId = request.getQueryParams().getFirst("userId");
        if (queryUserId != null && !queryUserId.isEmpty()) {
            return queryUserId;
        }
        
        return "anonymous";
    }

    /**
     * 从JWT token中提取用户ID
     * 简化实现，实际项目中需要proper JWT解析
     */
    private  String extractUserIdFromToken(String token) {
        try {
            // 这里应该使用proper JWT解析库
            // 简化实现，实际项目中需要JWT解析
            if (token != null && !token.isEmpty()) {
                return "jwt-user-" + token.hashCode();
            }
        } catch (Exception e) {
            log.warn("Failed to extract user ID from token", e);
        }
        return "anonymous";
    }

    /**
     * 获取请求ID
     * 支持多种请求ID头
     */
    public  String getRequestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst("X-Request-ID");
        if (requestId == null) {
            requestId = request.getHeaders().getFirst("X-Correlation-ID");
        }
        if (requestId == null) {
            requestId = java.util.UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * 提取请求头信息
     */
    public  Map<String, String> extractHeaders(ServerHttpRequest request) {
        return request.getHeaders().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(", ", entry.getValue())
                ));
    }

    /**
     * 提取查询参数
     */
    public  Map<String, String> extractQueryParams(ServerHttpRequest request) {
        return request.getQueryParams().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(", ", entry.getValue())
                ));
    }

    /**
     * 提取请求体（字符串形式）
     */
    public  Mono<String> extractBodyAsString(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .map(dataBuffer -> {
                    try {
                        if (dataBuffer.readableByteCount() > 0) {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            String body = new String(bytes, StandardCharsets.UTF_8);
                            DataBufferUtils.release(dataBuffer);
                            return body;
                        } else {
                            DataBufferUtils.release(dataBuffer);
                            return null;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read request body", e);
                        DataBufferUtils.release(dataBuffer);
                        return "Unable to read body: " + e.getMessage();
                    }
                });
    }

    /**
     * 提取请求体（JSON对象形式）
     */
    public  Mono<Object> extractBodyAsJson(ServerWebExchange exchange) {
        return extractBodyAsString(exchange)
                .map(body -> {
                    if (body != null && !body.isEmpty()) {
                        try {
                            return objectMapper.readValue(body, Object.class);
                        } catch (Exception e) {
                            log.warn("Failed to parse JSON body", e);
                            return body; // 返回原始字符串
                        }
                    }
                    return null;
                });
    }

    /**
     * 获取基本请求信息（不包含body）
     */
    public  Map<String, Object> getBasicRequestInfo(ServerHttpRequest request) {
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("path", request.getPath().value());
        requestInfo.put("method", request.getMethod().name());
        requestInfo.put("uri", request.getURI().toString());
        requestInfo.put("clientIp", getClientIp(request));
        requestInfo.put("userId", getUserId(request));
        requestInfo.put("requestId", getRequestId(request));
        requestInfo.put("headers", extractHeaders(request));
        requestInfo.put("queryParams", extractQueryParams(request));
        return requestInfo;
    }
} 