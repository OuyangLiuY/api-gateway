package com.citi.tts.api.gateway.scheduler;

import com.citi.tts.api.gateway.metrics.QPSMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * QPS定时清理任务
 * 定期清理过期的QPS统计数据
 */
@Slf4j
@Component
public class QPSCleanupScheduler {

    @Autowired
    private QPSMetrics qpsMetrics;

    /**
     * 每分钟清理一次过期的QPS数据
     */
    @Scheduled(fixedRate = 60000) // 60秒
    public void cleanupQPSData() {
        try {
            log.debug("Starting scheduled QPS data cleanup...");
            qpsMetrics.cleanup();
            log.debug("Scheduled QPS data cleanup completed");
        } catch (Exception e) {
            log.error("Error during scheduled QPS data cleanup", e);
        }
    }

    /**
     * 每5分钟输出一次QPS统计摘要
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void logQPSSummary() {
        try {
            QPSMetrics.QPSStatistics statistics = qpsMetrics.getQPSStatistics();
            
            log.info("=== QPS Summary Report ===");
            log.info("Global QPS: {}", statistics.getGlobalQPS());
            log.info("Active APIs: {}", statistics.getApiQPS().size());
            log.info("Active IPs: {}", statistics.getIpQPS().size());
            log.info("Active Users: {}", statistics.getUserQPS().size());
            log.info("Active Priorities: {}", statistics.getPriorityQPS().size());
            
            // 输出前5个最活跃的API
            statistics.getApiQPS().entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                    .limit(5)
                    .forEach(entry -> 
                        log.info("Top API - {}: {} QPS", entry.getKey(), entry.getValue()));
            
            // 输出前5个最活跃的IP
            statistics.getIpQPS().entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                    .limit(5)
                    .forEach(entry -> 
                        log.info("Top IP - {}: {} QPS", entry.getKey(), entry.getValue()));
            
            log.info("=== End QPS Summary Report ===");
        } catch (Exception e) {
            log.error("Error during QPS summary logging", e);
        }
    }
} 