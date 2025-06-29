package com.citi.tts.api.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池拒绝策略处理器
 * 支持多级线程池间的降级和优先级管理
 */
@Slf4j
@Component
public class ThreadPoolRejectionHandler {

    /**
     * 服务繁忙异常
     */
    public static class ServiceBusyException extends RuntimeException {
        public ServiceBusyException(String message) {
            super(message);
        }
    }

    /**
     * 服务不可用异常
     */
    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
} 