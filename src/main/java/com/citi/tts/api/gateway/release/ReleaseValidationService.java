package com.citi.tts.api.gateway.release;

import com.citi.tts.api.gateway.routes.DynamicRouteService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 发布验证服务
 * 支持A/B测试、灰度发布、金丝雀发布等功能
 */
@Slf4j
@Service
public class ReleaseValidationService {

    @Autowired
    private DynamicRouteService dynamicRouteService;

    // 发布策略缓存
    private final Map<String, ReleaseStrategy> strategyCache = new ConcurrentHashMap<>();
    
    // 发布统计信息
    private final Map<String, ReleaseStats> statsCache = new ConcurrentHashMap<>();
    
    // 发布版本号
    private final AtomicLong releaseVersion = new AtomicLong(1);

    /**
     * 发布策略类型
     */
    public enum StrategyType {
        A_B_TEST,      // A/B测试
        GRAY_RELEASE,  // 灰度发布
        CANARY_RELEASE, // 金丝雀发布
        BLUE_GREEN,    // 蓝绿发布
        ROLLING_UPDATE // 滚动更新
    }

    /**
     * 发布策略
     */
    @Data
    public static class ReleaseStrategy {
        private String id;                    // 策略ID
        private String name;                  // 策略名称
        private String description;           // 策略描述
        private StrategyType type;            // 策略类型
        private String serviceName;           // 服务名称
        private String baseRouteId;           // 基础路由ID
        private String newRouteId;            // 新版本路由ID
        private String fallbackRouteId;       // 回滚路由ID
        private LocalDateTime startTime;      // 开始时间
        private LocalDateTime endTime;        // 结束时间
        private boolean enabled;              // 是否启用
        private Map<String, Object> config;   // 策略配置
        private ReleaseStatus status;         // 发布状态
        private int currentTrafficPercent;    // 当前流量百分比
        private int targetTrafficPercent;     // 目标流量百分比
        private int stepTrafficPercent;       // 步进流量百分比
        private int stepIntervalSeconds;      // 步进间隔（秒）
        private List<String> targetUsers;     // 目标用户列表
        private List<String> targetIps;       // 目标IP列表
        private Map<String, Object> criteria; // 分流条件
    }

    /**
     * 发布状态
     */
    public enum ReleaseStatus {
        PENDING,    // 待发布
        RUNNING,    // 运行中
        PAUSED,     // 暂停
        COMPLETED,  // 完成
        ROLLBACK,   // 回滚
        FAILED      // 失败
    }

    /**
     * 发布统计信息
     */
    @Data
    public static class ReleaseStats {
        private String strategyId;            // 策略ID
        private long totalRequests;           // 总请求数
        private long newVersionRequests;      // 新版本请求数
        private long oldVersionRequests;      // 旧版本请求数
        private long newVersionSuccess;       // 新版本成功数
        private long oldVersionSuccess;       // 旧版本成功数
        private long newVersionErrors;        // 新版本错误数
        private long oldVersionErrors;        // 旧版本错误数
        private double newVersionAvgResponseTime; // 新版本平均响应时间
        private double oldVersionAvgResponseTime; // 旧版本平均响应时间
        private double newVersionErrorRate;   // 新版本错误率
        private double oldVersionErrorRate;   // 旧版本错误率
        private LocalDateTime lastUpdateTime; // 最后更新时间
        private Map<String, Long> errorCounts = new HashMap<>(); // 错误统计
    }

    /**
     * 分流条件
     */
    @Data
    public static class TrafficCriteria {
        private String userId;                // 用户ID
        private String userAgent;             // 用户代理
        private String ipAddress;             // IP地址
        private String region;                // 地区
        private String deviceType;            // 设备类型
        private String browser;               // 浏览器
        private String os;                    // 操作系统
        private Map<String, String> headers;  // 请求头
        private Map<String, String> cookies;  // Cookie
        private String requestPath;           // 请求路径
        private String httpMethod;            // HTTP方法
    }

    /**
     * 创建A/B测试策略
     */
    public ReleaseStrategy createABTestStrategy(String serviceName, String baseRouteId, 
                                               String newRouteId, int trafficPercent) {
        ReleaseStrategy strategy = new ReleaseStrategy();
        strategy.setId("ab-test-" + serviceName + "-" + System.currentTimeMillis());
        strategy.setName("A/B测试-" + serviceName);
        strategy.setDescription("A/B测试策略，对比新旧版本性能");
        strategy.setType(StrategyType.A_B_TEST);
        strategy.setServiceName(serviceName);
        strategy.setBaseRouteId(baseRouteId);
        strategy.setNewRouteId(newRouteId);
        strategy.setStartTime(LocalDateTime.now());
        strategy.setEnabled(true);
        strategy.setStatus(ReleaseStatus.PENDING);
        strategy.setCurrentTrafficPercent(0);
        strategy.setTargetTrafficPercent(trafficPercent);
        strategy.setStepTrafficPercent(trafficPercent);
        strategy.setStepIntervalSeconds(0);

        Map<String, Object> config = new HashMap<>();
        config.put("trafficPercent", trafficPercent);
        config.put("duration", 3600); // 1小时
        config.put("successCriteria", "errorRate < 0.05 && avgResponseTime < 2000");
        strategy.setConfig(config);

        strategyCache.put(strategy.getId(), strategy);
        log.info("Created A/B test strategy: {}", strategy.getId());
        return strategy;
    }

    /**
     * 创建灰度发布策略
     */
    public ReleaseStrategy createGrayReleaseStrategy(String serviceName, String baseRouteId, 
                                                   String newRouteId, int initialTrafficPercent) {
        ReleaseStrategy strategy = new ReleaseStrategy();
        strategy.setId("gray-release-" + serviceName + "-" + System.currentTimeMillis());
        strategy.setName("灰度发布-" + serviceName);
        strategy.setDescription("灰度发布策略，逐步增加新版本流量");
        strategy.setType(StrategyType.GRAY_RELEASE);
        strategy.setServiceName(serviceName);
        strategy.setBaseRouteId(baseRouteId);
        strategy.setNewRouteId(newRouteId);
        strategy.setStartTime(LocalDateTime.now());
        strategy.setEnabled(true);
        strategy.setStatus(ReleaseStatus.PENDING);
        strategy.setCurrentTrafficPercent(0);
        strategy.setTargetTrafficPercent(100);
        strategy.setStepTrafficPercent(10);
        strategy.setStepIntervalSeconds(300); // 5分钟

        Map<String, Object> config = new HashMap<>();
        config.put("initialTrafficPercent", initialTrafficPercent);
        config.put("stepTrafficPercent", 10);
        config.put("stepIntervalSeconds", 300);
        config.put("successCriteria", "errorRate < 0.02 && avgResponseTime < 1500");
        strategy.setConfig(config);

        strategyCache.put(strategy.getId(), strategy);
        log.info("Created gray release strategy: {}", strategy.getId());
        return strategy;
    }

    /**
     * 创建金丝雀发布策略
     */
    public ReleaseStrategy createCanaryReleaseStrategy(String serviceName, String baseRouteId, 
                                                     String newRouteId, List<String> targetUsers) {
        ReleaseStrategy strategy = new ReleaseStrategy();
        strategy.setId("canary-release-" + serviceName + "-" + System.currentTimeMillis());
        strategy.setName("金丝雀发布-" + serviceName);
        strategy.setDescription("金丝雀发布策略，特定用户优先体验新版本");
        strategy.setType(StrategyType.CANARY_RELEASE);
        strategy.setServiceName(serviceName);
        strategy.setBaseRouteId(baseRouteId);
        strategy.setNewRouteId(newRouteId);
        strategy.setStartTime(LocalDateTime.now());
        strategy.setEnabled(true);
        strategy.setStatus(ReleaseStatus.PENDING);
        strategy.setTargetUsers(targetUsers);
        strategy.setCurrentTrafficPercent(0);
        strategy.setTargetTrafficPercent(100);
        strategy.setStepTrafficPercent(100);
        strategy.setStepIntervalSeconds(0);

        Map<String, Object> config = new HashMap<>();
        config.put("targetUsers", targetUsers);
        config.put("duration", 7200); // 2小时
        config.put("successCriteria", "errorRate < 0.01 && avgResponseTime < 1000");
        strategy.setConfig(config);

        strategyCache.put(strategy.getId(), strategy);
        log.info("Created canary release strategy: {}", strategy.getId());
        return strategy;
    }

    /**
     * 启动发布策略
     */
    public boolean startReleaseStrategy(String strategyId) {
        ReleaseStrategy strategy = strategyCache.get(strategyId);
        if (strategy == null) {
            log.error("Strategy not found: {}", strategyId);
            return false;
        }

        strategy.setStatus(ReleaseStatus.RUNNING);
        strategy.setStartTime(LocalDateTime.now());
        
        // 初始化统计信息
        ReleaseStats stats = new ReleaseStats();
        stats.setStrategyId(strategyId);
        stats.setLastUpdateTime(LocalDateTime.now());
        statsCache.put(strategyId, stats);

        log.info("Started release strategy: {}", strategyId);
        return true;
    }

    /**
     * 暂停发布策略
     */
    public boolean pauseReleaseStrategy(String strategyId) {
        ReleaseStrategy strategy = strategyCache.get(strategyId);
        if (strategy == null) {
            log.error("Strategy not found: {}", strategyId);
            return false;
        }

        strategy.setStatus(ReleaseStatus.PAUSED);
        log.info("Paused release strategy: {}", strategyId);
        return true;
    }

    /**
     * 完成发布策略
     */
    public boolean completeReleaseStrategy(String strategyId) {
        ReleaseStrategy strategy = strategyCache.get(strategyId);
        if (strategy == null) {
            log.error("Strategy not found: {}", strategyId);
            return false;
        }

        strategy.setStatus(ReleaseStatus.COMPLETED);
        strategy.setEndTime(LocalDateTime.now());
        strategy.setCurrentTrafficPercent(strategy.getTargetTrafficPercent());
        
        log.info("Completed release strategy: {}", strategyId);
        return true;
    }

    /**
     * 回滚发布策略
     */
    public boolean rollbackReleaseStrategy(String strategyId) {
        ReleaseStrategy strategy = strategyCache.get(strategyId);
        if (strategy == null) {
            log.error("Strategy not found: {}", strategyId);
            return false;
        }

        strategy.setStatus(ReleaseStatus.ROLLBACK);
        strategy.setCurrentTrafficPercent(0);
        strategy.setEndTime(LocalDateTime.now());
        
        log.info("Rollback release strategy: {}", strategyId);
        return true;
    }

    /**
     * 获取发布策略
     */
    public ReleaseStrategy getReleaseStrategy(String strategyId) {
        return strategyCache.get(strategyId);
    }

    /**
     * 获取所有发布策略
     */
    public List<ReleaseStrategy> getAllReleaseStrategies() {
        return new ArrayList<>(strategyCache.values());
    }

    /**
     * 删除发布策略
     */
    public boolean deleteReleaseStrategy(String strategyId) {
        ReleaseStrategy strategy = strategyCache.remove(strategyId);
        if (strategy != null) {
            statsCache.remove(strategyId);
            log.info("Deleted release strategy: {}", strategyId);
            return true;
        }
        return false;
    }

    /**
     * 更新发布统计信息
     */
    public void updateReleaseStats(String strategyId, boolean isNewVersion, 
                                 boolean success, long responseTime, String errorType) {
        ReleaseStats stats = statsCache.computeIfAbsent(strategyId, k -> {
            ReleaseStats newStats = new ReleaseStats();
            newStats.setStrategyId(strategyId);
            newStats.setLastUpdateTime(LocalDateTime.now());
            return newStats;
        });

        stats.setTotalRequests(stats.getTotalRequests() + 1);
        stats.setLastUpdateTime(LocalDateTime.now());

        if (isNewVersion) {
            stats.setNewVersionRequests(stats.getNewVersionRequests() + 1);
            if (success) {
                stats.setNewVersionSuccess(stats.getNewVersionSuccess() + 1);
            } else {
                stats.setNewVersionErrors(stats.getNewVersionErrors() + 1);
                if (errorType != null) {
                    stats.getErrorCounts().merge("new-" + errorType, 1L, Long::sum);
                }
            }
            
            // 更新新版本平均响应时间
            if (responseTime > 0) {
                if (stats.getNewVersionAvgResponseTime() == 0) {
                    stats.setNewVersionAvgResponseTime(responseTime);
                } else {
                    double totalTime = stats.getNewVersionAvgResponseTime() * (stats.getNewVersionSuccess() - 1) + responseTime;
                    stats.setNewVersionAvgResponseTime(totalTime / stats.getNewVersionSuccess());
                }
            }
        } else {
            stats.setOldVersionRequests(stats.getOldVersionRequests() + 1);
            if (success) {
                stats.setOldVersionSuccess(stats.getOldVersionSuccess() + 1);
            } else {
                stats.setOldVersionErrors(stats.getOldVersionErrors() + 1);
                if (errorType != null) {
                    stats.getErrorCounts().merge("old-" + errorType, 1L, Long::sum);
                }
            }
            
            // 更新旧版本平均响应时间
            if (responseTime > 0) {
                if (stats.getOldVersionAvgResponseTime() == 0) {
                    stats.setOldVersionAvgResponseTime(responseTime);
                } else {
                    double totalTime = stats.getOldVersionAvgResponseTime() * (stats.getOldVersionSuccess() - 1) + responseTime;
                    stats.setOldVersionAvgResponseTime(totalTime / stats.getOldVersionSuccess());
                }
            }
        }

        // 计算错误率
        if (stats.getNewVersionRequests() > 0) {
            stats.setNewVersionErrorRate((double) stats.getNewVersionErrors() / stats.getNewVersionRequests());
        }
        if (stats.getOldVersionRequests() > 0) {
            stats.setOldVersionErrorRate((double) stats.getOldVersionErrors() / stats.getOldVersionRequests());
        }
    }

    /**
     * 获取发布统计信息
     */
    public ReleaseStats getReleaseStats(String strategyId) {
        return statsCache.get(strategyId);
    }

    /**
     * 获取所有发布统计信息
     */
    public Map<String, ReleaseStats> getAllReleaseStats() {
        return new HashMap<>(statsCache);
    }

    /**
     * 判断请求是否应该路由到新版本
     */
    public boolean shouldRouteToNewVersion(String strategyId, TrafficCriteria criteria) {
        ReleaseStrategy strategy = strategyCache.get(strategyId);
        if (strategy == null || !strategy.isEnabled() || strategy.getStatus() != ReleaseStatus.RUNNING) {
            return false;
        }

        switch (strategy.getType()) {
            case A_B_TEST:
                return shouldRouteForABTest(strategy, criteria);
            case GRAY_RELEASE:
                return shouldRouteForGrayRelease(strategy, criteria);
            case CANARY_RELEASE:
                return shouldRouteForCanaryRelease(strategy, criteria);
            default:
                return false;
        }
    }

    /**
     * A/B测试分流逻辑
     */
    private boolean shouldRouteForABTest(ReleaseStrategy strategy, TrafficCriteria criteria) {
        // 基于用户ID的哈希分流
        if (criteria.getUserId() != null) {
            int hash = Math.abs(criteria.getUserId().hashCode());
            int percent = hash % 100;
            return percent < strategy.getCurrentTrafficPercent();
        }
        
        // 基于IP的哈希分流
        if (criteria.getIpAddress() != null) {
            int hash = Math.abs(criteria.getIpAddress().hashCode());
            int percent = hash % 100;
            return percent < strategy.getCurrentTrafficPercent();
        }
        
        // 随机分流
        return Math.random() * 100 < strategy.getCurrentTrafficPercent();
    }

    /**
     * 灰度发布分流逻辑
     */
    private boolean shouldRouteForGrayRelease(ReleaseStrategy strategy, TrafficCriteria criteria) {
        // 基于用户ID的哈希分流
        if (criteria.getUserId() != null) {
            int hash = Math.abs(criteria.getUserId().hashCode());
            int percent = hash % 100;
            return percent < strategy.getCurrentTrafficPercent();
        }
        
        // 基于IP的哈希分流
        if (criteria.getIpAddress() != null) {
            int hash = Math.abs(criteria.getIpAddress().hashCode());
            int percent = hash % 100;
            return percent < strategy.getCurrentTrafficPercent();
        }
        
        // 随机分流
        return Math.random() * 100 < strategy.getCurrentTrafficPercent();
    }

    /**
     * 金丝雀发布分流逻辑
     */
    private boolean shouldRouteForCanaryRelease(ReleaseStrategy strategy, TrafficCriteria criteria) {
        // 检查是否在目标用户列表中
        if (criteria.getUserId() != null && strategy.getTargetUsers() != null) {
            return strategy.getTargetUsers().contains(criteria.getUserId());
        }
        
        // 检查是否在目标IP列表中
        if (criteria.getIpAddress() != null && strategy.getTargetIps() != null) {
            return strategy.getTargetIps().contains(criteria.getIpAddress());
        }
        
        return false;
    }

    /**
     * 自动步进流量（用于灰度发布）
     */
    public void autoStepTraffic() {
        for (ReleaseStrategy strategy : strategyCache.values()) {
            if (strategy.getType() == StrategyType.GRAY_RELEASE && 
                strategy.getStatus() == ReleaseStatus.RUNNING) {
                
                int currentPercent = strategy.getCurrentTrafficPercent();
                int stepPercent = strategy.getStepTrafficPercent();
                int targetPercent = strategy.getTargetTrafficPercent();
                
                if (currentPercent < targetPercent) {
                    int newPercent = Math.min(currentPercent + stepPercent, targetPercent);
                    strategy.setCurrentTrafficPercent(newPercent);
                    
                    if (newPercent >= targetPercent) {
                        strategy.setStatus(ReleaseStatus.COMPLETED);
                        strategy.setEndTime(LocalDateTime.now());
                    }
                    
                    log.info("Stepped traffic for strategy {}: {}% -> {}%", 
                            strategy.getId(), currentPercent, newPercent);
                }
            }
        }
    }

    /**
     * 获取发布版本号
     */
    public long getReleaseVersion() {
        return releaseVersion.get();
    }

    /**
     * 增加发布版本号
     */
    public void incrementReleaseVersion() {
        releaseVersion.incrementAndGet();
    }
} 