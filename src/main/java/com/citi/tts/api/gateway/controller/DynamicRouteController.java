package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.routes.DynamicRouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 动态路由控制器
 * 提供路由的动态管理REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway/routes")
public class DynamicRouteController {

    @Autowired
    private DynamicRouteService dynamicRouteService;

    /**
     * 获取所有路由
     */
    @GetMapping
    public Mono<ResponseEntity<List<DynamicRouteService.DynamicRouteDefinition>>> getAllRoutes() {
        return dynamicRouteService.getAllRoutes()
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    /**
     * 根据ID获取路由
     */
    @GetMapping("/{routeId}")
    public Mono<ResponseEntity<DynamicRouteService.DynamicRouteDefinition>> getRouteById(@PathVariable String routeId) {
        return dynamicRouteService.getRouteById(routeId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    /**
     * 添加路由
     */
    @PostMapping
    public Mono<ResponseEntity<String>> addRoute(@RequestBody DynamicRouteService.DynamicRouteDefinition routeDef) {
        return dynamicRouteService.addRoute(routeDef)
                .map(success -> {
                    if (success) {
                        dynamicRouteService.incrementRouteVersion();
                        return ResponseEntity.ok("Route added successfully: " + routeDef.getId());
                    } else {
                        return ResponseEntity.badRequest().body("Failed to add route: " + routeDef.getId());
                    }
                })
                .onErrorReturn(ResponseEntity.internalServerError().body("Error adding route: " + routeDef.getId()));
    }

    /**
     * 更新路由
     */
    @PutMapping("/{routeId}")
    public Mono<ResponseEntity<String>> updateRoute(@PathVariable String routeId, 
                                                   @RequestBody DynamicRouteService.DynamicRouteDefinition routeDef) {
        routeDef.setId(routeId);
        return dynamicRouteService.updateRoute(routeDef)
                .map(success -> {
                    if (success) {
                        dynamicRouteService.incrementRouteVersion();
                        return ResponseEntity.ok("Route updated successfully: " + routeId);
                    } else {
                        return ResponseEntity.badRequest().body("Failed to update route: " + routeId);
                    }
                })
                .onErrorReturn(ResponseEntity.internalServerError().body("Error updating route: " + routeId));
    }

    /**
     * 删除路由
     */
    @DeleteMapping("/{routeId}")
    public Mono<ResponseEntity<String>> deleteRoute(@PathVariable String routeId) {
        return dynamicRouteService.deleteRoute(routeId)
                .map(success -> {
                    if (success) {
                        dynamicRouteService.incrementRouteVersion();
                        return ResponseEntity.ok("Route deleted successfully: " + routeId);
                    } else {
                        return ResponseEntity.badRequest().body("Failed to delete route: " + routeId);
                    }
                })
                .onErrorReturn(ResponseEntity.internalServerError().body("Error deleting route: " + routeId));
    }

    /**
     * 启用/禁用路由
     */
    @PatchMapping("/{routeId}/toggle")
    public Mono<ResponseEntity<String>> toggleRoute(@PathVariable String routeId, 
                                                   @RequestParam boolean enabled) {
        return dynamicRouteService.toggleRoute(routeId, enabled)
                .map(success -> {
                    if (success) {
                        dynamicRouteService.incrementRouteVersion();
                        String action = enabled ? "enabled" : "disabled";
                        return ResponseEntity.ok("Route " + action + " successfully: " + routeId);
                    } else {
                        return ResponseEntity.badRequest().body("Failed to toggle route: " + routeId);
                    }
                })
                .onErrorReturn(ResponseEntity.internalServerError().body("Error toggling route: " + routeId));
    }

    /**
     * 获取路由统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, DynamicRouteService.RouteStats>> getRouteStats() {
        return ResponseEntity.ok(dynamicRouteService.getRouteStats());
    }

    /**
     * 获取路由版本号
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getRouteVersion() {
        Map<String, Object> versionInfo = Map.of(
            "version", dynamicRouteService.getRouteVersion(),
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(versionInfo);
    }

    /**
     * 批量更新路由
     */
    @PostMapping("/batch")
    public Mono<ResponseEntity<String>> batchUpdateRoutes(@RequestBody List<DynamicRouteService.DynamicRouteDefinition> routeDefs) {
        return Mono.fromCallable(() -> {
            boolean allSuccess = true;
            StringBuilder result = new StringBuilder();
            
            for (DynamicRouteService.DynamicRouteDefinition routeDef : routeDefs) {
                try {
                    Boolean success = dynamicRouteService.updateRoute(routeDef).block();
                    if (success == null || !success) {
                        allSuccess = false;
                        result.append("Failed to update route: ").append(routeDef.getId()).append("; ");
                    } else {
                        result.append("Updated route: ").append(routeDef.getId()).append("; ");
                    }
                } catch (Exception e) {
                    allSuccess = false;
                    result.append("Error updating route: ").append(routeDef.getId()).append(": ").append(e.getMessage()).append("; ");
                }
            }
            
            if (allSuccess) {
                dynamicRouteService.incrementRouteVersion();
                return ResponseEntity.ok("Batch update completed: " + result.toString());
            } else {
                return ResponseEntity.badRequest().body("Batch update failed: " + result.toString());
            }
        });
    }

    /**
     * 刷新路由配置
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshRoutes() {
        try {
            dynamicRouteService.incrementRouteVersion();
            return ResponseEntity.ok("Routes refreshed successfully. Version: " + dynamicRouteService.getRouteVersion());
        } catch (Exception e) {
            log.error("Error refreshing routes", e);
            return ResponseEntity.internalServerError().body("Error refreshing routes: " + e.getMessage());
        }
    }
} 