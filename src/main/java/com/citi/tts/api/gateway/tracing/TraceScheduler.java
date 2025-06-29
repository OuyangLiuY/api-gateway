package com.citi.tts.api.gateway.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 追踪调度器
 * 定期执行追踪相关的清理和报告任务
 */
@Slf4j
@Component
public class TraceScheduler {

    @Autowired
    private TraceManager traceManager;

    @Autowired
    private TraceReporter traceReporter;

    @Value("${tracing.cleanup.interval:300000}")
    private long cleanupIntervalMs = 300000; // 5分钟

    @Value("${tracing.report.interval:10000}")
    private long reportIntervalMs = 10000; // 10秒

    @Value("${tracing.cleanup.max-age:600000}")
    private long maxAgeMs = 600000; // 10分钟

    /**
     * 定期清理过期的追踪上下文
     */
    @Scheduled(fixedDelayString = "${tracing.cleanup.interval:300000}")
    public void cleanupExpiredTraces() {
        try {
            long startTime = System.currentTimeMillis();
            int beforeCount = traceManager.getActiveTraceCount();
            
            traceManager.cleanupExpiredTraces(maxAgeMs);
            
            int afterCount = traceManager.getActiveTraceCount();
            int cleanedCount = beforeCount - afterCount;
            
            if (cleanedCount > 0) {
                log.info("Scheduled trace cleanup completed - Cleaned: {}, Remaining: {}, Duration: {}ms", 
                        cleanedCount, afterCount, System.currentTimeMillis() - startTime);
            } else {
                log.debug("Scheduled trace cleanup completed - No traces cleaned, Duration: {}ms", 
                        System.currentTimeMillis() - startTime);
            }
        } catch (Exception e) {
            log.error("Error during scheduled trace cleanup", e);
        }
    }

    /**
     * 定期报告追踪数据
     */
    @Scheduled(fixedDelayString = "${tracing.report.interval:10000}")
    public void reportTraces() {
        try {
            long startTime = System.currentTimeMillis();
            
            traceReporter.reportBatch()
                    .subscribe(
                        (Void v) -> log.debug("Scheduled trace report completed in {}ms", 
                                System.currentTimeMillis() - startTime),
                        error -> log.error("Error during scheduled trace report", error)
                    );
        } catch (Exception e) {
            log.error("Error during scheduled trace report", e);
        }
    }

    /**
     * 定期记录追踪统计信息
     */
    @Scheduled(fixedDelayString = "${tracing.stats.interval:60000}")
    public void logTraceStats() {
        try {
            TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
            TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
            
            log.info("Trace Statistics - Manager: Total={}, Sampled={}, Active={}, SamplingRate={}, " +
                    "Reporter: Enabled={}, QueueSize={}, Reported={}, Failed={}", 
                    managerStats.getTotalTraces(),
                    managerStats.getSampledTraces(),
                    managerStats.getActiveTraces(),
                    managerStats.getSamplingRate(),
                    reporterStats.isEnabled(),
                    reporterStats.getQueueSize(),
                    reporterStats.getReportedTraces(),
                    reporterStats.getFailedReports());
        } catch (Exception e) {
            log.error("Error logging trace statistics", e);
        }
    }

    /**
     * 定期检查追踪系统健康状态
     */
    @Scheduled(fixedDelayString = "${tracing.health.interval:30000}")
    public void checkTraceHealth() {
        try {
            TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
            TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
            
            // 检查活跃追踪数量是否过多
            if (managerStats.getActiveTraces() > 10000) {
                log.warn("High number of active traces detected: {}", managerStats.getActiveTraces());
            }
            
            // 检查报告队列是否过满
            if (reporterStats.getQueueSize() > 5000) {
                log.warn("High trace report queue size detected: {}", reporterStats.getQueueSize());
            }
            
            // 检查失败报告率
            long totalReports = reporterStats.getReportedTraces() + reporterStats.getFailedReports();
            if (totalReports > 0) {
                double failureRate = (double) reporterStats.getFailedReports() / totalReports;
                if (failureRate > 0.1) { // 失败率超过10%
                    log.warn("High trace report failure rate detected: {}%", 
                            String.format("%.2f", failureRate * 100));
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking trace health", e);
        }
    }
} 