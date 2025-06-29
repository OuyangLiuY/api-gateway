package com.citi.tts.api.gateway.controller;

import com.citi.tts.api.gateway.service.WebClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebClient测试控制器
 * 展示如何使用分级WebClient进行HTTP调用
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway/webclient")
public class WebClientTestController {

    @Autowired
    private WebClientService webClientService;

    /**
     * 测试核心服务调用
     */
    @GetMapping("/test/core")
    public Mono<ResponseEntity<Map<String, Object>>> testCoreService(
            @RequestParam(defaultValue = "https://httpbin.org/get") String url) {
        
        log.info("Testing core service WebClient with URL: {}", url);
        
        return webClientService.callCoreService(url, Map.class)
                .map(result -> {
                    log.info("Core service call successful");
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "serviceType", "CORE",
                        "url", url,
                        "result", result
                    ));
                })
                .onErrorResume(error -> {
                    log.error("Core service call failed", error);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of(
                        "success", false,
                        "serviceType", "CORE",
                        "url", url,
                        "error", error.getMessage()
                    )));
                });
    }

    /**
     * 测试重要服务调用
     */
    @GetMapping("/test/important")
    public Mono<ResponseEntity<Map<String, Object>>> testImportantService(
            @RequestParam(defaultValue = "https://httpbin.org/get") String url) {
        
        log.info("Testing important service WebClient with URL: {}", url);
        
        return webClientService.callImportantService(url, Map.class)
                .map(result -> {
                    log.info("Important service call successful");
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "serviceType", "IMPORTANT",
                        "url", url,
                        "result", result
                    ));
                })
                .onErrorResume(error -> {
                    log.error("Important service call failed", error);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of(
                        "success", false,
                        "serviceType", "IMPORTANT",
                        "url", url,
                        "error", error.getMessage()
                    )));
                });
    }

    /**
     * 测试普通服务调用
     */
    @GetMapping("/test/normal")
    public Mono<ResponseEntity<Map<String, Object>>> testNormalService(
            @RequestParam(defaultValue = "https://httpbin.org/get") String url) {
        
        log.info("Testing normal service WebClient with URL: {}", url);
        
        return webClientService.callNormalService(url, Map.class)
                .map(result -> {
                    log.info("Normal service call successful");
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "serviceType", "NORMAL",
                        "url", url,
                        "result", result
                    ));
                })
                .onErrorResume(error -> {
                    log.error("Normal service call failed", error);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of(
                        "success", false,
                        "serviceType", "NORMAL",
                        "url", url,
                        "error", error.getMessage()
                    )));
                });
    }

    /**
     * 测试非核心服务调用
     */
    @GetMapping("/test/non-core")
    public Mono<ResponseEntity<Map<String, Object>>> testNonCoreService(
            @RequestParam(defaultValue = "https://httpbin.org/get") String url) {
        
        log.info("Testing non-core service WebClient with URL: {}", url);
        
        return webClientService.callNonCoreService(url, Map.class)
                .map(result -> {
                    log.info("Non-core service call successful");
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "serviceType", "NON_CORE",
                        "url", url,
                        "result", result
                    ));
                })
                .onErrorResume(error -> {
                    log.error("Non-core service call failed", error);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of(
                        "success", false,
                        "serviceType", "NON_CORE",
                        "url", url,
                        "error", error.getMessage()
                    )));
                });
    }

    /**
     * 测试自动选择WebClient
     */
    @GetMapping("/test/auto")
    public Mono<ResponseEntity<Map<String, Object>>> testAutoWebClient(
            @RequestParam(defaultValue = "https://httpbin.org/get") String url) {
        
        log.info("Testing auto WebClient selection with URL: {}", url);
        
        return webClientService.callService(url, Map.class)
                .map(result -> {
                    log.info("Auto WebClient call successful");
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "url", url,
                        "result", result
                    ));
                })
                .onErrorResume(error -> {
                    log.error("Auto WebClient call failed", error);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of(
                        "success", false,
                        "url", url,
                        "error", error.getMessage()
                    )));
                });
    }

    /**
     * 测试POST请求
     */
    @PostMapping("/test/post")
    public Mono<ResponseEntity<Map<String, Object>>> testPostRequest(
            @RequestParam(defaultValue = "https://httpbin.org/post") String url,
            @RequestBody Map<String, Object> requestBody) {
        
        log.info("Testing POST request with URL: {}", url);
        
        return webClientService.postService(url, requestBody, Map.class)
                .map(result -> {
                    log.info("POST request successful");
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "url", url,
                        "requestBody", requestBody,
                        "result", result
                    ));
                })
                .onErrorResume(error -> {
                    log.error("POST request failed", error);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of(
                        "success", false,
                        "url", url,
                        "requestBody", requestBody,
                        "error", error.getMessage()
                    )));
                });
    }

    /**
     * 获取WebClient统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getWebClientStats() {
        Map<String, Object> stats = webClientService.getWebClientStats();
        log.info("WebClient stats: {}", stats);
        return ResponseEntity.ok(stats);
    }

    /**
     * 测试不同服务类型的URL识别
     */
    @GetMapping("/test/url-recognition")
    public ResponseEntity<Map<String, Object>> testUrlRecognition() {
        String[] testUrls = {
            "https://api.example.com/payment/process",
            "https://api.example.com/user/profile",
            "https://api.example.com/query/balance",
            "https://api.example.com/log/audit",
            "https://api.example.com/unknown/path"
        };

        Map<String, String> results = new java.util.HashMap<>();
        
        for (String url : testUrls) {
            WebClientService.ServiceType serviceType = 
                webClientService.getWebClientByPath(url) == webClientService.getWebClient(WebClientService.ServiceType.CORE) ? WebClientService.ServiceType.CORE :
                webClientService.getWebClientByPath(url) == webClientService.getWebClient(WebClientService.ServiceType.IMPORTANT) ? WebClientService.ServiceType.IMPORTANT :
                webClientService.getWebClientByPath(url) == webClientService.getWebClient(WebClientService.ServiceType.NORMAL) ? WebClientService.ServiceType.NORMAL :
                webClientService.getWebClientByPath(url) == webClientService.getWebClient(WebClientService.ServiceType.NON_CORE) ? WebClientService.ServiceType.NON_CORE :
                WebClientService.ServiceType.DEFAULT;
            
            results.put(url, serviceType.name());
        }

        log.info("URL recognition test results: {}", results);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "urlRecognitionResults", results
        ));
    }
} 