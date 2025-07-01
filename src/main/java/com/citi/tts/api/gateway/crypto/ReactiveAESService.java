package com.citi.tts.api.gateway.crypto;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ReactiveAESService {
    public Mono<String> encrypt(String plain, String keyId, long timeoutMs) {
        return Mono.fromCallable(() -> AES256Util.encrypt(plain, keyId))
                .timeout(java.time.Duration.ofMillis(timeoutMs))
                .onErrorResume(e ->
                        Mono.error(new RuntimeException("加密失败: " + e.getMessage(), e)));
    }

    public Mono<String> decrypt(String encrypted, String keyId, long timeoutMs) {
        return Mono.fromCallable(() -> AES256Util.decrypt(encrypted, keyId))
                .timeout(java.time.Duration.ofMillis(timeoutMs))
                .onErrorResume(e ->
                        Mono.error(new RuntimeException("解密失败: " + e.getMessage(), e)));
    }
} 