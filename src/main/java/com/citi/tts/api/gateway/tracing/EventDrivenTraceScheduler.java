package com.citi.tts.api.gateway.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件驱动追踪调度器
 * 基于Spring Events实现事件驱动的调度，提供更好的解耦和扩展性
 */
@Slf4j
@Component
public class EventDrivenTraceScheduler {

    @Autowired
    private TraceManager traceManager;

    @Autowired
    private TraceReporter traceReporter;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${tracing.event.cleanup.interval:300000}")
    private long cleanupIntervalMs = 300000; // 5分钟

    @Value("${tracing.event.report.interval:10000}")
    private long reportIntervalMs = 10000; // 10秒

    @Value("${tracing.event.stats.interval:60000}")
    private long statsIntervalMs = 60000; // 1分钟

    @Value("${tracing.event.health.interval:30000}")
    private long healthIntervalMs = 30000; // 30秒

    @Value("${tracing.event.cleanup.max-age:600000}")
    private long maxAgeMs = 600000; // 10分钟

    private final AtomicLong cleanupCounter = new AtomicLong(0);
    private final AtomicLong reportCounter = new AtomicLong(0);
    private final AtomicLong statsCounter = new AtomicLong(0);
    private final AtomicLong healthCounter = new AtomicLong(0);

    /**
     * 监听清理事件
     */
    @EventListener
    @Async("traceTaskExecutor")
    public void handleCleanupEvent(CleanupEvent event) {
        try {
            long startTime = System.currentTimeMillis();
            int beforeCount = traceManager.getActiveTraceCount();
            
            traceManager.cleanupExpiredTraces(maxAgeMs);
            
            int afterCount = traceManager.getActiveTraceCount();
            int cleanedCount = beforeCount - afterCount;
            
            if (cleanedCount > 0) {
                log.info("Event-driven trace cleanup completed - EventId: {}, Cleaned: {}, Remaining: {}, Duration: {}ms", 
                        event.getEventId(), cleanedCount, afterCount, System.currentTimeMillis() - startTime);
            } else {
                log.debug("Event-driven trace cleanup completed - EventId: {}, No traces cleaned, Duration: {}ms", 
                        event.getEventId(), System.currentTimeMillis() - startTime);
            }
            
            // 发布清理完成事件
            eventPublisher.publishEvent(new CleanupCompletedEvent(event.getEventId(), cleanedCount, afterCount));
            
        } catch (Exception e) {
            log.error("Error during event-driven trace cleanup", e);
            // 发布清理失败事件
            eventPublisher.publishEvent(new CleanupFailedEvent(event.getEventId(), e.getMessage()));
        }
    }

    /**
     * 监听报告事件
     */
    @EventListener
    @Async("traceTaskExecutor")
    public void handleReportEvent(ReportEvent event) {
        try {
            long startTime = System.currentTimeMillis();
            
            traceReporter.reportBatch()
                    .subscribe(
                        (Void v) -> {
                            log.debug("Event-driven trace report completed - EventId: {}, Duration: {}ms", 
                                    event.getEventId(), System.currentTimeMillis() - startTime);
                            // 发布报告完成事件
                            eventPublisher.publishEvent(new ReportCompletedEvent(event.getEventId()));
                        },
                        error -> {
                            log.error("Event-driven trace report failed - EventId: {}", event.getEventId(), error);
                            // 发布报告失败事件
                            eventPublisher.publishEvent(new ReportFailedEvent(event.getEventId(), error.getMessage()));
                        }
                    );
            
        } catch (Exception e) {
            log.error("Error during event-driven trace report", e);
            // 发布报告失败事件
            eventPublisher.publishEvent(new ReportFailedEvent(event.getEventId(), e.getMessage()));
        }
    }

    /**
     * 监听统计事件
     */
    @EventListener
    @Async("traceTaskExecutor")
    public void handleStatsEvent(StatsEvent event) {
        try {
            TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
            TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
            
            log.info("Event-driven Trace Statistics - EventId: {}, Manager: Total={}, Sampled={}, Active={}, SamplingRate={}, " +
                    "Reporter: Enabled={}, QueueSize={}, Reported={}, Failed={}", 
                    event.getEventId(),
                    managerStats.getTotalTraces(),
                    managerStats.getSampledTraces(),
                    managerStats.getActiveTraces(),
                    managerStats.getSamplingRate(),
                    reporterStats.isEnabled(),
                    reporterStats.getQueueSize(),
                    reporterStats.getReportedTraces(),
                    reporterStats.getFailedReports());
            
            // 发布统计完成事件
            eventPublisher.publishEvent(new StatsCompletedEvent(event.getEventId(), managerStats, reporterStats));
            
        } catch (Exception e) {
            log.error("Error during event-driven trace statistics", e);
            // 发布统计失败事件
            eventPublisher.publishEvent(new StatsFailedEvent(event.getEventId(), e.getMessage()));
        }
    }

    /**
     * 监听健康检查事件
     */
    @EventListener
    @Async("traceTaskExecutor")
    public void handleHealthEvent(HealthEvent event) {
        try {
            TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
            TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
            
            boolean healthy = true;
            String warningMessage = null;
            
            // 检查活跃追踪数量是否过多
            if (managerStats.getActiveTraces() > 10000) {
                log.warn("High number of active traces detected: {}", managerStats.getActiveTraces());
                healthy = false;
                warningMessage = "High active traces: " + managerStats.getActiveTraces();
            }
            
            // 检查报告队列是否过满
            if (reporterStats.getQueueSize() > 5000) {
                log.warn("High trace report queue size detected: {}", reporterStats.getQueueSize());
                healthy = false;
                warningMessage = "High queue size: " + reporterStats.getQueueSize();
            }
            
            // 检查失败报告率
            long totalReports = reporterStats.getReportedTraces() + reporterStats.getFailedReports();
            if (totalReports > 0) {
                double failureRate = (double) reporterStats.getFailedReports() / totalReports;
                if (failureRate > 0.1) { // 失败率超过10%
                    log.warn("High trace report failure rate detected: {}%", 
                            String.format("%.2f", failureRate * 100));
                    healthy = false;
                    warningMessage = "High failure rate: " + String.format("%.2f", failureRate * 100) + "%";
                }
            }
            
            // 发布健康检查完成事件
            eventPublisher.publishEvent(new HealthCompletedEvent(event.getEventId(), healthy, warningMessage));
            
        } catch (Exception e) {
            log.error("Error during event-driven trace health check", e);
            // 发布健康检查失败事件
            eventPublisher.publishEvent(new HealthFailedEvent(event.getEventId(), e.getMessage()));
        }
    }

    /**
     * 手动触发清理
     */
    public void triggerCleanup() {
        String eventId = "manual-cleanup-" + cleanupCounter.incrementAndGet();
        eventPublisher.publishEvent(new CleanupEvent(eventId));
        log.info("Manual cleanup event published: {}", eventId);
    }

    /**
     * 手动触发报告
     */
    public void triggerReport() {
        String eventId = "manual-report-" + reportCounter.incrementAndGet();
        eventPublisher.publishEvent(new ReportEvent(eventId));
        log.info("Manual report event published: {}", eventId);
    }

    /**
     * 手动触发统计
     */
    public void triggerStats() {
        String eventId = "manual-stats-" + statsCounter.incrementAndGet();
        eventPublisher.publishEvent(new StatsEvent(eventId));
        log.info("Manual stats event published: {}", eventId);
    }

    /**
     * 手动触发健康检查
     */
    public void triggerHealth() {
        String eventId = "manual-health-" + healthCounter.incrementAndGet();
        eventPublisher.publishEvent(new HealthEvent(eventId));
        log.info("Manual health event published: {}", eventId);
    }

    // 事件定义
    public static class CleanupEvent {
        private final String eventId;
        
        public CleanupEvent(String eventId) {
            this.eventId = eventId;
        }
        
        public String getEventId() {
            return eventId;
        }
    }

    public static class ReportEvent {
        private final String eventId;
        
        public ReportEvent(String eventId) {
            this.eventId = eventId;
        }
        
        public String getEventId() {
            return eventId;
        }
    }

    public static class StatsEvent {
        private final String eventId;
        
        public StatsEvent(String eventId) {
            this.eventId = eventId;
        }
        
        public String getEventId() {
            return eventId;
        }
    }

    public static class HealthEvent {
        private final String eventId;
        
        public HealthEvent(String eventId) {
            this.eventId = eventId;
        }
        
        public String getEventId() {
            return eventId;
        }
    }

    // 完成事件
    public static class CleanupCompletedEvent {
        private final String eventId;
        private final int cleanedCount;
        private final int remainingCount;
        
        public CleanupCompletedEvent(String eventId, int cleanedCount, int remainingCount) {
            this.eventId = eventId;
            this.cleanedCount = cleanedCount;
            this.remainingCount = remainingCount;
        }
        
        public String getEventId() { return eventId; }
        public int getCleanedCount() { return cleanedCount; }
        public int getRemainingCount() { return remainingCount; }
    }

    public static class ReportCompletedEvent {
        private final String eventId;
        
        public ReportCompletedEvent(String eventId) {
            this.eventId = eventId;
        }
        
        public String getEventId() { return eventId; }
    }

    public static class StatsCompletedEvent {
        private final String eventId;
        private final TraceManager.TraceStatistics managerStats;
        private final TraceReporter.TraceReporterStats reporterStats;
        
        public StatsCompletedEvent(String eventId, TraceManager.TraceStatistics managerStats, 
                                 TraceReporter.TraceReporterStats reporterStats) {
            this.eventId = eventId;
            this.managerStats = managerStats;
            this.reporterStats = reporterStats;
        }
        
        public String getEventId() { return eventId; }
        public TraceManager.TraceStatistics getManagerStats() { return managerStats; }
        public TraceReporter.TraceReporterStats getReporterStats() { return reporterStats; }
    }

    public static class HealthCompletedEvent {
        private final String eventId;
        private final boolean healthy;
        private final String warningMessage;
        
        public HealthCompletedEvent(String eventId, boolean healthy, String warningMessage) {
            this.eventId = eventId;
            this.healthy = healthy;
            this.warningMessage = warningMessage;
        }
        
        public String getEventId() { return eventId; }
        public boolean isHealthy() { return healthy; }
        public String getWarningMessage() { return warningMessage; }
    }

    // 失败事件
    public static class CleanupFailedEvent {
        private final String eventId;
        private final String errorMessage;
        
        public CleanupFailedEvent(String eventId, String errorMessage) {
            this.eventId = eventId;
            this.errorMessage = errorMessage;
        }
        
        public String getEventId() { return eventId; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class ReportFailedEvent {
        private final String eventId;
        private final String errorMessage;
        
        public ReportFailedEvent(String eventId, String errorMessage) {
            this.eventId = eventId;
            this.errorMessage = errorMessage;
        }
        
        public String getEventId() { return eventId; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class StatsFailedEvent {
        private final String eventId;
        private final String errorMessage;
        
        public StatsFailedEvent(String eventId, String errorMessage) {
            this.eventId = eventId;
            this.errorMessage = errorMessage;
        }
        
        public String getEventId() { return eventId; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class HealthFailedEvent {
        private final String eventId;
        private final String errorMessage;
        
        public HealthFailedEvent(String eventId, String errorMessage) {
            this.eventId = eventId;
            this.errorMessage = errorMessage;
        }
        
        public String getEventId() { return eventId; }
        public String getErrorMessage() { return errorMessage; }
    }
} 