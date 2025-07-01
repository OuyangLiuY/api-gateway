package com.citi.tts.api.gateway.routes;

import com.citi.tts.api.gateway.filter.AdvancedRateLimitFilter;
import com.citi.tts.api.gateway.filter.CircuitBreakerFilter;
import com.citi.tts.api.gateway.filter.CryptoFilter;
import com.citi.tts.api.gateway.filter.ServiceDegradationGatewayFilter;
import com.citi.tts.api.gateway.service.ServiceDegradationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutesConfig {

    @Autowired
    private CryptoFilter cryptoFilter;

    @Autowired
    private AdvancedRateLimitFilter advancedRateLimitFilter;

    @Autowired
    private CircuitBreakerFilter circuitBreakerFilter;

    @Autowired
    private ServiceDegradationGatewayFilter serviceDegradationGatewayFilter;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                // 支付服务路由 - 核心API（前置降级：捕获熔断器和网络异常）
                .route("payment-route", r -> r
                        .path("/api/gateway/payment/**")
                        .filters(f -> f
                                .rewritePath("/api/gateway/payment/(?<segment>.*)",
                                        "/api/payment/${segment}")
                                .filter(advancedRateLimitFilter) // 1. 限流过滤器
//                                .filter(serviceDegradationGatewayFilter.apply(
//                                    new ServiceDegradationGatewayFilter.Config(
//                                        ServiceDegradationService.ServiceLevel.CORE,
//                                        "payment-service"
//                                    )
//                                )) // 2. 服务降级过滤器（前置：捕获熔断器异常）
                                .filter(circuitBreakerFilter)    // 3. 熔断器过滤器
                                .filter(cryptoFilter)           // 4. 加解密过滤器
                        )
                        .uri("http://localhost:8081")
                )
                // 用户服务路由 - 重要API（前置降级：快速响应）
                .route("user-route", r -> r
                        .path("/api/gateway/user/**")
                        .filters(f -> f
                                .rewritePath("/api/gateway/user/(?<segment>.*)",
                                        "/api/user/${segment}")
                                .filter(advancedRateLimitFilter) // 1. 限流过滤器
                                .filter(serviceDegradationGatewayFilter.apply(
                                    new ServiceDegradationGatewayFilter.Config(
                                        ServiceDegradationService.ServiceLevel.IMPORTANT, 
                                        "user-service"
                                    )
                                )) // 2. 服务降级过滤器（前置：快速降级）
                                .filter(circuitBreakerFilter)    // 3. 熔断器过滤器
                        )
                        .uri("http://localhost:8082")
                )
                // 账户服务路由 - 核心API（前置降级：捕获熔断器异常）
                .route("account-route", r -> r
                        .path("/api/gateway/account/**")
                        .filters(f -> f
                                .rewritePath("/api/gateway/account/(?<segment>.*)",
                                        "/api/account/${segment}")
                                .filter(advancedRateLimitFilter) // 1. 限流过滤器
                                .filter(serviceDegradationGatewayFilter.apply(
                                    new ServiceDegradationGatewayFilter.Config(
                                        ServiceDegradationService.ServiceLevel.CORE, 
                                        "account-service"
                                    )
                                )) // 2. 服务降级过滤器（前置：捕获熔断器异常）
                                .filter(circuitBreakerFilter)    // 3. 熔断器过滤器
                        )
                        .uri("http://localhost:8083")
                )
                // 转账服务路由 - 核心API（前置降级：捕获熔断器异常）
                .route("transfer-route", r -> r
                        .path("/api/gateway/transfer/**")
                        .filters(f -> f
                                .rewritePath("/api/gateway/transfer/(?<segment>.*)",
                                        "/api/transfer/${segment}")
                                .filter(advancedRateLimitFilter) // 1. 限流过滤器
                                .filter(serviceDegradationGatewayFilter.apply(
                                    new ServiceDegradationGatewayFilter.Config(
                                        ServiceDegradationService.ServiceLevel.CORE, 
                                        "transfer-service"
                                    )
                                )) // 2. 服务降级过滤器（前置：捕获熔断器异常）
                                .filter(circuitBreakerFilter)    // 3. 熔断器过滤器
                                .filter(cryptoFilter)           // 4. 加解密过滤器
                        )
                        .uri("http://localhost:8084")
                )
                // 查询服务路由 - 普通API（后置降级：捕获所有异常）
                .route("query-route", r -> r
                        .path("/api/gateway/query/**")
                        .filters(f -> f
                                .rewritePath("/api/gateway/query/(?<segment>.*)",
                                        "/api/query/${segment}")
                                .filter(advancedRateLimitFilter) // 1. 限流过滤器
                                .filter(circuitBreakerFilter)    // 2. 熔断器过滤器
                                .filter(serviceDegradationGatewayFilter.apply(
                                    new ServiceDegradationGatewayFilter.Config(
                                        ServiceDegradationService.ServiceLevel.NORMAL, 
                                        "query-service"
                                    )
                                )) // 3. 服务降级过滤器（后置：捕获所有异常）
                        )
                        .uri("http://localhost:8085")
                )
                // 统计服务路由 - 非核心API（后置降级：捕获所有异常）
                .route("statistics-route", r -> r
                        .path("/api/gateway/statistics/**")
                        .filters(f -> f
                                .rewritePath("/api/gateway/statistics/(?<segment>.*)",
                                        "/api/statistics/${segment}")
                                .filter(advancedRateLimitFilter) // 1. 限流过滤器
                                .filter(circuitBreakerFilter)    // 2. 熔断器过滤器
                                .filter(serviceDegradationGatewayFilter.apply(
                                    new ServiceDegradationGatewayFilter.Config(
                                        ServiceDegradationService.ServiceLevel.NON_CORE, 
                                        "statistics-service"
                                    )
                                )) // 3. 服务降级过滤器（后置：捕获所有异常）
                        )
                        .uri("http://localhost:8086")
                )
                // 测试路由 - 本地测试接口（后置降级：捕获所有异常）
                .route("test-route", r -> r
                        .path("/api/gateway/test/**")
                        .filters(f -> f
                                .rewritePath("/api/gateway/test/(?<segment>.*)",
                                        "/api/gateway/test/${segment}")
                                .filter(advancedRateLimitFilter) // 1. 限流过滤器
                                .filter(circuitBreakerFilter)    // 2. 熔断器过滤器
                                .filter(serviceDegradationGatewayFilter.apply(
                                    new ServiceDegradationGatewayFilter.Config(
                                        ServiceDegradationService.ServiceLevel.NORMAL, 
                                        "test-service"
                                    )
                                )) // 3. 服务降级过滤器（后置：捕获所有异常）
                        )
                        .uri("http://localhost:8080") // 指向本地网关
                )
                .build();
    }
}
