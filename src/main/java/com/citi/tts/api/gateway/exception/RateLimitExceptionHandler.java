package com.citi.tts.api.gateway.exception;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.util.concurrent.RejectedExecutionException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitExceptionHandler implements WebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (ServerWebExchangeUtils.isAlreadyRouted(exchange)) {
            return Mono.error(ex);
        }
        if (ex instanceof RejectedExecutionException) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"code\":429,\"msg\":\"Too Many Requests - API限流\"}";
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory().wrap(body.getBytes())));
        }
        return Mono.error(ex);
    }
}