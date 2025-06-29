package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.service.ServiceDegradationService;
import com.citi.tts.api.gateway.service.impl.CoreServiceFallbackHandlerImpl;
import com.citi.tts.api.gateway.service.impl.ImportantServiceFallbackHandlerImpl;
import com.citi.tts.api.gateway.service.impl.NormalServiceFallbackHandlerImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务降级控制器
 * 提供降级策略的管理和监控接口
 */
@Slf4j
@RestController
@RequestMapping("/api/degradation")
public class ServiceDegradationController {

    @Autowired
    private ServiceDegradationService degradationService;

    @Autowired
    private CoreServiceFallbackHandlerImpl coreFallbackHandler;

    @Autowired
    private ImportantServiceFallbackHandlerImpl importantFallbackHandler;

    @Autowired
    private NormalServiceFallbackHandlerImpl normalFallbackHandler;

    /**
     * 获取服务级别统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getServiceLevelStats() {
        try {
            Map<String, Object> stats = degradationService.getServiceLevelStats();
            log.info("Service level stats retrieved successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get service level stats", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get service level stats",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 更新服务级别配置
     */
    @PostMapping("/service-level")
    public ResponseEntity<Map<String, Object>> updateServiceLevel(
            @RequestParam String serviceName,
            @RequestParam String apiPath,
            @RequestParam ServiceDegradationService.ServiceLevel level) {
        
        try {
            degradationService.updateServiceLevel(serviceName, apiPath, level);
            log.info("Service level updated - Service: {}, Path: {}, Level: {}", serviceName, apiPath, level);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Service level updated successfully",
                "serviceName", serviceName,
                "apiPath", apiPath,
                "level", level.name()
            ));
        } catch (Exception e) {
            log.error("Failed to update service level", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to update service level",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 获取核心服务缓存统计
     */
    @GetMapping("/cache/core/stats")
    public ResponseEntity<Map<String, Object>> getCoreCacheStats() {
        try {
            Map<String, Object> stats = coreFallbackHandler.getLocalCacheStats();
            log.info("Core cache stats retrieved successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get core cache stats", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get core cache stats",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 清理核心服务缓存
     */
    @DeleteMapping("/cache/core")
    public ResponseEntity<Map<String, Object>> clearCoreCache() {
        try {
            coreFallbackHandler.clearLocalCache();
            log.info("Core cache cleared successfully");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Core cache cleared successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to clear core cache", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to clear core cache",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 更新核心服务缓存
     */
    @PostMapping("/cache/core")
    public ResponseEntity<Map<String, Object>> updateCoreCache(
            @RequestParam String serviceName,
            @RequestParam String apiPath,
            @RequestBody Object data) {
        
        try {
            coreFallbackHandler.updateLocalCache(serviceName, apiPath, data);
            log.info("Core cache updated - Service: {}, Path: {}", serviceName, apiPath);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Core cache updated successfully",
                "serviceName", serviceName,
                "apiPath", apiPath
            ));
        } catch (Exception e) {
            log.error("Failed to update core cache", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to update core cache",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 获取重要服务缓存统计
     */
    @GetMapping("/cache/important/stats")
    public ResponseEntity<Map<String, Object>> getImportantCacheStats() {
        try {
            Map<String, Object> stats = importantFallbackHandler.getLocalCacheStats();
            log.info("Important cache stats retrieved successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get important cache stats", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get important cache stats",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 清理重要服务缓存
     */
    @DeleteMapping("/cache/important")
    public ResponseEntity<Map<String, Object>> clearImportantCache() {
        try {
            importantFallbackHandler.clearLocalCache();
            log.info("Important cache cleared successfully");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Important cache cleared successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to clear important cache", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to clear important cache",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 获取普通服务缓存统计
     */
    @GetMapping("/cache/normal/stats")
    public ResponseEntity<Map<String, Object>> getNormalCacheStats() {
        try {
            Map<String, Object> stats = normalFallbackHandler.getLocalCacheStats();
            log.info("Normal cache stats retrieved successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get normal cache stats", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get normal cache stats",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 清理普通服务缓存
     */
    @DeleteMapping("/cache/normal")
    public ResponseEntity<Map<String, Object>> clearNormalCache() {
        try {
            normalFallbackHandler.clearLocalCache();
            log.info("Normal cache cleared successfully");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Normal cache cleared successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to clear normal cache", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to clear normal cache",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 清理所有缓存
     */
    @DeleteMapping("/cache/all")
    public ResponseEntity<Map<String, Object>> clearAllCache() {
        try {
            coreFallbackHandler.clearLocalCache();
            importantFallbackHandler.clearLocalCache();
            normalFallbackHandler.clearLocalCache();
            log.info("All cache cleared successfully");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All cache cleared successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to clear all cache", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to clear all cache",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 获取所有缓存统计
     */
    @GetMapping("/cache/all/stats")
    public ResponseEntity<Map<String, Object>> getAllCacheStats() {
        try {
            Map<String, Object> allStats = new HashMap<>();
            allStats.put("core", coreFallbackHandler.getLocalCacheStats());
            allStats.put("important", importantFallbackHandler.getLocalCacheStats());
            allStats.put("normal", normalFallbackHandler.getLocalCacheStats());
            allStats.put("timestamp", System.currentTimeMillis());
            
            log.info("All cache stats retrieved successfully");
            return ResponseEntity.ok(allStats);
        } catch (Exception e) {
            log.error("Failed to get all cache stats", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get all cache stats",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 测试服务降级
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testDegradation(
            @RequestParam String serviceName,
            @RequestParam String apiPath,
            @RequestParam ServiceDegradationService.ServiceLevel level,
            @RequestParam String errorType,
            @RequestParam String errorMessage) {
        
        try {
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("test", true);
            requestData.put("serviceName", serviceName);
            requestData.put("apiPath", apiPath);
            
            ServiceDegradationService.DegradationRequest request = 
                new ServiceDegradationService.DegradationRequest(
                    serviceName, apiPath, level, requestData, errorType, errorMessage
                );
            
            return degradationService.executeDegradation(request)
                    .map(response -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("success", response.isSuccess());
                        result.put("message", response.getMessage());
                        result.put("data", response.getData());
                        result.put("fallbackType", response.getFallbackType());
                        result.put("timestamp", response.getTimestamp());
                        return ResponseEntity.ok(result);
                    })
                    .block();
                    
        } catch (Exception e) {
            log.error("Failed to test degradation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to test degradation",
                "message", e.getMessage()
            ));
        }
    }
} 