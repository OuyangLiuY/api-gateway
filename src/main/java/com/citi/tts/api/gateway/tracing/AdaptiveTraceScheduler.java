package com.citi.tts.api.gateway.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 自适应追踪调度器
 * 根据系统负载、内存使用、队列状态等动态调整调度策略
 */
@Slf4j
@Component
public class AdaptiveTraceScheduler {

    @Autowired
    private TraceManager traceManager;

    @Autowired
    private TraceReporter traceReporter;

    // 基础配置
    @Value("${tracing.adaptive.base.cleanup.interval:300000}")
    private long baseCleanupIntervalMs = 300000; // 5分钟

    @Value("${tracing.adaptive.base.report.interval:10000}")
    private long baseReportIntervalMs = 10000; // 10秒

    @Value("${tracing.adaptive.base.stats.interval:60000}")
    private long baseStatsIntervalMs = 60000; // 1分钟

    @Value("${tracing.adaptive.base.health.interval:30000}")
    private long baseHealthIntervalMs = 30000; // 30秒

    // 自适应配置
    @Value("${tracing.adaptive.min.interval.multiplier:0.5}")
    private double minIntervalMultiplier = 0.5; // 最小间隔倍数

    @Value("${tracing.adaptive.max.interval.multiplier:3.0}")
    private double maxIntervalMultiplier = 3.0; // 最大间隔倍数

    @Value("${tracing.adaptive.memory.threshold:0.8}")
    private double memoryThreshold = 0.8; // 内存使用阈值

    @Value("${tracing.adaptive.queue.threshold:0.7}")
    private double queueThreshold = 0.7; // 队列使用阈值

    @Value("${tracing.adaptive.cpu.threshold:0.7}")
    private double cpuThreshold = 0.7; // CPU使用阈值

    // 当前间隔倍数
    private final AtomicReference<Double> currentMultiplier = new AtomicReference<>(1.0);
    
    // 统计信息
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong adaptiveAdjustments = new AtomicLong(0);
    private final AtomicLong lastAdjustmentTime = new AtomicLong(0);

    /**
     * 自适应清理任务
     */
    @Scheduled(fixedDelayString = "${tracing.adaptive.base.cleanup.interval:300000}")
    public void adaptiveCleanup() {
        long startTime = System.currentTimeMillis();
        
        try {
            // 检查是否需要调整间隔
            checkAndAdjustInterval();
            
            int beforeCount = traceManager.getActiveTraceCount();
            traceManager.cleanupExpiredTraces(600000); // 10分钟过期
            int afterCount = traceManager.getActiveTraceCount();
            int cleanedCount = beforeCount - afterCount;
            
            totalExecutions.incrementAndGet();
            
            if (cleanedCount > 0) {
                log.info("Adaptive trace cleanup completed - Cleaned: {}, Remaining: {}, Duration: {}ms, Multiplier: {}", 
                        cleanedCount, afterCount, System.currentTimeMillis() - startTime, currentMultiplier.get());
            } else {
                log.debug("Adaptive trace cleanup completed - No traces cleaned, Duration: {}ms, Multiplier: {}", 
                        System.currentTimeMillis() - startTime, currentMultiplier.get());
            }
            
        } catch (Exception e) {
            log.error("Error during adaptive trace cleanup", e);
        }
    }

    /**
     * 自适应报告任务
     */
    @Scheduled(fixedDelayString = "${tracing.adaptive.base.report.interval:10000}")
    public void adaptiveReport() {
        long startTime = System.currentTimeMillis();
        
        try {
            // 检查是否需要调整间隔
            checkAndAdjustInterval();
            
            traceReporter.reportBatch()
                    .subscribe(
                        (Void v) -> {
                            totalExecutions.incrementAndGet();
                            log.debug("Adaptive trace report completed in {}ms, Multiplier: {}", 
                                    System.currentTimeMillis() - startTime, currentMultiplier.get());
                        },
                        error -> {
                            log.error("Adaptive trace report failed", error);
                        }
                    );
            
        } catch (Exception e) {
            log.error("Error during adaptive trace report", e);
        }
    }

    /**
     * 自适应统计任务
     */
    @Scheduled(fixedDelayString = "${tracing.adaptive.base.stats.interval:60000}")
    public void adaptiveStats() {
        try {
            // 检查是否需要调整间隔
            checkAndAdjustInterval();
            
            TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
            TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
            
            totalExecutions.incrementAndGet();
            
            log.info("Adaptive Trace Statistics - Manager: Total={}, Sampled={}, Active={}, SamplingRate={}, " +
                    "Reporter: Enabled={}, QueueSize={}, Reported={}, Failed={}, Multiplier: {}", 
                    managerStats.getTotalTraces(),
                    managerStats.getSampledTraces(),
                    managerStats.getActiveTraces(),
                    managerStats.getSamplingRate(),
                    reporterStats.isEnabled(),
                    reporterStats.getQueueSize(),
                    reporterStats.getReportedTraces(),
                    reporterStats.getFailedReports(),
                    currentMultiplier.get());
            
        } catch (Exception e) {
            log.error("Error during adaptive trace statistics", e);
        }
    }

    /**
     * 自适应健康检查任务
     */
    @Scheduled(fixedDelayString = "${tracing.adaptive.base.health.interval:30000}")
    public void adaptiveHealth() {
        try {
            // 检查是否需要调整间隔
            checkAndAdjustInterval();
            
            TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
            TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
            
            totalExecutions.incrementAndGet();
            
            // 检查活跃追踪数量是否过多
            if (managerStats.getActiveTraces() > 10000) {
                log.warn("High number of active traces detected: {}, Multiplier: {}", 
                        managerStats.getActiveTraces(), currentMultiplier.get());
            }
            
            // 检查报告队列是否过满
            if (reporterStats.getQueueSize() > 5000) {
                log.warn("High trace report queue size detected: {}, Multiplier: {}", 
                        reporterStats.getQueueSize(), currentMultiplier.get());
            }
            
            // 检查失败报告率
            long totalReports = reporterStats.getReportedTraces() + reporterStats.getFailedReports();
            if (totalReports > 0) {
                double failureRate = (double) reporterStats.getFailedReports() / totalReports;
                if (failureRate > 0.1) { // 失败率超过10%
                    log.warn("High trace report failure rate detected: {}%, Multiplier: {}", 
                            String.format("%.2f", failureRate * 100), currentMultiplier.get());
                }
            }
            
        } catch (Exception e) {
            log.error("Error during adaptive trace health check", e);
        }
    }

    /**
     * 检查并调整间隔
     */
    private void checkAndAdjustInterval() {
        long now = System.currentTimeMillis();
        
        // 避免频繁调整，至少间隔1分钟
        if (now - lastAdjustmentTime.get() < 60000) {
            return;
        }
        
        double newMultiplier = calculateOptimalMultiplier();
        double currentMultiplierValue = currentMultiplier.get();
        
        // 如果变化超过10%，则调整
        if (Math.abs(newMultiplier - currentMultiplierValue) > 0.1) {
            if (currentMultiplier.compareAndSet(currentMultiplierValue, newMultiplier)) {
                adaptiveAdjustments.incrementAndGet();
                lastAdjustmentTime.set(now);
                
                log.info("Adaptive interval adjustment: {} -> {} ({}%)", 
                        currentMultiplierValue, newMultiplier, 
                        String.format("%.1f", (newMultiplier / currentMultiplierValue - 1) * 100));
            }
        }
    }

    /**
     * 计算最优间隔倍数
     */
    private double calculateOptimalMultiplier() {
        double multiplier = 1.0;
        
        // 基于内存使用调整
        double memoryUsage = getMemoryUsage();
        if (memoryUsage > memoryThreshold) {
            multiplier *= (1 + (memoryUsage - memoryThreshold) * 2); // 内存压力大时增加间隔
        }
        
        // 基于队列使用调整
        TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
        if (reporterStats.getQueueSize() > 0) {
            double queueUsage = (double) reporterStats.getQueueSize() / 10000; // 假设最大队列大小为10000
            if (queueUsage > queueThreshold) {
                multiplier *= (1 + (queueUsage - queueThreshold) * 1.5); // 队列压力大时增加间隔
            }
        }
        
        // 基于CPU使用调整
        double cpuUsage = getCpuUsage();
        if (cpuUsage > cpuThreshold) {
            multiplier *= (1 + (cpuUsage - cpuThreshold) * 1.5); // CPU压力大时增加间隔
        }
        
        // 基于活跃追踪数量调整
        TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
        if (managerStats.getActiveTraces() > 5000) {
            double tracePressure = (double) managerStats.getActiveTraces() / 10000;
            multiplier *= (1 + tracePressure * 0.5); // 追踪压力大时增加间隔
        }
        
        // 限制在合理范围内
        return Math.max(minIntervalMultiplier, Math.min(maxIntervalMultiplier, multiplier));
    }

    /**
     * 获取内存使用率
     */
    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return (double) usedMemory / totalMemory;
    }

    /**
     * 获取CPU使用率（简化实现）
     */
    private double getCpuUsage() {
        // 这里使用简化实现，实际项目中可以使用OSHI或其他库
        // 基于线程数和系统负载估算
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int activeThreads = Thread.activeCount();
        
        return Math.min(1.0, (double) activeThreads / (availableProcessors * 2));
    }

    /**
     * 获取当前间隔倍数
     */
    public double getCurrentMultiplier() {
        return currentMultiplier.get();
    }

    /**
     * 手动设置间隔倍数
     */
    public void setMultiplier(double multiplier) {
        double clampedMultiplier = Math.max(minIntervalMultiplier, Math.min(maxIntervalMultiplier, multiplier));
        double oldMultiplier = currentMultiplier.getAndSet(clampedMultiplier);
        
        log.info("Manual multiplier adjustment: {} -> {}", oldMultiplier, clampedMultiplier);
    }

    /**
     * 获取自适应统计信息
     */
    public AdaptiveStats getStats() {
        return AdaptiveStats.builder()
                .currentMultiplier(currentMultiplier.get())
                .totalExecutions(totalExecutions.get())
                .adaptiveAdjustments(adaptiveAdjustments.get())
                .lastAdjustmentTime(lastAdjustmentTime.get())
                .memoryUsage(getMemoryUsage())
                .cpuUsage(getCpuUsage())
                .minIntervalMultiplier(minIntervalMultiplier)
                .maxIntervalMultiplier(maxIntervalMultiplier)
                .build();
    }

    /**
     * 自适应统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class AdaptiveStats {
        private double currentMultiplier;
        private long totalExecutions;
        private long adaptiveAdjustments;
        private long lastAdjustmentTime;
        private double memoryUsage;
        private double cpuUsage;
        private double minIntervalMultiplier;
        private double maxIntervalMultiplier;
    }
} 