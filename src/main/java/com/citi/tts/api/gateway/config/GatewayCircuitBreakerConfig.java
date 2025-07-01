package com.citi.tts.api.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.convert.DurationUnit;
import java.time.temporal.ChronoUnit;
import java.time.Duration;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 熔断器配置类
 * 支持多种熔断策略和限流策略
 */
@Slf4j
@Configuration
public class GatewayCircuitBreakerConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "gateway.circuit-breaker")
    public CircuitBreakerProperties circuitBreakerProperties() {
        return new CircuitBreakerProperties();
    }

    /**
     * 熔断器注册表
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerProperties properties) {
        return CircuitBreakerRegistry.ofDefaults();
    }

    /**
     * 限流器注册表
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry(CircuitBreakerProperties properties) {
        return RateLimiterRegistry.ofDefaults();
    }

    /**
     * 核心API熔断器
     */
    @Bean("coreApiCircuitBreaker")
    public CircuitBreaker coreApiCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config =
            CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .recordExceptions(
                    IOException.class,
                    TimeoutException.class,
                    ResourceAccessException.class
                )
                .build();

        return registry.circuitBreaker("coreApiCircuitBreaker", config);
    }

    /**
     * 普通API熔断器
     */
    @Bean("normalApiCircuitBreaker")
    public CircuitBreaker normalApiCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config =
            CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .failureRateThreshold(30.0f)
                .slowCallRateThreshold(30.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .recordExceptions(
                    IOException.class,
                    TimeoutException.class,
                    ResourceAccessException.class
                )
                .build();

        return registry.circuitBreaker("normalApiCircuitBreaker", config);
    }

    /**
     * 非核心API熔断器
     */
    @Bean("nonCoreApiCircuitBreaker")
    public CircuitBreaker nonCoreApiCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config =
            CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(30)
                .minimumNumberOfCalls(15)
                .permittedNumberOfCallsInHalfOpenState(8)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .failureRateThreshold(20.0f)
                .slowCallRateThreshold(20.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .recordExceptions(
                    IOException.class,
                    TimeoutException.class,
                    ResourceAccessException.class
                )
                .build();

        return registry.circuitBreaker("nonCoreApiCircuitBreaker", config);
    }

    /**
     * 加解密API熔断器
     */
    @Bean("cryptoApiCircuitBreaker")
    public CircuitBreaker cryptoApiCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config =
            CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(15)
                .minimumNumberOfCalls(8)
                .permittedNumberOfCallsInHalfOpenState(4)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(8))
                .failureRateThreshold(40.0f)
                .slowCallRateThreshold(40.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .recordExceptions(
                    IOException.class,
                    TimeoutException.class,
                    ResourceAccessException.class,
                    GeneralSecurityException.class
                )
                .build();

        return registry.circuitBreaker("cryptoApiCircuitBreaker", config);
    }

    /**
     * 核心API限流器
     */
    @Bean("coreApiRateLimiter")
    public RateLimiter coreApiRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(500))
            .build();

        return registry.rateLimiter("coreApiRateLimiter", config);
    }

    /**
     * 普通API限流器
     */
    @Bean("normalApiRateLimiter")
    public RateLimiter normalApiRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(50)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(1000))
            .build();

        return registry.rateLimiter("normalApiRateLimiter", config);
    }

    /**
     * 非核心API限流器
     */
    @Bean("nonCoreApiRateLimiter")
    public RateLimiter nonCoreApiRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(20)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(2000))
            .build();

        return registry.rateLimiter("nonCoreApiRateLimiter", config);
    }

    /**
     * 熔断器配置属性
     */
    @Data
    @Component
    public static class CircuitBreakerProperties {
        // 熔断器开关
        private boolean enabled = true;
        
        // 熔断器配置
        private Map<String, CircuitBreakerInstanceConfig> instances = Map.of(
            "coreApi", new CircuitBreakerInstanceConfig(10, 5, 3, Duration.ofSeconds(5), 50.0f, Duration.ofSeconds(2)),
            "normalApi", new CircuitBreakerInstanceConfig(20, 10, 5, Duration.ofSeconds(10), 30.0f, Duration.ofSeconds(5)),
            "nonCoreApi", new CircuitBreakerInstanceConfig(30, 15, 8, Duration.ofSeconds(15), 20.0f, Duration.ofSeconds(10)),
            "cryptoApi", new CircuitBreakerInstanceConfig(15, 8, 4, Duration.ofSeconds(8), 40.0f, Duration.ofSeconds(3))
        );
        
        // 限流器配置
        private Map<String, RateLimiterInstanceConfig> rateLimiters = Map.of(
            "coreApi", new RateLimiterInstanceConfig(100, 1, 500),
            "normalApi", new RateLimiterInstanceConfig(50, 1, 1000),
            "nonCoreApi", new RateLimiterInstanceConfig(20, 1, 2000)
        );
    }

    /**
     * 熔断器实例配置
     */
    @Data
    public static class CircuitBreakerInstanceConfig {
        private int slidingWindowSize;
        private int minimumNumberOfCalls;
        private int permittedNumberOfCallsInHalfOpenState;
        @DurationUnit(ChronoUnit.MILLIS)
        private Duration waitDurationInOpenState;
        private float failureRateThreshold;
        @DurationUnit(ChronoUnit.MILLIS)
        private Duration slowCallDurationThreshold;

        public CircuitBreakerInstanceConfig(int slidingWindowSize, int minimumNumberOfCalls,
                                          int permittedNumberOfCallsInHalfOpenState, Duration waitDurationInOpenState,
                                          float failureRateThreshold, Duration slowCallDurationThreshold) {
            this.slidingWindowSize = slidingWindowSize;
            this.minimumNumberOfCalls = minimumNumberOfCalls;
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
            this.waitDurationInOpenState = waitDurationInOpenState;
            this.failureRateThreshold = failureRateThreshold;
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }
    }

    /**
     * 限流器实例配置
     */
    @Data
    public static class RateLimiterInstanceConfig {
        private int limitForPeriod;
        private int limitRefreshPeriod;
        private int timeoutDuration;

        public RateLimiterInstanceConfig(int limitForPeriod, int limitRefreshPeriod, int timeoutDuration) {
            this.limitForPeriod = limitForPeriod;
            this.limitRefreshPeriod = limitRefreshPeriod;
            this.timeoutDuration = timeoutDuration;
        }
    }
} 