package com.citi.tts.api.gateway.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 追踪报告器
 * 将追踪数据异步发送到外部系统（如Jaeger、Zipkin等）
 */
@Slf4j
@Component
public class TraceReporter {

    @Value("${tracing.reporter.enabled:false}")
    private boolean enabled = false;

    @Value("${tracing.reporter.endpoint:}")
    private String endpoint;

    @Value("${tracing.reporter.batch-size:100}")
    private int batchSize = 100;

    @Value("${tracing.reporter.flush-interval:5000}")
    private long flushIntervalMs = 5000;

    @Value("${tracing.reporter.timeout:3000}")
    private long timeoutMs = 3000;

    @Autowired
    @Qualifier("defaultWebClient")
    private WebClient webClient;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final BlockingQueue<TraceContext> traceQueue = new LinkedBlockingQueue<>(10000);
    private final AtomicLong reportedTraces = new AtomicLong(0);
    private final AtomicLong failedReports = new AtomicLong(0);

    /**
     * 报告追踪数据
     */
    public void report(TraceContext traceContext) {
        if (!enabled || !traceContext.isSampled()) {
            return;
        }

        try {
            boolean offered = traceQueue.offer(traceContext);
            if (!offered) {
                log.warn("Trace queue is full, dropping trace: {}", traceContext.getTraceId());
                failedReports.incrementAndGet();
            }
        } catch (Exception e) {
            log.error("Failed to queue trace for reporting: {}", traceContext.getTraceId(), e);
            failedReports.incrementAndGet();
        }
    }

    /**
     * 批量报告追踪数据
     */
    public Mono<Void> reportBatch() {
        if (!enabled || traceQueue.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            // 收集批次数据
            TraceContext[] batch = new TraceContext[Math.min(batchSize, traceQueue.size())];
            int count = 0;
            
            for (int i = 0; i < batch.length && !traceQueue.isEmpty(); i++) {
                TraceContext trace = traceQueue.poll();
                if (trace != null) {
                    batch[i] = trace;
                    count++;
                }
            }

            if (count == 0) {
                return null;
            }

            // 转换为报告格式
            TraceReport report = createTraceReport(batch, count);
            
            log.debug("Reporting batch of {} traces", count);
            return report;
        })
        .flatMap(report -> {
            if (report == null) {
                return Mono.empty();
            }
            
            return sendReport(report)
                    .doOnSuccess(v -> reportedTraces.addAndGet(report.getTraces().length))
                    .doOnError(e -> {
                        log.error("Failed to send trace report", e);
                        failedReports.incrementAndGet();
                    });
        });
    }

    /**
     * 发送报告到外部系统
     */
    private Mono<Void> sendReport(TraceReport report) {
        if (endpoint == null || endpoint.isEmpty()) {
            log.warn("Tracing endpoint not configured, skipping report");
            return Mono.empty();
        }

        return webClient.post()
                .uri(endpoint)
                .bodyValue(report)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnSuccess(v -> log.debug("Trace report sent successfully"))
                .doOnError(e -> log.error("Failed to send trace report to {}", endpoint, e));
    }

    /**
     * 创建追踪报告
     */
    private TraceReport createTraceReport(TraceContext[] traces, int count) {
        TraceReport report = new TraceReport();
        report.setServiceName("api-gateway");
        report.setTimestamp(System.currentTimeMillis());
        report.setTraceCount(count);
        
        TraceReport.TraceData[] traceData = new TraceReport.TraceData[count];
        for (int i = 0; i < count; i++) {
            traceData[i] = convertToTraceData(traces[i]);
        }
        report.setTraces(traceData);
        
        return report;
    }

    /**
     * 转换追踪上下文为报告数据
     */
    private TraceReport.TraceData convertToTraceData(TraceContext traceContext) {
        TraceReport.TraceData data = new TraceReport.TraceData();
        data.setTraceId(traceContext.getTraceId());
        data.setSpanId(traceContext.getSpanId());
        data.setParentSpanId(traceContext.getParentSpanId());
        data.setRequestId(traceContext.getRequestId());
        data.setCorrelationId(traceContext.getCorrelationId());
        data.setUserId(traceContext.getUserId());
        data.setTenantId(traceContext.getTenantId());
        data.setServiceName(traceContext.getServiceName());
        data.setOperationName(traceContext.getOperationName());
        data.setStartTime(traceContext.getStartTime());
        data.setEndTime(traceContext.getEndTime());
        data.setDurationMs(traceContext.getDurationMs());
        data.setStatusCode(traceContext.getStatusCode());
        data.setErrorMessage(traceContext.getErrorMessage());
        data.setTags(traceContext.getTags());
        data.setEvents(traceContext.getEvents());
        data.setSampled(traceContext.isSampled());
        
        return data;
    }

    /**
     * 获取统计信息
     */
    public TraceReporterStats getStats() {
        return TraceReporterStats.builder()
                .enabled(enabled)
                .endpoint(endpoint)
                .queueSize(traceQueue.size())
                .reportedTraces(reportedTraces.get())
                .failedReports(failedReports.get())
                .batchSize(batchSize)
                .flushIntervalMs(flushIntervalMs)
                .timeoutMs(timeoutMs)
                .build();
    }

    /**
     * 手动刷新报告队列
     */
    public Mono<Void> flush() {
        return reportBatch();
    }

    /**
     * 追踪报告
     */
    @lombok.Data
    public static class TraceReport {
        private String serviceName;
        private long timestamp;
        private int traceCount;
        private TraceData[] traces;
        
        @lombok.Data
        public static class TraceData {
            private String traceId;
            private String spanId;
            private String parentSpanId;
            private String requestId;
            private String correlationId;
            private String userId;
            private String tenantId;
            private String serviceName;
            private String operationName;
            private Date startTime;
            private Date endTime;
            private long durationMs;
            private Integer statusCode;
            private String errorMessage;
            private java.util.Map<String, String> tags;
            private java.util.List<TraceContext.TraceEvent> events;
            private boolean sampled;
        }
    }

    /**
     * 追踪报告器统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class TraceReporterStats {
        private boolean enabled;
        private String endpoint;
        private int queueSize;
        private long reportedTraces;
        private long failedReports;
        private int batchSize;
        private long flushIntervalMs;
        private long timeoutMs;
    }
} 