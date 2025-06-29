package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.release.ReleaseValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布验证管理控制器
 * 提供发布策略的创建、管理、监控等REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway/release")
public class ReleaseValidationController {

    @Autowired
    private ReleaseValidationService releaseValidationService;

    /**
     * 创建A/B测试策略
     */
    @PostMapping("/strategies/ab-test")
    public ResponseEntity<Map<String, Object>> createABTestStrategy(
            @RequestParam String serviceName,
            @RequestParam String baseRouteId,
            @RequestParam String newRouteId,
            @RequestParam(defaultValue = "50") int trafficPercent) {
        
        log.info("Creating A/B test strategy - Service: {}, Base Route: {}, New Route: {}, Traffic: {}%", 
                serviceName, baseRouteId, newRouteId, trafficPercent);
        
        try {
            ReleaseValidationService.ReleaseStrategy strategy = 
                releaseValidationService.createABTestStrategy(serviceName, baseRouteId, newRouteId, trafficPercent);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("strategy", strategy);
            response.put("message", "A/B test strategy created successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create A/B test strategy", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 创建灰度发布策略
     */
    @PostMapping("/strategies/gray-release")
    public ResponseEntity<Map<String, Object>> createGrayReleaseStrategy(
            @RequestParam String serviceName,
            @RequestParam String baseRouteId,
            @RequestParam String newRouteId,
            @RequestParam(defaultValue = "10") int initialTrafficPercent) {
        
        log.info("Creating gray release strategy - Service: {}, Base Route: {}, New Route: {}, Initial Traffic: {}%", 
                serviceName, baseRouteId, newRouteId, initialTrafficPercent);
        
        try {
            ReleaseValidationService.ReleaseStrategy strategy = 
                releaseValidationService.createGrayReleaseStrategy(serviceName, baseRouteId, newRouteId, initialTrafficPercent);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("strategy", strategy);
            response.put("message", "Gray release strategy created successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create gray release strategy", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 创建金丝雀发布策略
     */
    @PostMapping("/strategies/canary-release")
    public ResponseEntity<Map<String, Object>> createCanaryReleaseStrategy(
            @RequestParam String serviceName,
            @RequestParam String baseRouteId,
            @RequestParam String newRouteId,
            @RequestBody List<String> targetUsers) {
        
        log.info("Creating canary release strategy - Service: {}, Base Route: {}, New Route: {}, Target Users: {}", 
                serviceName, baseRouteId, newRouteId, targetUsers);
        
        try {
            ReleaseValidationService.ReleaseStrategy strategy = 
                releaseValidationService.createCanaryReleaseStrategy(serviceName, baseRouteId, newRouteId, targetUsers);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("strategy", strategy);
            response.put("message", "Canary release strategy created successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create canary release strategy", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 启动发布策略
     */
    @PostMapping("/strategies/{strategyId}/start")
    public ResponseEntity<Map<String, Object>> startReleaseStrategy(@PathVariable String strategyId) {
        log.info("Starting release strategy: {}", strategyId);
        
        try {
            boolean success = releaseValidationService.startReleaseStrategy(strategyId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("strategyId", strategyId);
            response.put("message", success ? "Release strategy started successfully" : "Failed to start release strategy");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start release strategy: {}", strategyId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("strategyId", strategyId);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 暂停发布策略
     */
    @PostMapping("/strategies/{strategyId}/pause")
    public ResponseEntity<Map<String, Object>> pauseReleaseStrategy(@PathVariable String strategyId) {
        log.info("Pausing release strategy: {}", strategyId);
        
        try {
            boolean success = releaseValidationService.pauseReleaseStrategy(strategyId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("strategyId", strategyId);
            response.put("message", success ? "Release strategy paused successfully" : "Failed to pause release strategy");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to pause release strategy: {}", strategyId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("strategyId", strategyId);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 完成发布策略
     */
    @PostMapping("/strategies/{strategyId}/complete")
    public ResponseEntity<Map<String, Object>> completeReleaseStrategy(@PathVariable String strategyId) {
        log.info("Completing release strategy: {}", strategyId);
        
        try {
            boolean success = releaseValidationService.completeReleaseStrategy(strategyId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("strategyId", strategyId);
            response.put("message", success ? "Release strategy completed successfully" : "Failed to complete release strategy");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to complete release strategy: {}", strategyId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("strategyId", strategyId);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 回滚发布策略
     */
    @PostMapping("/strategies/{strategyId}/rollback")
    public ResponseEntity<Map<String, Object>> rollbackReleaseStrategy(@PathVariable String strategyId) {
        log.info("Rolling back release strategy: {}", strategyId);
        
        try {
            boolean success = releaseValidationService.rollbackReleaseStrategy(strategyId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("strategyId", strategyId);
            response.put("message", success ? "Release strategy rolled back successfully" : "Failed to rollback release strategy");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to rollback release strategy: {}", strategyId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("strategyId", strategyId);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取所有发布策略
     */
    @GetMapping("/strategies")
    public ResponseEntity<Map<String, Object>> getAllReleaseStrategies() {
        log.debug("Getting all release strategies");
        
        try {
            List<ReleaseValidationService.ReleaseStrategy> strategies = releaseValidationService.getAllReleaseStrategies();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("strategies", strategies);
            response.put("count", strategies.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get all release strategies", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 根据ID获取发布策略
     */
    @GetMapping("/strategies/{strategyId}")
    public ResponseEntity<Map<String, Object>> getReleaseStrategy(@PathVariable String strategyId) {
        log.debug("Getting release strategy: {}", strategyId);
        
        try {
            ReleaseValidationService.ReleaseStrategy strategy = releaseValidationService.getReleaseStrategy(strategyId);
            
            if (strategy == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Strategy not found");
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("strategy", strategy);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get release strategy: {}", strategyId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 删除发布策略
     */
    @DeleteMapping("/strategies/{strategyId}")
    public ResponseEntity<Map<String, Object>> deleteReleaseStrategy(@PathVariable String strategyId) {
        log.info("Deleting release strategy: {}", strategyId);
        
        try {
            boolean success = releaseValidationService.deleteReleaseStrategy(strategyId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("strategyId", strategyId);
            response.put("message", success ? "Release strategy deleted successfully" : "Failed to delete release strategy");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete release strategy: {}", strategyId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("strategyId", strategyId);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取发布统计信息
     */
    @GetMapping("/stats/{strategyId}")
    public ResponseEntity<Map<String, Object>> getReleaseStats(@PathVariable String strategyId) {
        log.debug("Getting release stats: {}", strategyId);
        
        try {
            ReleaseValidationService.ReleaseStats stats = releaseValidationService.getReleaseStats(strategyId);
            
            if (stats == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Stats not found");
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get release stats: {}", strategyId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取所有发布统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAllReleaseStats() {
        log.debug("Getting all release stats");
        
        try {
            Map<String, ReleaseValidationService.ReleaseStats> allStats = releaseValidationService.getAllReleaseStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", allStats);
            response.put("count", allStats.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get all release stats", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 自动步进流量（用于灰度发布）
     */
    @PostMapping("/auto-step")
    public ResponseEntity<Map<String, Object>> autoStepTraffic() {
        log.info("Executing auto step traffic");
        
        try {
            releaseValidationService.autoStepTraffic();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Auto step traffic executed successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to execute auto step traffic", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取发布版本号
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getReleaseVersion() {
        log.debug("Getting release version");
        
        try {
            long version = releaseValidationService.getReleaseVersion();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("version", version);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get release version", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 