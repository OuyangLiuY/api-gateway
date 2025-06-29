package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.util.RequestExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 降级处理器
 * 处理熔断器触发时的降级响应
 * 支持获取完整的请求参数信息
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * 认证服务降级处理
     */
    @GetMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "auth-service", "AUTH_SERVICE_DOWN");
    }

    /**
     * 支付服务降级处理
     */
    @GetMapping("/payment")
    public Mono<ResponseEntity<Map<String, Object>>> paymentFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "payment-service", "PAYMENT_SERVICE_DOWN");
    }

    /**
     * 用户服务降级处理
     */
    @GetMapping("/user")
    public Mono<ResponseEntity<Map<String, Object>>> userFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "user-service", "USER_SERVICE_DOWN");
    }

    /**
     * 订单服务降级处理
     */
    @GetMapping("/order")
    public Mono<ResponseEntity<Map<String, Object>>> orderFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "order-service", "ORDER_SERVICE_DOWN");
    }

    /**
     * 查询服务降级处理
     */
    @GetMapping("/query")
    public Mono<ResponseEntity<Map<String, Object>>> queryFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "query-service", "QUERY_SERVICE_DOWN");
    }

    /**
     * 统计服务降级处理
     */
    @GetMapping("/statistics")
    public Mono<ResponseEntity<Map<String, Object>>> statisticsFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "statistics-service", "STATISTICS_SERVICE_DOWN");
    }

    /**
     * 日志服务降级处理
     */
    @GetMapping("/log")
    public Mono<ResponseEntity<Map<String, Object>>> logFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "log-service", "LOG_SERVICE_DOWN");
    }

    /**
     * 账户服务降级处理
     */
    @GetMapping("/account")
    public Mono<ResponseEntity<Map<String, Object>>> accountFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "account-service", "ACCOUNT_SERVICE_DOWN");
    }

    /**
     * 报表服务降级处理
     */
    @GetMapping("/report")
    public Mono<ResponseEntity<Map<String, Object>>> reportFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "report-service", "REPORT_SERVICE_DOWN");
    }

    /**
     * 通用降级处理
     */
    @GetMapping("/default")
    public Mono<ResponseEntity<Map<String, Object>>> defaultFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "unknown-service", "SERVICE_UNAVAILABLE");
    }

    /**
     * 构建降级响应
     * 包含完整的请求参数信息
     */
    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(
            ServerWebExchange exchange, String serviceName, String errorCode) {
        
        return RequestExtractor.extractRequestInfo(exchange)
                .map(requestInfo -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("timestamp", LocalDateTime.now());
                    response.put("status", "SERVICE_UNAVAILABLE");
                    response.put("message", serviceName + "暂时不可用，请稍后重试");
                    response.put("service", serviceName);
                    response.put("fallback", true);
                    response.put("code", errorCode);
                    
                    // 添加请求信息
                    response.put("requestInfo", requestInfo);
                    
                    // 添加降级建议
                    response.put("suggestions", buildSuggestions(serviceName, requestInfo));
                    
                    log.warn("Service fallback triggered - Service: {}, Path: {}, User: {}, IP: {}", 
                            serviceName, 
                            requestInfo.get("path"), 
                            requestInfo.get("userId"), 
                            requestInfo.get("clientIp"));
                    
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                });
    }

    /**
     * 构建降级建议
     */
    private Map<String, Object> buildSuggestions(String serviceName, Map<String, Object> requestInfo) {
        Map<String, Object> suggestions = new HashMap<>();
        
        suggestions.put("retryAfter", 30); // 30秒后重试
        suggestions.put("alternativeServices", getAlternativeServices(serviceName));
        suggestions.put("contactSupport", "如果问题持续存在，请联系技术支持");
        
        // 根据服务类型提供特定建议
        switch (serviceName) {
            case "payment-service":
                suggestions.put("action", "请稍后重试支付操作");
                suggestions.put("priority", "high");
                break;
            case "auth-service":
                suggestions.put("action", "请重新登录或刷新token");
                suggestions.put("priority", "high");
                break;
            case "user-service":
                suggestions.put("action", "请稍后重试用户相关操作");
                suggestions.put("priority", "medium");
                break;
            default:
                suggestions.put("action", "请稍后重试");
                suggestions.put("priority", "low");
        }
        
        return suggestions;
    }

    /**
     * 获取替代服务
     */
    private Map<String, String> getAlternativeServices(String serviceName) {
        Map<String, String> alternatives = new HashMap<>();
        
        switch (serviceName) {
            case "payment-service":
                alternatives.put("alternative1", "备用支付服务");
                alternatives.put("alternative2", "离线支付模式");
                break;
            case "query-service":
                alternatives.put("alternative1", "缓存查询服务");
                alternatives.put("alternative2", "历史数据查询");
                break;
            case "statistics-service":
                alternatives.put("alternative1", "本地统计服务");
                break;
            default:
                alternatives.put("alternative1", "通用备用服务");
        }
        
        return alternatives;
    }
} 