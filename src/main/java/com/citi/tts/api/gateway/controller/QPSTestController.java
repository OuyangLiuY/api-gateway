package com.citi.tts.api.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * QPS测试控制器
 * 用于测试QPS统计功能
 */
@Slf4j
@RestController
@RequestMapping("/api/test/qps")
public class QPSTestController {

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final Random random = new Random();

    /**
     * 模拟不同API路径的请求
     */
    @GetMapping("/api/{path}")
    public ResponseEntity<Map<String, Object>> testApiPath(@PathVariable String path) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "API path test");
        response.put("path", path);
        response.put("timestamp", System.currentTimeMillis());
        
        // 模拟处理时间
        try {
            Thread.sleep(random.nextInt(100) + 50); // 50-150ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.debug("Test API path: {}", path);
        return ResponseEntity.ok(response);
    }

    /**
     * 模拟不同用户的请求
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> testUser(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User test");
        response.put("userId", userId);
        response.put("timestamp", System.currentTimeMillis());
        
        // 模拟处理时间
        try {
            Thread.sleep(random.nextInt(200) + 100); // 100-300ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.debug("Test user: {}", userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 模拟不同优先级的请求
     */
    @GetMapping("/priority/{priority}")
    public ResponseEntity<Map<String, Object>> testPriority(@PathVariable String priority) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Priority test");
        response.put("priority", priority);
        response.put("timestamp", System.currentTimeMillis());
        
        // 根据优先级调整处理时间
        int sleepTime;
        switch (priority.toLowerCase()) {
            case "core":
                sleepTime = random.nextInt(50) + 20; // 20-70ms
                break;
            case "normal":
                sleepTime = random.nextInt(100) + 50; // 50-150ms
                break;
            case "non-core":
                sleepTime = random.nextInt(200) + 100; // 100-300ms
                break;
            default:
                sleepTime = random.nextInt(150) + 75; // 75-225ms
        }
        
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.debug("Test priority: {}", priority);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量测试请求
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchTest(@RequestBody Map<String, Object> request) {
        int count = (int) request.getOrDefault("count", 10);
        String type = (String) request.getOrDefault("type", "api");
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Batch test started");
        response.put("count", count);
        response.put("type", type);
        response.put("timestamp", System.currentTimeMillis());
        
        // 异步执行批量测试
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < count; i++) {
                try {
                    switch (type) {
                        case "api":
                            testApiPath("/api/test/batch/" + i);
                            break;
                        case "user":
                            testUser("user" + i);
                            break;
                        case "priority":
                            String[] priorities = {"core", "normal", "non-core"};
                            testPriority(priorities[i % priorities.length]);
                            break;
                        default:
                            testApiPath("/api/test/batch/" + i);
                    }
                    
                    // 随机间隔
                    Thread.sleep(random.nextInt(100) + 50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, executorService);
        
        log.info("Batch test started - Count: {}, Type: {}", count, type);
        return ResponseEntity.ok(response);
    }

    /**
     * 压力测试
     */
    @PostMapping("/stress")
    public ResponseEntity<Map<String, Object>> stressTest(@RequestBody Map<String, Object> request) {
        int duration = (int) request.getOrDefault("duration", 30); // 秒
        int qps = (int) request.getOrDefault("qps", 100);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Stress test started");
        response.put("duration", duration);
        response.put("qps", qps);
        response.put("timestamp", System.currentTimeMillis());
        
        // 异步执行压力测试
        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (duration * 1000L);
            long interval = 1000L / qps; // 请求间隔
            
            while (System.currentTimeMillis() < endTime) {
                try {
                    // 随机选择测试类型
                    int type = random.nextInt(3);
                    switch (type) {
                        case 0:
                            testApiPath("/api/stress/" + random.nextInt(10));
                            break;
                        case 1:
                            testUser("stress-user-" + random.nextInt(100));
                            break;
                        case 2:
                            String[] priorities = {"core", "normal", "non-core"};
                            testPriority(priorities[random.nextInt(priorities.length)]);
                            break;
                    }
                    
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, executorService);
        
        log.info("Stress test started - Duration: {}s, QPS: {}", duration, qps);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取测试状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTestStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("executorService", executorService.isShutdown() ? "shutdown" : "running");
        status.put("activeThreads", executorService.isShutdown() ? 0 : "active");
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }
} 