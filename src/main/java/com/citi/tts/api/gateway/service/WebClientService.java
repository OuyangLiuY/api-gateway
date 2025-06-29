package com.citi.tts.api.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebClient服务类
 * 提供分级WebClient的使用方法，根据服务类型选择合适的WebClient
 */
@Slf4j
@Service
public class WebClientService {

    @Autowired
    @Qualifier("coreServiceWebClient")
    private WebClient coreServiceWebClient;

    @Autowired
    @Qualifier("importantServiceWebClient")
    private WebClient importantServiceWebClient;

    @Autowired
    @Qualifier("normalServiceWebClient")
    private WebClient normalServiceWebClient;

    @Autowired
    @Qualifier("nonCoreServiceWebClient")
    private WebClient nonCoreServiceWebClient;

    @Autowired
    @Qualifier("defaultWebClient")
    private WebClient defaultWebClient;

    /**
     * 服务类型枚举
     */
    public enum ServiceType {
        CORE,           // 核心服务：支付、转账等
        IMPORTANT,      // 重要服务：用户认证、账户管理等
        NORMAL,         // 普通服务：查询、统计等
        NON_CORE,       // 非核心服务：日志、监控等
        DEFAULT         // 默认服务
    }

    /**
     * 根据服务类型获取对应的WebClient
     */
    public WebClient getWebClient(ServiceType serviceType) {
        return switch (serviceType) {
            case CORE -> coreServiceWebClient;
            case IMPORTANT -> importantServiceWebClient;
            case NORMAL -> normalServiceWebClient;
            case NON_CORE -> nonCoreServiceWebClient;
            case DEFAULT -> defaultWebClient;
        };
    }

    /**
     * 根据服务名称自动判断服务类型
     */
    public WebClient getWebClientByServiceName(String serviceName) {
        ServiceType serviceType = determineServiceType(serviceName);
        log.debug("Service: {} -> Type: {} -> WebClient: {}", 
                serviceName, serviceType, serviceType.name().toLowerCase() + "WebClient");
        return getWebClient(serviceType);
    }

    /**
     * 根据API路径自动判断服务类型
     */
    public WebClient getWebClientByPath(String path) {
        ServiceType serviceType = determineServiceTypeByPath(path);
        log.debug("Path: {} -> Type: {} -> WebClient: {}", 
                path, serviceType, serviceType.name().toLowerCase() + "WebClient");
        return getWebClient(serviceType);
    }

    /**
     * 核心服务调用 - 使用核心服务WebClient
     */
    public <T> Mono<T> callCoreService(String url, Class<T> responseType) {
        return coreServiceWebClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(responseType)
                .doOnSuccess(result -> log.debug("Core service call successful: {}", url))
                .doOnError(error -> log.error("Core service call failed: {}", url, error));
    }

    /**
     * 重要服务调用 - 使用重要服务WebClient
     */
    public <T> Mono<T> callImportantService(String url, Class<T> responseType) {
        return importantServiceWebClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(responseType)
                .doOnSuccess(result -> log.debug("Important service call successful: {}", url))
                .doOnError(error -> log.error("Important service call failed: {}", url, error));
    }

    /**
     * 普通服务调用 - 使用普通服务WebClient
     */
    public <T> Mono<T> callNormalService(String url, Class<T> responseType) {
        return normalServiceWebClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(responseType)
                .doOnSuccess(result -> log.debug("Normal service call successful: {}", url))
                .doOnError(error -> log.error("Normal service call failed: {}", url, error));
    }

    /**
     * 非核心服务调用 - 使用非核心服务WebClient
     */
    public <T> Mono<T> callNonCoreService(String url, Class<T> responseType) {
        return nonCoreServiceWebClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(responseType)
                .doOnSuccess(result -> log.debug("Non-core service call successful: {}", url))
                .doOnError(error -> log.error("Non-core service call failed: {}", url, error));
    }

    /**
     * 通用服务调用 - 根据URL自动选择WebClient
     */
    public <T> Mono<T> callService(String url, Class<T> responseType) {
        WebClient webClient = getWebClientByPath(url);
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(responseType)
                .doOnSuccess(result -> log.debug("Service call successful: {}", url))
                .doOnError(error -> log.error("Service call failed: {}", url, error));
    }

    /**
     * POST请求调用
     */
    public <T, R> Mono<R> postService(String url, T requestBody, Class<R> responseType) {
        WebClient webClient = getWebClientByPath(url);
        return webClient.post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(responseType)
                .doOnSuccess(result -> log.debug("POST service call successful: {}", url))
                .doOnError(error -> log.error("POST service call failed: {}", url, error));
    }

    /**
     * 根据服务名称判断服务类型
     */
    private ServiceType determineServiceType(String serviceName) {
        if (serviceName == null) {
            return ServiceType.DEFAULT;
        }

        String lowerServiceName = serviceName.toLowerCase();
        
        // 核心服务
        if (lowerServiceName.contains("payment") || 
            lowerServiceName.contains("transfer") || 
            lowerServiceName.contains("withdraw") ||
            lowerServiceName.contains("deposit") ||
            lowerServiceName.contains("account")) {
            return ServiceType.CORE;
        }
        
        // 重要服务
        if (lowerServiceName.contains("user") || 
            lowerServiceName.contains("auth") || 
            lowerServiceName.contains("security") ||
            lowerServiceName.contains("notification")) {
            return ServiceType.IMPORTANT;
        }
        
        // 普通服务
        if (lowerServiceName.contains("query") || 
            lowerServiceName.contains("report") || 
            lowerServiceName.contains("history") ||
            lowerServiceName.contains("balance")) {
            return ServiceType.NORMAL;
        }
        
        // 非核心服务
        if (lowerServiceName.contains("log") || 
            lowerServiceName.contains("monitor") || 
            lowerServiceName.contains("statistics") ||
            lowerServiceName.contains("analytics")) {
            return ServiceType.NON_CORE;
        }
        
        return ServiceType.DEFAULT;
    }

    /**
     * 根据API路径判断服务类型
     */
    private ServiceType determineServiceTypeByPath(String path) {
        if (path == null) {
            return ServiceType.DEFAULT;
        }

        String lowerPath = path.toLowerCase();
        
        // 核心服务路径
        if (lowerPath.contains("/payment/") || 
            lowerPath.contains("/transfer/") || 
            lowerPath.contains("/withdraw/") ||
            lowerPath.contains("/deposit/") ||
            lowerPath.contains("/account/")) {
            return ServiceType.CORE;
        }
        
        // 重要服务路径
        if (lowerPath.contains("/user/") || 
            lowerPath.contains("/auth/") || 
            lowerPath.contains("/security/") ||
            lowerPath.contains("/notification/")) {
            return ServiceType.IMPORTANT;
        }
        
        // 普通服务路径
        if (lowerPath.contains("/query/") || 
            lowerPath.contains("/report/") || 
            lowerPath.contains("/history/") ||
            lowerPath.contains("/balance/")) {
            return ServiceType.NORMAL;
        }
        
        // 非核心服务路径
        if (lowerPath.contains("/log/") || 
            lowerPath.contains("/monitor/") || 
            lowerPath.contains("/statistics/") ||
            lowerPath.contains("/analytics/")) {
            return ServiceType.NON_CORE;
        }
        
        return ServiceType.DEFAULT;
    }

    /**
     * 获取WebClient统计信息
     */
    public Map<String, Object> getWebClientStats() {
        return Map.of(
            "coreServiceWebClient", "Active",
            "importantServiceWebClient", "Active", 
            "normalServiceWebClient", "Active",
            "nonCoreServiceWebClient", "Active",
            "defaultWebClient", "Active",
            "timestamp", System.currentTimeMillis()
        );
    }
} 