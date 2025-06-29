package com.citi.tts.api.gateway.config;

import com.citi.tts.api.gateway.release.ReleaseValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 发布验证配置
 * 配置自动步进调度器和相关参数
 */
@Slf4j
@Configuration
@EnableScheduling
public class ReleaseValidationConfig {

    @Autowired
    private ReleaseValidationService releaseValidationService;

    /**
     * 自动步进流量调度器
     * 每5分钟执行一次，用于灰度发布的自动流量步进
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void autoStepTrafficScheduler() {
        try {
            log.debug("Executing auto step traffic scheduler");
            releaseValidationService.autoStepTraffic();
        } catch (Exception e) {
            log.error("Error in auto step traffic scheduler", e);
        }
    }

    /**
     * 发布策略健康检查调度器
     * 每10分钟执行一次，检查发布策略的健康状态
     */
    @Scheduled(fixedRate = 600000) // 10分钟
    public void releaseStrategyHealthCheck() {
        try {
            log.debug("Executing release strategy health check");
            
            // 检查所有运行中的策略
            for (ReleaseValidationService.ReleaseStrategy strategy : releaseValidationService.getAllReleaseStrategies()) {
                if (strategy.getStatus() == ReleaseValidationService.ReleaseStatus.RUNNING) {
                    checkStrategyHealth(strategy);
                }
            }
        } catch (Exception e) {
            log.error("Error in release strategy health check", e);
        }
    }

    /**
     * 检查单个策略的健康状态
     */
    private void checkStrategyHealth(ReleaseValidationService.ReleaseStrategy strategy) {
        try {
            ReleaseValidationService.ReleaseStats stats = releaseValidationService.getReleaseStats(strategy.getId());
            if (stats == null) {
                return;
            }

            // 检查新版本错误率
            if (stats.getNewVersionRequests() > 0 && stats.getNewVersionErrorRate() > 0.1) {
                log.warn("High error rate detected for strategy {}: {}%", 
                        strategy.getId(), stats.getNewVersionErrorRate() * 100);
                
                // 如果错误率过高，可以考虑自动回滚
                if (stats.getNewVersionErrorRate() > 0.2) {
                    log.error("Critical error rate detected for strategy {}, considering auto rollback", 
                            strategy.getId());
                    // releaseValidationService.rollbackReleaseStrategy(strategy.getId());
                }
            }

            // 检查响应时间
            if (stats.getNewVersionAvgResponseTime() > 5000) { // 5秒
                log.warn("High response time detected for strategy {}: {}ms", 
                        strategy.getId(), stats.getNewVersionAvgResponseTime());
            }

        } catch (Exception e) {
            log.error("Error checking health for strategy: {}", strategy.getId(), e);
        }
    }

    /**
     * 发布策略清理调度器
     * 每小时执行一次，清理已完成的策略
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void cleanupCompletedStrategies() {
        try {
            log.debug("Executing cleanup completed strategies");
            
            // 这里可以实现清理逻辑，比如删除已完成的策略
            // 目前策略会保留在内存中，生产环境可以考虑持久化存储
            
        } catch (Exception e) {
            log.error("Error in cleanup completed strategies", e);
        }
    }
} 