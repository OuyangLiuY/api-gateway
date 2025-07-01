package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.service.ServiceDegradationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * 服务降级网关过滤器
 * 用于在路由中单独配置，支持针对不同API设置不同的降级策略
 * 
 * 降级触发条件：
 * 1. 网络连接错误（ConnectException, SocketTimeoutException）
 * 2. 服务超时错误（TimeoutException, ReadTimeoutException）
 * 3. 服务不可用错误（503 Service Unavailable）
 * 4. 熔断器触发错误（CircuitBreakerOpenException）
 * 5. 负载均衡错误（NoAvailableServerException）
 * 
 * 不触发降级的错误：
 * 1. 客户端错误（4xx）
 * 2. 业务逻辑错误
 * 3. 认证授权错误
 * 4. 参数验证错误
 * 5. 数据格式错误
 */
@Slf4j
@Component
public class ServiceDegradationGatewayFilter extends AbstractGatewayFilterFactory<ServiceDegradationGatewayFilter.Config> {

    @Autowired
    private ServiceDegradationService degradationService;

    // 应该触发降级的错误类型
    private static final Set<String> DEGRADATION_TRIGGER_ERRORS = Set.of(
        // 网络连接错误
        "ConnectException",
        "SocketTimeoutException", 
        "ConnectionTimeoutException",
        "NoRouteToHostException",
        "UnknownHostException",
        
        // 服务超时错误
        "TimeoutException",
        "ReadTimeoutException",
        "WriteTimeoutException",
        
        // 服务不可用错误
        "ServiceUnavailableException",
        "NoAvailableServerException",
        "LoadBalancerException",
        
        // 熔断器错误
        "CircuitBreakerOpenException",
        "CircuitBreakerException",
        
        // 其他系统级错误
        "IOException",
        "SocketException",
        "NetworkException"
    );

    // 不应该触发降级的错误类型
    private static final Set<String> NON_DEGRADATION_ERRORS = Set.of(
        // 客户端错误
        "BadRequestException",
        "UnauthorizedException", 
        "ForbiddenException",
        "NotFoundException",
        "MethodNotAllowedException",
        "NotAcceptableException",
        "ConflictException",
        "GoneException",
        "UnsupportedMediaTypeException",
        
        // 业务逻辑错误
        "BusinessException",
        "ValidationException",
        "DataIntegrityException",
        
        // 认证授权错误
        "AuthenticationException",
        "AuthorizationException",
        "TokenExpiredException",
        "InvalidTokenException",
        
        // 参数验证错误
        "IllegalArgumentException",
        "NullPointerException",
        "NumberFormatException",
        
        // 数据格式错误
        "JsonParseException",
        "XmlParseException",
        "DataFormatException"
    );

    public ServiceDegradationGatewayFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                String path = exchange.getRequest().getPath().value();
                String serviceName = config.getServiceName();
                ServiceDegradationService.ServiceLevel serviceLevel = config.getServiceLevel();
                
                log.debug("Service degradation filter - Service: {}, Path: {}, Level: {}", 
                        serviceName, path, serviceLevel);

                // 继续执行过滤器链
                return chain.filter(exchange)
                        .onErrorResume(throwable -> {
                            log.error("Service error detected - Service: {}, Path: {}, Error: {}", 
                                    serviceName, path, throwable.getMessage());
                            
                            // 判断是否应该触发降级
                            if (shouldTriggerDegradation(throwable, exchange)) {
                                log.info("Triggering service degradation - Service: {}, Error: {}", 
                                        serviceName, throwable.getClass().getSimpleName());
                                return executeServiceDegradation(exchange, serviceName, path, serviceLevel, throwable);
                            } else {
                                log.error("Not triggering degradation for error type - Service: {}, Error: {}",
                                        serviceName, throwable.getClass().getSimpleName());
                                // 不触发降级，直接返回错误
                                return writeErrorResponse(exchange, throwable);
                            }
                        });
            }
        };
    }

    /**
     * 判断是否应该触发降级策略
     */
    private boolean shouldTriggerDegradation(Throwable throwable, ServerWebExchange exchange) {
        String errorType = throwable.getClass().getSimpleName();
        
        // 1. 检查是否在明确不触发降级的错误列表中
        if (NON_DEGRADATION_ERRORS.contains(errorType)) {
            log.debug("Error type {} is in non-degradation list", errorType);
            return false;
        }
        
        // 2. 检查是否在明确触发降级的错误列表中
        if (DEGRADATION_TRIGGER_ERRORS.contains(errorType)) {
            log.debug("Error type {} is in degradation trigger list", errorType);
            return true;
        }
        
        // 3. 检查HTTP状态码
        if (exchange.getResponse().getStatusCode() != null) {
            int statusCode = exchange.getResponse().getStatusCode().value();
            if (isDegradationStatusCode(statusCode)) {
                log.debug("HTTP status code {} should trigger degradation", statusCode);
                return true;
            } else if (isNonDegradationStatusCode(statusCode)) {
                log.debug("HTTP status code {} should not trigger degradation", statusCode);
                return false;
            }
        }
        
        // 4. 检查错误消息内容
        String errorMessage = throwable.getMessage();
        if (errorMessage != null) {
            if (containsDegradationKeywords(errorMessage)) {
                log.debug("Error message contains degradation keywords: {}", errorMessage);
                return true;
            } else if (containsNonDegradationKeywords(errorMessage)) {
                log.debug("Error message contains non-degradation keywords: {}", errorMessage);
                return false;
            }
        }
        
        // 5. 检查异常的根本原因
        Throwable rootCause = getRootCause(throwable);
        if (rootCause != null && rootCause != throwable) {
            String rootCauseType = rootCause.getClass().getSimpleName();
            if (DEGRADATION_TRIGGER_ERRORS.contains(rootCauseType)) {
                log.debug("Root cause {} should trigger degradation", rootCauseType);
                return true;
            } else if (NON_DEGRADATION_ERRORS.contains(rootCauseType)) {
                log.debug("Root cause {} should not trigger degradation", rootCauseType);
                return false;
            }
        }
        
        // 6. 默认策略：对于未知错误类型，根据服务级别决定
        // 核心服务：倾向于触发降级以保障业务连续性
        // 非核心服务：倾向于不触发降级以避免资源浪费
        return shouldTriggerByDefault(throwable);
    }

    /**
     * 判断HTTP状态码是否应该触发降级
     */
    private boolean isDegradationStatusCode(int statusCode) {
        return statusCode == 503 || // Service Unavailable
               statusCode == 502 || // Bad Gateway
               statusCode == 504;   // Gateway Timeout
    }

    /**
     * 判断HTTP状态码是否不应该触发降级
     */
    private boolean isNonDegradationStatusCode(int statusCode) {
        return statusCode >= 400 && statusCode < 500; // 客户端错误
    }

    /**
     * 检查错误消息是否包含降级关键词
     */
    private boolean containsDegradationKeywords(String errorMessage) {
        String lowerMessage = errorMessage.toLowerCase();
        return lowerMessage.contains("timeout") ||
               lowerMessage.contains("connection") ||
               lowerMessage.contains("unavailable") ||
               lowerMessage.contains("circuit") ||
               lowerMessage.contains("load balancer") ||
               lowerMessage.contains("no available") ||
               lowerMessage.contains("network") ||
               lowerMessage.contains("socket");
    }

    /**
     * 检查错误消息是否包含非降级关键词
     */
    private boolean containsNonDegradationKeywords(String errorMessage) {
        String lowerMessage = errorMessage.toLowerCase();
        return lowerMessage.contains("validation") ||
               lowerMessage.contains("invalid") ||
               lowerMessage.contains("unauthorized") ||
               lowerMessage.contains("forbidden") ||
               lowerMessage.contains("not found") ||
               lowerMessage.contains("bad request") ||
               lowerMessage.contains("conflict") ||
               lowerMessage.contains("business");
    }

    /**
     * 获取异常的根本原因
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * 默认降级策略
     */
    private boolean shouldTriggerByDefault(Throwable throwable) {
        // 对于未知错误类型，根据错误特征判断
        String errorType = throwable.getClass().getSimpleName();
        String errorMessage = throwable.getMessage();
        
        // 如果是运行时异常且包含系统级错误信息，倾向于触发降级
        if (throwable instanceof RuntimeException) {
            if (errorMessage != null && 
                (errorMessage.contains("timeout") || 
                 errorMessage.contains("connection") || 
                 errorMessage.contains("network"))) {
                return true;
            }
        }
        
        // 默认不触发降级，避免对业务错误进行不必要的降级处理
        return false;
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
     * 写入原始错误响应
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, Throwable throwable) {
        ServerHttpResponse httpResponse = exchange.getResponse();
        
        // 根据异常类型设置合适的HTTP状态码
        HttpStatus status = determineHttpStatus(throwable);
        httpResponse.setStatusCode(status);
        httpResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", false);
        responseBody.put("message", throwable.getMessage());
        responseBody.put("errorType", throwable.getClass().getSimpleName());
        responseBody.put("timestamp", System.currentTimeMillis());

        return httpResponse.writeWith(
            Mono.just(httpResponse.bufferFactory().wrap(
                responseBody.toString().getBytes()
            ))
        );
    }

    /**
     * 根据异常类型确定HTTP状态码
     */
    private HttpStatus determineHttpStatus(Throwable throwable) {
        String errorType = throwable.getClass().getSimpleName();
        
        if (errorType.contains("Timeout")) {
            return HttpStatus.REQUEST_TIMEOUT;
        } else if (errorType.contains("Connection")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else if (errorType.contains("Unauthorized")) {
            return HttpStatus.UNAUTHORIZED;
        } else if (errorType.contains("Forbidden")) {
            return HttpStatus.FORBIDDEN;
        } else if (errorType.contains("NotFound")) {
            return HttpStatus.NOT_FOUND;
        } else if (errorType.contains("BadRequest")) {
            return HttpStatus.BAD_REQUEST;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * 配置类
     */
    public static class Config {
        private ServiceDegradationService.ServiceLevel serviceLevel;
        private String serviceName;

        public Config() {}

        public Config(ServiceDegradationService.ServiceLevel serviceLevel, String serviceName) {
            this.serviceLevel = serviceLevel;
            this.serviceName = serviceName;
        }

        public ServiceDegradationService.ServiceLevel getServiceLevel() { return serviceLevel; }
        public void setServiceLevel(ServiceDegradationService.ServiceLevel serviceLevel) { this.serviceLevel = serviceLevel; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    }

    /**
     * 支持短配置名称
     */
    @Override
    public String name() {
        return "ServiceDegradation";
    }
} 