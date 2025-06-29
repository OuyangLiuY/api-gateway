package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.service.ServiceDegradationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务降级过滤器
 * 在网关层面拦截请求，根据服务级别执行不同的降级策略
 */
@Slf4j
@Component
public class ServiceDegradationFilter implements GlobalFilter, Ordered {

    @Autowired
    private ServiceDegradationService degradationService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String serviceName = extractServiceName(path);
        
        // 获取服务级别
        ServiceDegradationService.ServiceLevel serviceLevel = 
            degradationService.getServiceLevel(serviceName, path);
        
        log.debug("Service degradation filter - Service: {}, Path: {}, Level: {}", 
                serviceName, path, serviceLevel);

        // 继续执行过滤器链
        return chain.filter(exchange)
                .onErrorResume(throwable -> {
                    log.error("Service error detected - Service: {}, Path: {}, Error: {}", 
                            serviceName, path, throwable.getMessage());
                    
                    // 执行服务降级
                    return executeServiceDegradation(exchange, serviceName, path, serviceLevel, throwable);
                });
    }

    /**
     * 执行服务降级
     */
    private Mono<Void> executeServiceDegradation(ServerWebExchange exchange, 
                                                String serviceName, 
                                                String path, 
                                                ServiceDegradationService.ServiceLevel serviceLevel,
                                                Throwable error) {
        
        // 构建降级请求
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("path", path);
        requestData.put("method", exchange.getRequest().getMethod().name());
        requestData.put("headers", exchange.getRequest().getHeaders());
        
        ServiceDegradationService.DegradationRequest degradationRequest = 
            new ServiceDegradationService.DegradationRequest(
                serviceName, 
                path, 
                serviceLevel, 
                requestData, 
                error.getClass().getSimpleName(), 
                error.getMessage()
            );

        // 执行降级处理
        return degradationService.executeDegradation(degradationRequest)
                .flatMap(response -> {
                    log.info("Service degradation completed - Service: {}, Level: {}, FallbackType: {}", 
                            serviceName, serviceLevel, response.getFallbackType());
                    
                    // 返回降级响应
                    return writeDegradationResponse(exchange, response);
                })
                .onErrorResume(e -> {
                    log.error("Service degradation failed - Service: {}", serviceName, e);
                    // 降级失败，返回通用错误响应
                    return writeErrorResponse(exchange, "Service temporarily unavailable");
                });
    }

    /**
     * 写入降级响应
     */
    private Mono<Void> writeDegradationResponse(ServerWebExchange exchange, 
                                               ServiceDegradationService.DegradationResponse response) {
        
        ServerHttpResponse httpResponse = exchange.getResponse();
        httpResponse.setStatusCode(HttpStatus.OK);
        httpResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 构建响应体
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", response.isSuccess());
        responseBody.put("message", response.getMessage());
        responseBody.put("data", response.getData());
        responseBody.put("fallbackType", response.getFallbackType());
        responseBody.put("timestamp", response.getTimestamp());
        responseBody.put("degraded", true);

        return httpResponse.writeWith(
            Mono.just(httpResponse.bufferFactory().wrap(
                responseBody.toString().getBytes()
            ))
        );
    }

    /**
     * 写入错误响应
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse httpResponse = exchange.getResponse();
        httpResponse.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        httpResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", false);
        responseBody.put("message", message);
        responseBody.put("timestamp", System.currentTimeMillis());

        return httpResponse.writeWith(
            Mono.just(httpResponse.bufferFactory().wrap(
                responseBody.toString().getBytes()
            ))
        );
    }

    /**
     * 从路径中提取服务名
     */
    private String extractServiceName(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        
        // 移除开头的斜杠
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        
        // 获取第一个路径段作为服务名
        String[] pathSegments = cleanPath.split("/");
        if (pathSegments.length > 0) {
            return pathSegments[0];
        }
        
        return "unknown";
    }

    @Override
    public int getOrder() {
        // 在限流过滤器之后执行
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
} 