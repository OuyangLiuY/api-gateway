package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.crypto.ReactiveAESService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class CryptoFilter implements GatewayFilter {
    @Autowired
    private ReactiveAESService reactiveAESService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final String keyId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-AES-Key-Id"))
                .orElse(UUID.randomUUID().toString());
        final long timeoutMs = 1000;
        log.debug("crupto filter is coming");
        ServerHttpRequestDecorator requestDecorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                return DataBufferUtils.join(super.getBody())
                        .flatMapMany(dataBuffer -> {
                            byte[] encryptedBytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(encryptedBytes);
                            DataBufferUtils.release(dataBuffer);
                            String encryptedPayload = new String(encryptedBytes, StandardCharsets.UTF_8);
                            return reactiveAESService.decrypt(encryptedPayload, keyId, timeoutMs)
                                    .map(plain -> {
                                        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
                                        log.info("plain test by decrypt = {}",new String(plainBytes));

                                        return wrapBytesFromFactory(plainBytes,exchange.getResponse().bufferFactory());
                                    });
                        });
            }
        };

        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(@NotNull Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(body)
                        .flatMap(dataBuffer -> {
                            byte[] plainBytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(plainBytes);
                            DataBufferUtils.release(dataBuffer);
                            String plainResponse = new String(plainBytes, StandardCharsets.UTF_8);
                            return reactiveAESService.encrypt(plainResponse, keyId, timeoutMs)
                                    .flatMap(encrypted -> {
                                        byte[] encryptedBytes = encrypted.getBytes(StandardCharsets.UTF_8);
                                        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(encryptedBytes);
                                        getDelegate().getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8");
                                        return super.writeWith(Mono.just(buffer));
                                    });
                        });
            }
        };

        return chain.filter(exchange.mutate()
                    .request(requestDecorator)
                    .response(responseDecorator)
                    .build())
                .onErrorResume(e -> {
                    var response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.BAD_REQUEST);
                    byte[] bytes = ("加解密失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
                    DataBuffer buffer = response.bufferFactory().wrap(bytes);
                    return response.writeWith(Mono.just(buffer));
                });
    }
    public static DataBuffer wrapBytesFromFactory(byte[] bytes, DataBufferFactory bufferFactory) {
        bufferFactory.allocateBuffer(bytes.length);
        DataBuffer buffer = bufferFactory.wrap(bytes);
        return (buffer);
    }
}
