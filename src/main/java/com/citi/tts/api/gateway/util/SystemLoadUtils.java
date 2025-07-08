package com.citi.tts.api.gateway.util;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ThreadPoolExecutor;

public class SystemLoadUtils {

    /**
     * 计算建议并发度
     * @param threadPool 业务线程池
     * @return 建议的并发度
     */
    public static int calcConcurrencyBySystemLoad(ThreadPoolExecutor threadPool) {
        int cpuConcurrency = calcByCpu();
        int poolConcurrency = calcByThreadPool(threadPool);
        int memConcurrency = calcByJvmMemory();

        // 取最保守的建议
        return Math.max(1, Math.min(cpuConcurrency, Math.min(poolConcurrency, memConcurrency)));
    }

    // 1. CPU负载
    private static int calcByCpu() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double load = osBean.getSystemLoadAverage();
        int cpuCores = osBean.getAvailableProcessors();

        if (load < 0) return 10;
        if (load < cpuCores * 0.5) return 20;
        if (load < cpuCores * 0.8) return 15;
        if (load < cpuCores * 1.0) return 10;
        if (load < cpuCores * 1.5) return 5;
        return 2;
    }

    // 2. 线程池活跃数
    private static int calcByThreadPool(ThreadPoolExecutor threadPool) {
        int maxPoolSize = threadPool.getMaximumPoolSize();
        int active = threadPool.getActiveCount();
        double usage = (double) active / maxPoolSize;

        if (usage < 0.5) return 20;
        if (usage < 0.7) return 10;
        if (usage < 0.9) return 5;
        return 2;
    }

    // 3. JVM内存
    private static int calcByJvmMemory() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long used = heap.getUsed();
        long max = heap.getMax();
        double usage = (double) used / max;

        if (usage < 0.6) return 20;
        if (usage < 0.8) return 10;
        if (usage < 0.9) return 5;
        return 2;
    }
}