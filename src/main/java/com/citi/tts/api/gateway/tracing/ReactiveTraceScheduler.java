package com.citi.tts.api.gateway.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 响应式追踪调度器
 * 使用Project Reactor实现响应式调度，提供更好的背压处理和资源管理
 */
@Slf4j
@Component
public class ReactiveTraceScheduler {

    @Autowired
    private TraceManager traceManager;

    @Autowired
    private TraceReporter traceReporter;

    @Value("${tracing.reactive.cleanup.interval:300000}")
    private long cleanupIntervalMs = 300000; // 5分钟

    @Value("${tracing.reactive.report.interval:10000}")
    private long reportIntervalMs = 10000; // 10秒

    @Value("${tracing.reactive.stats.interval:60000}")
    private long statsIntervalMs = 60000; // 1分钟

    @Value("${tracing.reactive.health.interval:30000}")
    private long healthIntervalMs = 30000; // 30秒

    @Value("${tracing.reactive.cleanup.max-age:600000}")
    private long maxAgeMs = 600000; // 10分钟

    @Value("${tracing.reactive.buffer-size:1000}")
    private int bufferSize = 1000;

    @Value("${tracing.reactive.backpressure.strategy:BUFFER}")
    private String backpressureStrategy = "BUFFER"; // BUFFER, DROP, LATEST

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Disposable cleanupDisposable;
    private Disposable reportDisposable;
    private Disposable statsDisposable;
    private Disposable healthDisposable;

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting reactive trace scheduler");
            
            // 启动清理任务
            startCleanupTask();
            
            // 启动报告任务
            startReportTask();
            
            // 启动统计任务
            startStatsTask();
            
            // 启动健康检查任务
            startHealthTask();
        }
    }

    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping reactive trace scheduler");
            
            if (cleanupDisposable != null) {
                cleanupDisposable.dispose();
            }
            if (reportDisposable != null) {
                reportDisposable.dispose();
            }
            if (statsDisposable != null) {
                statsDisposable.dispose();
            }
            if (healthDisposable != null) {
                healthDisposable.dispose();
            }
        }
    }

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        cleanupDisposable = Flux.interval(Duration.ofMillis(cleanupIntervalMs))
                .onBackpressureBuffer(bufferSize)
                .flatMap(tick -> {
                    if (!running.get()) {
                        return Mono.empty();
                    }
                    
                    return Mono.fromCallable(() -> {
                        long startTime = System.currentTimeMillis();
                        int beforeCount = traceManager.getActiveTraceCount();
                        
                        traceManager.cleanupExpiredTraces(maxAgeMs);
                        
                        int afterCount = traceManager.getActiveTraceCount();
                        int cleanedCount = beforeCount - afterCount;
                        
                        if (cleanedCount > 0) {
                            log.info("Reactive trace cleanup completed - Cleaned: {}, Remaining: {}, Duration: {}ms", 
                                    cleanedCount, afterCount, System.currentTimeMillis() - startTime);
                        } else {
                            log.debug("Reactive trace cleanup completed - No traces cleaned, Duration: {}ms", 
                                    System.currentTimeMillis() - startTime);
                        }
                        
                        return null;
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(error -> {
                        log.error("Error during reactive trace cleanup", error);
                        return Mono.empty();
                    });
                })
                .subscribeOn(Schedulers.single())
                .subscribe(
                    v -> log.debug("Cleanup task executed"),
                    error -> log.error("Cleanup task failed", error)
                );
    }

    /**
     * 启动报告任务
     */
    private void startReportTask() {
        reportDisposable = Flux.interval(Duration.ofMillis(reportIntervalMs))
                .onBackpressureBuffer(bufferSize)
                .flatMap(tick -> {
                    if (!running.get()) {
                        return Mono.empty();
                    }
                    
                    return traceReporter.reportBatch()
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(error -> {
                                log.error("Error during reactive trace report", error);
                                return Mono.empty();
                            });
                })
                .subscribeOn(Schedulers.single())
                .subscribe(
                    v -> log.debug("Report task executed"),
                    error -> log.error("Report task failed", error)
                );
    }

    /**
     * 启动统计任务
     */
    private void startStatsTask() {
        statsDisposable = Flux.interval(Duration.ofMillis(statsIntervalMs))
                .onBackpressureBuffer(bufferSize)
                .flatMap(tick -> {
                    if (!running.get()) {
                        return Mono.empty();
                    }
                    
                    return Mono.fromCallable(() -> {
                        TraceManager.TraceStatistics managerStats = traceManager.getStatistics();
                        TraceReporter.TraceReporterStats reporterStats = traceReporter.getStats();
                        
                        log.info("Reactive Trace Statistics - Manager: Total={}, Sampled={}, Active={}, SamplingRate={}, " +
                                "Reporter: Enabled={}, QueueSize={}, Reported={}, Failed={}", 
                                managerStats.getTotalTraces(),
                                managerStats.getSampledTraces(),
                                managerStats.getActiveTraces(),
                                managerStats.getSamplingRate(),
                                reporterStats.isEnabled(),
                                reporterStats.getQueueSize(),
                                reporterStats.getReportedTraces(),
                                reporterStats.getFailedReports());
                        
                        return null;
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(error -> {
                        log.error("Error logging reactive trace statistics", error);
                        return Mono.empty();
                    });
                })
                .subscribeOn(Schedulers.single())
                .subscribe(
                    v -> log.debug("Stats task executed"),
                    error -> log.error("Stats task failed", error)
                );
    }

    /**
     * 启动健康检查任务
     */
    private void startHealthTask() {
        healthDisposable = Flux.interval(Duration.ofMillis(healthIntervalMs))
                .onBackpressureBuffer(bufferSize)
                .flatMap(tick -> {
                    if (!running.get()) {
                        return Mono.empty();
                    }
                    
                    return Mono.fromCallable(() -> {
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
                        
                        return null;
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(error -> {
                        log.error("Error checking reactive trace health", error);
                        return Mono.empty();
                    });
                })
                .subscribeOn(Schedulers.single())
                .subscribe(
                    v -> log.debug("Health check task executed"),
                    error -> log.error("Health check task failed", error)
                );
    }

    /**
     * 手动触发清理
     */
    public Mono<Void> triggerCleanup() {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();
            int beforeCount = traceManager.getActiveTraceCount();
            
            traceManager.cleanupExpiredTraces(maxAgeMs);
            
            int afterCount = traceManager.getActiveTraceCount();
            int cleanedCount = beforeCount - afterCount;
            
            log.info("Manual reactive trace cleanup completed - Cleaned: {}, Remaining: {}, Duration: {}ms", 
                    cleanedCount, afterCount, System.currentTimeMillis() - startTime);
            
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }

    /**
     * 手动触发报告
     */
    public Mono<Void> triggerReport() {
        return traceReporter.reportBatch()
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取调度器状态
     */
    public Mono<ReactiveSchedulerStats> getStats() {
        return Mono.fromCallable(() -> {
            return ReactiveSchedulerStats.builder()
                    .running(running.get())
                    .cleanupIntervalMs(cleanupIntervalMs)
                    .reportIntervalMs(reportIntervalMs)
                    .statsIntervalMs(statsIntervalMs)
                    .healthIntervalMs(healthIntervalMs)
                    .maxAgeMs(maxAgeMs)
                    .bufferSize(bufferSize)
                    .backpressureStrategy(backpressureStrategy)
                    .build();
        });
    }

    /**
     * 响应式调度器统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class ReactiveSchedulerStats {
        private boolean running;
        private long cleanupIntervalMs;
        private long reportIntervalMs;
        private long statsIntervalMs;
        private long healthIntervalMs;
        private long maxAgeMs;
        private int bufferSize;
        private String backpressureStrategy;
    }
} 