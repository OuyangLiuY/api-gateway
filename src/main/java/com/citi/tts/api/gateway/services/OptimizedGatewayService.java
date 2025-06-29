//package com.citi.tts.api.gateway.services;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//import org.springframework.stereotype.Service;
//
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.atomic.AtomicLong;
//
///**
// * 优化的网关处理服务
// * 针对2核CPU，20ms加解密 + 200ms业务调用的混合负载优化
// */
//@Slf4j
//@Service
//public class OptimizedGatewayService {
//
//    @Autowired
//    @Qualifier("cryptoExecutor")
//    private ThreadPoolTaskExecutor cryptoThreadPool;
//
//    @Autowired
//    @Qualifier("cor")
//    private ThreadPoolTaskExecutor businessThreadPool;
//
//    @Autowired
//    @Qualifier("gatewayThreadPool")
//    private ThreadPoolTaskExecutor gatewayThreadPool;
//
//    // 请求计数器
//    private final AtomicLong totalRequests = new AtomicLong(0);
//    private final AtomicLong cryptoRequests = new AtomicLong(0);
//    private final AtomicLong businessRequests = new AtomicLong(0);
//    private final AtomicLong completedRequests = new AtomicLong(0);
//    private final AtomicLong failedRequests = new AtomicLong(0);
//
//    /**
//     * 处理网关请求
//     * 异步处理：加解密 -> 业务调用 -> 加解密
//     */
//    public CompletableFuture<GatewayResponse> processRequest(GatewayRequest request) {
//        totalRequests.incrementAndGet();
//
//        long startTime = System.currentTimeMillis();
//
//        return CompletableFuture
//            // 第一步：加解密处理 (20ms)
//            .supplyAsync(() -> {
//                cryptoRequests.incrementAndGet();
//                return processCrypto(request);
//            }, cryptoThreadPool)
//
//            // 第二步：业务服务调用 (200ms)
//            .thenComposeAsync(encryptedRequest -> {
//                businessRequests.incrementAndGet();
//                return callBusinessService(encryptedRequest);
//            }, businessThreadPool)
//
//            // 第三步：响应加解密处理 (20ms)
//            .thenApplyAsync(businessResponse -> {
//                cryptoRequests.incrementAndGet();
//                return processResponseCrypto(businessResponse);
//            }, cryptoThreadPool)
//
//            // 处理完成
//            .thenApply(response -> {
//                completedRequests.incrementAndGet();
//                long duration = System.currentTimeMillis() - startTime;
//                log.debug("Request processed in {}ms", duration);
//                return response;
//            })
//
//            // 异常处理
//            .exceptionally(throwable -> {
//                failedRequests.incrementAndGet();
//                log.error("Request processing failed", throwable);
//                return createErrorResponse(throwable);
//            });
//    }
//
//    /**
//     * 加解密处理
//     * 模拟20ms的加解密操作
//     */
//    private EncryptedRequest processCrypto(GatewayRequest request) {
//        try {
//            // 模拟20ms的加解密处理
//            Thread.sleep(20);
//
//            // 实际加解密逻辑
//            String encryptedData = encryptData(request.getData());
//
//            return EncryptedRequest.builder()
//                    .originalRequest(request)
//                    .encryptedData(encryptedData)
//                    .timestamp(System.currentTimeMillis())
//                    .build();
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException("Crypto processing interrupted", e);
//        }
//    }
//
//    /**
//     * 业务服务调用
//     * 模拟200ms的业务服务调用
//     */
//    private CompletableFuture<BusinessResponse> callBusinessService(EncryptedRequest encryptedRequest) {
//        return CompletableFuture.supplyAsync(() -> {
//            try {
//                // 模拟200ms的业务服务调用
//                Thread.sleep(200);
//
//                // 实际业务服务调用逻辑
//                return BusinessResponse.builder()
//                        .requestId(encryptedRequest.getOriginalRequest().getRequestId())
//                        .data("Business response data")
//                        .status("SUCCESS")
//                        .timestamp(System.currentTimeMillis())
//                        .build();
//
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                throw new RuntimeException("Business service call interrupted", e);
//            }
//        }, businessThreadPool);
//    }
//
//    /**
//     * 响应加解密处理
//     * 模拟20ms的响应加解密操作
//     */
//    private GatewayResponse processResponseCrypto(BusinessResponse businessResponse) {
//        try {
//            // 模拟20ms的响应加解密处理
//            Thread.sleep(20);
//
//            // 实际响应加解密逻辑
//            String decryptedData = decryptData(businessResponse.getData());
//
//            return GatewayResponse.builder()
//                    .requestId(businessResponse.getRequestId())
//                    .data(decryptedData)
//                    .status(businessResponse.getStatus())
//                    .timestamp(System.currentTimeMillis())
//                    .build();
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException("Response crypto processing interrupted", e);
//        }
//    }
//
//    /**
//     * 创建错误响应
//     */
//    private GatewayResponse createErrorResponse(Throwable throwable) {
//        return GatewayResponse.builder()
//                .requestId("ERROR")
//                .data("Error: " + throwable.getMessage())
//                .status("ERROR")
//                .timestamp(System.currentTimeMillis())
//                .build();
//    }
//
//    /**
//     * 模拟加密操作
//     */
//    private String encryptData(String data) {
//        // 实际加密逻辑
//        return "ENCRYPTED_" + data;
//    }
//
//    /**
//     * 模拟解密操作
//     */
//    private String decryptData(String data) {
//        // 实际解密逻辑
//        return data.replace("ENCRYPTED_", "");
//    }
//
//    /**
//     * 获取性能统计
//     */
//    public PerformanceStats getPerformanceStats() {
//        return PerformanceStats.builder()
//                .totalRequests(totalRequests.get())
//                .cryptoRequests(cryptoRequests.get())
//                .businessRequests(businessRequests.get())
//                .completedRequests(completedRequests.get())
//                .failedRequests(failedRequests.get())
//                .successRate(calculateSuccessRate())
//                .cryptoThreadPoolStatus(getThreadPoolStatus(cryptoThreadPool, "Crypto"))
//                .businessThreadPoolStatus(getThreadPoolStatus(businessThreadPool, "Business"))
//                .gatewayThreadPoolStatus(getThreadPoolStatus(gatewayThreadPool, "Gateway"))
//                .build();
//    }
//
//    /**
//     * 计算成功率
//     */
//    private double calculateSuccessRate() {
//        long total = totalRequests.get();
//        long completed = completedRequests.get();
//        return total > 0 ? (double) completed / total : 0.0;
//    }
//
//    /**
//     * 获取线程池状态
//     */
//    private ThreadPoolStatus getThreadPoolStatus(ThreadPoolTaskExecutor executor, String name) {
//        return ThreadPoolStatus.builder()
//                .name(name)
//                .activeCount(executor.getActiveCount())
//                .poolSize(executor.getPoolSize())
//                .maximumPoolSize(executor.getMaxPoolSize())
//                .queueSize(executor.getThreadPoolExecutor().getQueue().size())
//                .completedTasks(executor.getThreadPoolExecutor().getCompletedTaskCount())
//                .utilization((double) executor.getActiveCount() / executor.getMaxPoolSize())
//                .build();
//    }
//
//    /**
//     * 性能测试方法
//     */
//    public void performanceTest(int testCount) {
//        log.info("Starting performance test with {} requests", testCount);
//
//        long startTime = System.currentTimeMillis();
//        CompletableFuture<GatewayResponse>[] futures = new CompletableFuture[testCount];
//
//        for (int i = 0; i < testCount; i++) {
//            GatewayRequest request = GatewayRequest.builder()
//                    .requestId("TEST_" + i)
//                    .data("Test data " + i)
//                    .build();
//
//            futures[i] = processRequest(request);
//        }
//
//        CompletableFuture.allOf(futures).join();
//        long endTime = System.currentTimeMillis();
//
//        long totalTime = endTime - startTime;
//        double tps = (double) testCount / totalTime * 1000;
//
//        log.info("Performance Test Results:");
//        log.info("Test Count: {}", testCount);
//        log.info("Total Time: {}ms", totalTime);
//        log.info("Average Time per Request: {:.2f}ms", (double) totalTime / testCount);
//        log.info("TPS: {:.2f}", tps);
//
//        // 输出线程池状态
//        PerformanceStats stats = getPerformanceStats();
//        log.info("Performance Stats: {}", stats);
//    }
//
//    /**
//     * 网关请求
//     */
//    @lombok.Builder
//    @lombok.Data
//    public static class GatewayRequest {
//        private String requestId;
//        private String data;
//        private long timestamp;
//    }
//
//    /**
//     * 加密请求
//     */
//    @lombok.Builder
//    @lombok.Data
//    public static class EncryptedRequest {
//        private GatewayRequest originalRequest;
//        private String encryptedData;
//        private long timestamp;
//    }
//
//    /**
//     * 业务响应
//     */
//    @lombok.Builder
//    @lombok.Data
//    public static class BusinessResponse {
//        private String requestId;
//        private String data;
//        private String status;
//        private long timestamp;
//    }
//
//    /**
//     * 网关响应
//     */
//    @lombok.Builder
//    @lombok.Data
//    public static class GatewayResponse {
//        private String requestId;
//        private String data;
//        private String status;
//        private long timestamp;
//    }
//
//    /**
//     * 性能统计
//     */
//    @lombok.Builder
//    @lombok.Data
//    public static class PerformanceStats {
//        private long totalRequests;
//        private long cryptoRequests;
//        private long businessRequests;
//        private long completedRequests;
//        private long failedRequests;
//        private double successRate;
//        private ThreadPoolStatus cryptoThreadPoolStatus;
//        private ThreadPoolStatus businessThreadPoolStatus;
//        private ThreadPoolStatus gatewayThreadPoolStatus;
//    }
//
//    /**
//     * 线程池状态
//     */
//    @lombok.Builder
//    @lombok.Data
//    public static class ThreadPoolStatus {
//        private String name;
//        private int activeCount;
//        private int poolSize;
//        private int maximumPoolSize;
//        private int queueSize;
//        private long completedTasks;
//        private double utilization;
//    }
//}