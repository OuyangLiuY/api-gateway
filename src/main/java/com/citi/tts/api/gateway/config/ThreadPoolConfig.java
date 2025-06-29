package com.citi.tts.api.gateway.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class ThreadPoolConfig {

    // 2 核心

    public static final int CPU_IN = Runtime.getRuntime().availableProcessors();

    @Bean("cryptoExecutor")
    public ThreadPoolExecutor cryptoExecutor() {
        int poolSize = CPU_IN; // 2
        int maxPoolSize = CPU_IN + 2;
        int queueSize = 100;
        return new ThreadPoolExecutor(
                poolSize, maxPoolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                r -> new Thread(r, "crypto-pool-" + r.hashCode()),
                new CryptoRejectionHandler()
        );
    }

    @Bean("coreApiThreadPool")
    public ThreadPoolExecutor coreApiThreadPool() {
        int poolSize = CPU_IN * 8; // 2
        int maxPoolSize = poolSize + 2;
        int queueSize = 80;
        return new ThreadPoolExecutor(
                poolSize, maxPoolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                r -> new Thread(r, "core-api-pool-" + r.hashCode()),
                new CoreApiRejectionHandler()
        );
    }

    @Bean("normalApiThreadPool")
    public ThreadPoolExecutor normalApiThreadPool() {
        int poolSize = CPU_IN * 3; // 2
        int maxPoolSize = poolSize + 2;
        int queueSize = 20;
        return new ThreadPoolExecutor(
                poolSize, maxPoolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                r -> new Thread(r, "normal-api-pool-" + r.hashCode()),
                new NormalApiRejectionHandler()
        );
    }

    @Bean("defaultThreadPool")
    public ThreadPoolExecutor defaultThreadPool() {
        int poolSize = CPU_IN; // 2
        int maxPoolSize = poolSize + 2;
        int queueSize = 10;
        return new ThreadPoolExecutor(
                poolSize, maxPoolSize, 10L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                r -> new Thread(r, "default-api-pool-" + r.hashCode()),
                new NonCoreApiRejectionHandler()
        );
    }


    /**
     * 核心API拒绝策略 - 优先级降级
     */
    public class CoreApiRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Core API rejected, attempting priority degradation");

            // 尝试降级到普通API线程池
            if (tryExecuteInPool(normalApiThreadPool(), r, "normal API")) {
                return;
            }

            // 如果降级失败，记录错误并抛出异常
            log.error("Core API task rejected and cannot be degraded");
            throw new RejectedExecutionException("Core API task rejected");
        }
    }

    /**
     * 普通API拒绝策略 - 降级处理
     */
    public class NormalApiRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Normal API rejected, attempting degradation");

            // 尝试降级到非核心API线程池
            if (tryExecuteInPool(defaultThreadPool(), r, "non-core API")) {
                return;
            }

            // 如果降级失败，返回服务繁忙错误
            log.error("Normal API task rejected and cannot be degraded");
            throw new ThreadPoolRejectionHandler.ServiceBusyException("Service is busy, please try again later");
        }
    }

    /**
     * 非核心API拒绝策略 - 直接拒绝
     */
    public class NonCoreApiRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Non-core API rejected, returning error directly");

            // 非核心API直接返回错误，不进行降级
            throw new ThreadPoolRejectionHandler.ServiceUnavailableException("Service temporarily unavailable");
        }
    }

    /**
     * 加解密拒绝策略 - 优先级处理
     */
    public class CryptoRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Crypto task rejected, attempting priority handling");

            // 加解密任务优先尝试核心API线程池
            if (tryExecuteInPool(coreApiThreadPool(), r, "core API")) {
                return;
            }

            // 如果核心池也满了，尝试普通API线程池
            if (tryExecuteInPool(normalApiThreadPool(), r, "normal API")) {
                return;
            }

            // 如果都满了，抛出异常
            log.error("Crypto task rejected from all thread pools");
            throw new RejectedExecutionException("Crypto service is overloaded");
        }
    }

    /**
     * 尝试在指定线程池中执行任务
     */
    private boolean tryExecuteInPool(ThreadPoolExecutor pool, Runnable task, String poolName) {
        try {
            if (pool != null && pool.getActiveCount() < pool.getMaximumPoolSize()) {
                pool.execute(task);
                log.info("Task moved to {} thread pool", poolName);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to execute task in {} thread pool", poolName, e);
        }
        return false;
    }
}
