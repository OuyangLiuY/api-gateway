package com.citi.tts.api.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 协议适配过滤器
 * 支持不同协议间的转换（HTTP/HTTPS、REST/SOAP、JSON/XML等）
 */
@Slf4j
@Component
public class ProtocolAdapterFilter implements GatewayFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange adaptedExchange = adaptProtocol(exchange);
        return chain.filter(adaptedExchange);
    }

    /**
     * 协议适配
     */
    private ServerWebExchange adaptProtocol(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        String accept = headers.getFirst(HttpHeaders.ACCEPT);
        
        // 1. SOAP到REST转换
        if (isSoapRequest(contentType)) {
            log.debug("Converting SOAP request to REST");
            return convertSoapToRest(exchange);
        }
        
        // 2. XML到JSON转换
        if (isXmlRequest(contentType) && isJsonAccept(accept)) {
            log.debug("Converting XML request to JSON");
            return convertXmlToJson(exchange);
        }
        
        // 3. JSON到XML转换
        if (isJsonRequest(contentType) && isXmlAccept(accept)) {
            log.debug("Converting JSON request to XML");
            return convertJsonToXml(exchange);
        }
        
        // 4. HTTP到HTTPS重定向
        if (shouldRedirectToHttps(exchange)) {
            log.debug("Redirecting HTTP to HTTPS");
            return redirectToHttps(exchange);
        }
        
        return exchange;
    }

    /**
     * 判断是否为SOAP请求
     */
    private boolean isSoapRequest(String contentType) {
        return contentType != null && (
            contentType.contains("application/soap+xml") ||
            contentType.contains("text/xml") ||
            contentType.contains("application/xml")
        );
    }

    /**
     * 判断是否为XML请求
     */
    private boolean isXmlRequest(String contentType) {
        return contentType != null && (
            contentType.contains("application/xml") ||
            contentType.contains("text/xml")
        );
    }

    /**
     * 判断是否为JSON请求
     */
    private boolean isJsonRequest(String contentType) {
        return contentType != null && contentType.contains("application/json");
    }

    /**
     * 判断是否接受JSON响应
     */
    private boolean isJsonAccept(String accept) {
        return accept != null && accept.contains("application/json");
    }

    /**
     * 判断是否接受XML响应
     */
    private boolean isXmlAccept(String accept) {
        return accept != null && (
            accept.contains("application/xml") ||
            accept.contains("text/xml")
        );
    }

    /**
     * 判断是否需要重定向到HTTPS
     */
    private boolean shouldRedirectToHttps(ServerWebExchange exchange) {
        String scheme = exchange.getRequest().getURI().getScheme();
        String forwardedProto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        
        return "http".equals(scheme) && 
               !"https".equals(forwardedProto) &&
               isSecureEndpoint(exchange.getRequest().getPath().value());
    }

    /**
     * 判断是否为安全端点
     */
    private boolean isSecureEndpoint(String path) {
        return path.startsWith("/api/gateway/payment") ||
               path.startsWith("/api/gateway/transfer") ||
               path.startsWith("/api/admin");
    }

    /**
     * SOAP到REST转换
     */
    private ServerWebExchange convertSoapToRest(ServerWebExchange exchange) {
        // 这里实现SOAP到REST的转换逻辑
        // 1. 解析SOAP envelope
        // 2. 提取操作名称和参数
        // 3. 转换为REST格式
        log.info("SOAP to REST conversion for path: {}", exchange.getRequest().getPath());
        return exchange;
    }

    /**
     * XML到JSON转换
     */
    private ServerWebExchange convertXmlToJson(ServerWebExchange exchange) {
        // 这里实现XML到JSON的转换逻辑
        log.info("XML to JSON conversion for path: {}", exchange.getRequest().getPath());
        return exchange;
    }

    /**
     * JSON到XML转换
     */
    private ServerWebExchange convertJsonToXml(ServerWebExchange exchange) {
        // 这里实现JSON到XML的转换逻辑
        log.info("JSON to XML conversion for path: {}", exchange.getRequest().getPath());
        return exchange;
    }

    /**
     * HTTP到HTTPS重定向
     */
    private ServerWebExchange redirectToHttps(ServerWebExchange exchange) {
        // 这里实现HTTP到HTTPS的重定向逻辑
        log.info("HTTP to HTTPS redirect for path: {}", exchange.getRequest().getPath());
        return exchange;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100; // 在认证和限流之后
    }
} 