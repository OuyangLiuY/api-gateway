package com.citi.tts.api.gateway.tracing;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链路追踪上下文
 * 管理请求的追踪信息，支持分布式追踪
 */
@Slf4j
@Data
public class TraceContext {
    
    // 追踪ID（全局唯一）
    private String traceId;
    
    // 跨度ID（当前服务）
    private String spanId;
    
    // 父跨度ID
    private String parentSpanId;
    
    // 请求ID
    private String requestId;
    
    // 关联ID
    private String correlationId;
    
    // 用户ID
    private String userId;
    
    // 租户ID
    private String tenantId;
    
    // 服务名称
    private String serviceName;
    
    // 操作名称
    private String operationName;
    
    // 开始时间
    private Date startTime;
    
    // 结束时间
    private Date endTime;
    
    // 状态码
    private Integer statusCode;
    
    // 错误信息
    private String errorMessage;
    
    // 标签（键值对）
    private Map<String, String> tags = new ConcurrentHashMap<>();
    
    // 事件列表
    private List<TraceEvent> events = new ArrayList<>();
    
    // 子跨度列表
    private List<TraceContext> childSpans = new ArrayList<>();
    
    // 是否采样
    private boolean sampled = true;
    
    // 追踪级别
    private TraceLevel level = TraceLevel.INFO;
    
    /**
     * 创建根追踪上下文
     */
    public static TraceContext createRoot(ServerWebExchange exchange) {
        TraceContext context = new TraceContext();
        context.setTraceId(generateTraceId());
        context.setSpanId(generateSpanId());
        context.setStartTime(new Date());
        context.setServiceName("api-gateway");
        
        // 从请求中提取信息
        ServerHttpRequest request = exchange.getRequest();
        context.setRequestId(extractRequestId(request));
        context.setCorrelationId(extractCorrelationId(request));
        context.setUserId(extractUserId(request));
        context.setTenantId(extractTenantId(request));
        context.setOperationName(request.getMethod().name() + " " + request.getPath().value());
        
        // 添加基本标签
        context.addTag("http.method", request.getMethod().name());
        context.addTag("http.path", request.getPath().value());
        context.addTag("http.host", request.getURI().getHost());
        context.addTag("http.port", String.valueOf(request.getURI().getPort()));
        context.addTag("client.ip", extractClientIp(request));
        context.addTag("user.agent", request.getHeaders().getFirst("User-Agent"));
        
        log.debug("Created root trace context: {}", context.getTraceId());
        return context;
    }
    
    /**
     * 创建子跨度
     */
    public TraceContext createChildSpan(String operationName) {
        TraceContext child = new TraceContext();
        child.setTraceId(this.traceId);
        child.setSpanId(generateSpanId());
        child.setParentSpanId(this.spanId);
        child.setRequestId(this.requestId);
        child.setCorrelationId(this.correlationId);
        child.setUserId(this.userId);
        child.setTenantId(this.tenantId);
        child.setServiceName(this.serviceName);
        child.setOperationName(operationName);
        child.setStartTime(new Date());
        child.setSampled(this.sampled);
        child.setLevel(this.level);
        
        // 复制标签
        child.getTags().putAll(this.tags);
        
        this.childSpans.add(child);
        log.debug("Created child span: {} for trace: {}", child.getSpanId(), this.traceId);
        return child;
    }
    
    /**
     * 添加标签
     */
    public void addTag(String key, String value) {
        if (key != null && value != null) {
            this.tags.put(key, value);
        }
    }
    
    /**
     * 添加事件
     */
    public void addEvent(String name, String message) {
        TraceEvent event = new TraceEvent();
        event.setName(name);
        event.setMessage(message);
        event.setTimestamp(new Date());
        this.events.add(event);
    }
    
    /**
     * 添加事件（带标签）
     */
    public void addEvent(String name, String message, Map<String, String> eventTags) {
        TraceEvent event = new TraceEvent();
        event.setName(name);
        event.setMessage(message);
        event.setTimestamp(new Date());
        event.setTags(eventTags);
        this.events.add(event);
    }
    
    /**
     * 完成追踪
     */
    public void finish(Integer statusCode, String errorMessage) {
        this.endTime = new Date();
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        
        if (statusCode != null) {
            this.addTag("http.status_code", String.valueOf(statusCode));
        }
        
        if (errorMessage != null) {
            this.addTag("error.message", errorMessage);
            this.addTag("error", "true");
        }
        
        log.debug("Finished trace: {} with status: {}", this.traceId, statusCode);
    }
    
    /**
     * 获取持续时间（毫秒）
     */
    public long getDurationMs() {
        if (startTime != null && endTime != null) {
            return endTime.getTime() - startTime.getTime();
        }
        return 0;
    }
    
    /**
     * 获取追踪头信息
     */
    public Map<String, String> getTraceHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Trace-ID", traceId);
        headers.put("X-Span-ID", spanId);
        headers.put("X-Request-ID", requestId);
        headers.put("X-Correlation-ID", correlationId);
        
        if (parentSpanId != null) {
            headers.put("X-Parent-Span-ID", parentSpanId);
        }
        
        if (userId != null) {
            headers.put("X-User-ID", userId);
        }
        
        if (tenantId != null) {
            headers.put("X-Tenant-ID", tenantId);
        }
        
        // 添加采样信息
        headers.put("X-Sampled", String.valueOf(sampled));
        
        return headers;
    }
    
    /**
     * 生成追踪ID
     */
    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成跨度ID
     */
    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * 提取请求ID
     */
    private static String extractRequestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst("X-Request-ID");
        if (requestId == null) {
            requestId = request.getHeaders().getFirst("X-Correlation-ID");
        }
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }
    
    /**
     * 提取关联ID
     */
    private static String extractCorrelationId(ServerHttpRequest request) {
        return request.getHeaders().getFirst("X-Correlation-ID");
    }
    
    /**
     * 提取用户ID
     */
    private static String extractUserId(ServerHttpRequest request) {
        String userId = request.getHeaders().getFirst("X-User-ID");
        if (userId == null) {
            // 从Authorization头中提取
            String authorization = request.getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                userId = "jwt-user-" + authorization.substring(7).hashCode();
            }
        }
        return userId;
    }
    
    /**
     * 提取租户ID
     */
    private static String extractTenantId(ServerHttpRequest request) {
        return request.getHeaders().getFirst("X-Tenant-ID");
    }
    
    /**
     * 提取客户端IP
     */
    private static String extractClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    /**
     * 追踪级别
     */
    public enum TraceLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 追踪事件
     */
    @Data
    public static class TraceEvent {
        private String name;
        private String message;
        private Date timestamp;
        private Map<String, String> tags = new HashMap<>();
    }
} 